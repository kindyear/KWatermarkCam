package cn.kindyear.kwatermarkcam.core.camera

import android.content.Context
import android.util.Log
import android.util.Rational
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
    ) {
        val zoomStops: List<Float> get() = (listOf(.5f, 1f, 2f, 3f).filter { it in minZoom..maxZoom } + listOf(minZoom)).distinct().sorted()
    }
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var provider: ProcessCameraProvider? = null
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
    fun bind(owner: LifecycleOwner, previewView: PreviewView, front: Boolean, flash: String) {
        unbind()
        view = previewView
        val current = generation
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (current != generation) return@addListener
            try {
                val p = future.get()
                provider = p
                val backAvailable = p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                val frontAvailable = p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                val useFront = if (front) frontAvailable else !backAvailable && frontAvailable
                val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                val pixelBudget = (Runtime.getRuntime().maxMemory() * .30 / 4).toLong()
                val resolution = ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .setResolutionFilter { sizes, _ ->
                        sizes.filter { it.width.toLong() * it.height <= pixelBudget }.ifEmpty { listOf(sizes.last()) }
                    }.build()
                val output = ImageCapture.Builder().setResolutionSelector(resolution).setTargetRotation(rotation)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(95).build()
                val live = Preview.Builder().setTargetRotation(rotation).build()
                live.setSurfaceProvider(previewView.surfaceProvider)
                // Shared ViewPort crops ImageCapture to exactly the preview's visible rectangle.
                val viewport = ViewPort.Builder(Rational(previewView.width.coerceAtLeast(1), previewView.height.coerceAtLeast(1)), rotation)
                    .setScaleType(ViewPort.FILL_CENTER).build()
                val group = UseCaseGroup.Builder().setViewPort(viewport).addUseCase(live).addUseCase(output).build()
                val bound = p.bindToLifecycle(owner, selector, group)
                camera = bound; capture = output; preview = live
                output.flashMode = if (bound.cameraInfo.hasFlashUnit()) flashValue(flash) else ImageCapture.FLASH_MODE_OFF
                mutable.value = State(hasFlash = bound.cameraInfo.hasFlashUnit(), canSwitch = backAvailable && frontAvailable, front = useFront)
                zoomObserver = Observer { z ->
                    if (current == generation) mutable.value = mutable.value.copy(zoom = z.zoomRatio, minZoom = z.minZoomRatio, maxZoom = z.maxZoomRatio)
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
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(s.minZoom, s.maxZoom))?.let { observeFailure(it) }
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
        mutable.value = State()
    }
    private fun flashValue(value: String) = when (value) { "on" -> ImageCapture.FLASH_MODE_ON; "auto" -> ImageCapture.FLASH_MODE_AUTO; else -> ImageCapture.FLASH_MODE_OFF }
}
