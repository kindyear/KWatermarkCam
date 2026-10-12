package cn.kindyear.kwatermarkcam.core.camera

import android.util.Log
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.lifecycle.ProcessCameraProvider

/** Only CameraX-enumerated cameras are selectable. Physical IDs are diagnostic, not forced selectors. */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
internal class CameraCatalog(provider: ProcessCameraProvider) {
    data class Entry(val info: CameraInfo, val lens: CameraLens)
    val entries: List<Entry>
    init {
        val infos = provider.availableCameraInfos
        val defaults = listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA).mapNotNull { selector ->
            runCatching { selector.filter(infos).firstOrNull() }.getOrNull()
        }.toSet()
        entries = infos.mapNotNull { info ->
            try {
                val facing = info.lensFacing
                if (facing != CameraSelector.LENS_FACING_BACK && facing != CameraSelector.LENS_FACING_FRONT) return@mapNotNull null
                val id = Camera2CameraInfo.from(info).cameraId
                val physical = try { info.physicalCameraInfos.map { Camera2CameraInfo.from(it).cameraId }.sorted() }
                catch (e: Exception) { Log.w("CameraCatalog", "Physical metadata unavailable for $id", e); emptyList() }
                val ratio = info.intrinsicZoomRatio.takeIf { it.isFinite() && it > 0 } ?: 1f
                Entry(info, CameraLens(id, facing == CameraSelector.LENS_FACING_FRONT, ratio, info.hasFlashUnit(), info in defaults, physical)).also {
                    Log.i("CameraCatalog", "Camera $id front=${it.lens.front} intrinsic=$ratio flash=${it.lens.hasFlash} physical=$physical default=${it.lens.isDefault}")
                }
            } catch (e: Exception) { Log.w("CameraCatalog", "Ignoring unreadable camera", e); null }
        }
    }
    fun select(front: Boolean, requestedId: String?): Entry? {
        val facing = entries.filter { it.lens.front == front }.ifEmpty { entries }
        return facing.firstOrNull { it.lens.id == requestedId } ?: facing.firstOrNull { it.lens.isDefault } ?: facing.firstOrNull()
    }
}

data class CameraLens(val id: String, val front: Boolean, val intrinsicZoom: Float, val hasFlash: Boolean, val isDefault: Boolean, val physicalIds: List<String> = emptyList())
