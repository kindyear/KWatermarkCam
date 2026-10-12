package cn.kindyear.kwatermarkcam.core.camera

import android.content.Context
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** CameraX and its lifecycle are owned here, never by recomposition or business UI. */
class CameraController @Inject constructor(@param:ApplicationContext private val context: Context) {
    data class State(
        val ready: Boolean = false, val hasFlash: Boolean = false, val canSwitch: Boolean = false,
        val front: Boolean = false, val zoom: Float = 1f, val minZoom: Float = 1f,
        val maxZoom: Float = 1f, val error: Boolean = false,
        val lenses: List<CameraLens> = emptyList(), val activeLensId: String? = null,
        val unavailableLensIds: Set<String> = emptySet(), val intrinsicZoom: Float = 1f,
    ) {
        val zoomStops: List<Float> get() = (listOf(.5f, 1f, 2f, 3f).filter { it in minZoom..maxZoom } + listOf(minZoom)).distinct().sorted()
    }
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var provider: ProcessCameraProvider? = null
    private var catalog: CameraCatalog? = null
    private val failedLenses = mutableSetOf<String>()
    private val requested = MutableStateFlow<String?>(null)
    val requestedLens = requested.asStateFlow()
    fun selectLens(id: String) { if (id !in failedLenses) requested.value = id }
    private var camera: Camera? = null
    private var capture: ImageCapture? = null
    private var preview: Preview? = null
    private var view: PreviewView? = null
    private var generation = 0
    private var zoomObserver: Observer<ZoomState>? = null
    private var cameraObserver: Observer<CameraState>? = null
    private var lifecycleObserver: LifecycleEventObserver? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var rotationListener: OrientationEventListener? = null
    private val executor = ContextCompat.getMainExecutor(context)

    /** Bind only when the lifecycle, preview geometry, lens, or aspect changes. Call on main. */
    fun bind(owner: LifecycleOwner, previewView: PreviewView, front: Boolean, flash: String, requestedId: String? = requested.value) {
        unbind()
        view = previewView
        val current = generation
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (current != generation) return@addListener
            try {
                val p = future.get()
                provider = p
                val detected = catalog ?: CameraCatalog(p).also { catalog = it }
                val preferred = requireNotNull(detected.select(front, requestedId?.takeUnless { it in failedLenses })) { "No selectable camera" }
                val primary = requireNotNull(detected.select(front, null))
                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                val pixelBudget = (Runtime.getRuntime().maxMemory() * .30 / 4).toLong()
                fun createOutput(conservative: Boolean): ImageCapture {
                    val strategy = if (conservative) ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER) else ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
                    val resolution = ResolutionSelector.Builder().setResolutionStrategy(strategy)
                        .setResolutionFilter { sizes, _ ->
                            sizes.filter { it.width.toLong() * it.height <= pixelBudget }.ifEmpty { sizes.takeLast(1) }
                        }.build()
                    return ImageCapture.Builder().setResolutionSelector(resolution).setTargetRotation(rotation)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(95).build()
                }
                var output = createOutput(false)
                val live = Preview.Builder().setTargetRotation(rotation).build()
                live.setSurfaceProvider(previewView.surfaceProvider)
                // Shared ViewPort crops ImageCapture to exactly the preview's visible rectangle.
                val viewport = ViewPort.Builder(Rational(previewView.width.coerceAtLeast(1), previewView.height.coerceAtLeast(1)), rotation)
                    .setScaleType(ViewPort.FILL_CENTER).build()
                fun bind(entry: CameraCatalog.Entry): Camera {
                    val group = UseCaseGroup.Builder().setViewPort(viewport).addUseCase(live).addUseCase(output).build()
                    return p.bindToLifecycle(owner, entry.info.cameraSelector, group)
                }
                var selected = preferred
                val bound = try { bind(preferred) } catch (first: Exception) {
                    Log.w("CameraController", "Preferred camera/resolution unavailable; falling back", first)
                    p.unbind(live, output)
                    if (preferred.lens.id != primary.lens.id && first is IllegalArgumentException) failedLenses += preferred.lens.id
                    selected = primary
                    output = createOutput(true)
                    bind(primary)
                }
                camera = bound; capture = output; preview = live
                output.flashMode = if (bound.cameraInfo.hasFlashUnit()) flashValue(flash) else ImageCapture.FLASH_MODE_OFF
                mutable.value = State(hasFlash = bound.cameraInfo.hasFlashUnit(), canSwitch = detected.entries.any { it.lens.front } && detected.entries.any { !it.lens.front }, front = selected.lens.front,
                    lenses = detected.entries.map { it.lens }, activeLensId = selected.lens.id, unavailableLensIds = failedLenses.toSet(), intrinsicZoom = selected.lens.intrinsicZoom)
                zoomObserver = Observer { z ->
                    if (current == generation) mutable.value = mutable.value.copy(zoom = z.zoomRatio * selected.lens.intrinsicZoom, minZoom = z.minZoomRatio * selected.lens.intrinsicZoom, maxZoom = z.maxZoomRatio * selected.lens.intrinsicZoom)
                }
                cameraObserver = Observer { s ->
                    if (current == generation) mutable.value = mutable.value.copy(ready = s.type == CameraState.Type.OPEN && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), error = s.error != null)
                }
                bound.cameraInfo.zoomState.observe(owner, requireNotNull(zoomObserver))
                bound.cameraInfo.cameraState.observe(owner, requireNotNull(cameraObserver))
                lifecycleOwner = owner
                rotationListener = object : OrientationEventListener(context) {
                    override fun onOrientationChanged(degrees: Int) {
                        if (degrees == ORIENTATION_UNKNOWN) return
                        val r = when (degrees) { in 45..134 -> Surface.ROTATION_270; in 135..224 -> Surface.ROTATION_180; in 225..314 -> Surface.ROTATION_90; else -> Surface.ROTATION_0 }
                        output.targetRotation = r; live.targetRotation = r
                    }
                }
                lifecycleObserver = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) rotationListener?.enable()
                    if (event == Lifecycle.Event.ON_PAUSE) rotationListener?.disable()
                    // LiveData observers are inactive after STOP and cannot report the closed state.
                    if (event == Lifecycle.Event.ON_STOP) mutable.value = mutable.value.copy(ready = false)
                    if (event == Lifecycle.Event.ON_START) mutable.value = mutable.value.copy(ready = bound.cameraInfo.cameraState.value?.type == CameraState.Type.OPEN)
                }.also { owner.lifecycle.addObserver(it) }
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) rotationListener?.enable()
            } catch (e: Exception) {
                Log.e("CameraController", "Camera binding failed", e)
                mutable.value = State(error = true)
            }
        }, executor)
    }

    fun setFlash(value: String) { capture?.flashMode = if (mutable.value.hasFlash) flashValue(value) else ImageCapture.FLASH_MODE_OFF }
    fun zoom(ratio: Float) {
        val s = mutable.value
        if (!s.ready) return
        camera?.cameraControl?.setZoomRatio((ratio.coerceIn(s.minZoom, s.maxZoom) / s.intrinsicZoom))?.let { observeFailure(it) }
    }
    fun focus(x: Float, y: Float) {
        val point = view?.meteringPointFactory?.createPoint(x, y) ?: return
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        val bound = camera ?: return
        if (bound.cameraInfo.isFocusMeteringSupported(action)) observeFailure(bound.cameraControl.startFocusAndMetering(action))
    }
    private fun observeFailure(future: com.google.common.util.concurrent.ListenableFuture<*>) {
        future.addListener({ try { future.get() } catch (e: Exception) {
            if (e.cause is CameraControl.OperationCanceledException) Log.d("CameraController", "Control request superseded or camera stopped")
            else Log.w("CameraController", "Control request unavailable", e)
        } }, executor)
    }

    /** Capture a JPEG to app-private storage; front output mirrors the front preview. */
    suspend fun takePhoto(file: File): File = suspendCancellableCoroutine { continuation ->
        val imageCapture = capture
        if (imageCapture == null || !mutable.value.ready) {
            continuation.resumeWithException(IllegalStateException("Camera not ready")); return@suspendCancellableCoroutine
        }
        val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = mutable.value.front }
        val options = ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()
        imageCapture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                if (continuation.isActive) continuation.resume(file) else file.delete()
            }
            override fun onError(exception: ImageCaptureException) {
                file.delete()
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
        })
    }
    fun unbind() {
        generation++
        zoomObserver?.let { camera?.cameraInfo?.zoomState?.removeObserver(it) }
        cameraObserver?.let { camera?.cameraInfo?.cameraState?.removeObserver(it) }
        lifecycleObserver?.let { lifecycleOwner?.lifecycle?.removeObserver(it) }
        zoomObserver = null; cameraObserver = null; lifecycleObserver = null; lifecycleOwner = null
        rotationListener?.disable(); rotationListener = null
        val useCases = listOfNotNull(preview, capture)
        if (useCases.isNotEmpty()) provider?.unbind(*useCases.toTypedArray())
        camera = null; capture = null; preview = null; view = null
        mutable.value = State(lenses = catalog?.entries.orEmpty().map { it.lens }, unavailableLensIds = failedLenses.toSet())
    }
    private fun flashValue(value: String) = when (value) { "on" -> ImageCapture.FLASH_MODE_ON; "auto" -> ImageCapture.FLASH_MODE_AUTO; else -> ImageCapture.FLASH_MODE_OFF }
}
