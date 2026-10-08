package cn.kindyear.kwatermarkcam

import android.Manifest
import android.util.Log
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import cn.kindyear.kwatermarkcam.feature.camera.CameraViewModel
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Hardware integration test. A pass covers the connected device, not every vendor. */
@RunWith(AndroidJUnit4::class)
class CameraLifecycleTest {
    @get:Rule val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @Test fun initializeCaptureControlsSwitchAndResume() = runBlocking {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: CameraViewModel
        scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); model = ViewModelProvider(it)[CameraViewModel::class.java] }
        try {
            withTimeout(30_000) { while (!model.camera.state.value.ready || model.ui.value.loading || model.capture.value.recovering) {
                Log.i("CameraLifecycleTest", "Waiting for init: camera=${model.camera.state.value}, loading=${model.ui.value.loading}, recovering=${model.capture.value.recovering}")
                delay(500)
            } }
            val state = model.camera.state.value
            assertTrue(state.zoomStops.all { it in state.minZoom..state.maxZoom })
            withContext(Dispatchers.Main) { model.camera.zoom(state.maxZoom); model.camera.setFlash("auto"); model.camera.focus(100f, 100f) }
            delay(400)
            withContext(Dispatchers.Main) { model.camera.zoom(1f.coerceIn(state.minZoom, state.maxZoom)); model.shoot() }
            withTimeout(30_000) { while (model.capture.value.lastPhoto == null) {
                Log.i("CameraLifecycleTest", "Waiting for save: capturing=${model.capture.value.capturing}, saving=${model.capture.value.saving}")
                delay(500)
            } }
            val photo = model.capture.value.lastPhoto!!
            assertTrue(photo.width > 0 && photo.height > 0)
            val ratio = minOf(photo.width, photo.height).toDouble() / maxOf(photo.width, photo.height)
            assertEquals(if (model.ui.value.settings.wideAspect) 9.0 / 16 else 3.0 / 4, ratio, .015)
            Log.i("CameraLifecycleTest", "Saved normalized image: ${photo.width} x ${photo.height}")
            val context: android.content.Context = ApplicationProvider.getApplicationContext()
            context.contentResolver.openInputStream(Uri.parse(photo.uri))!!.use { assertTrue(it.read() >= 0) }
            context.contentResolver.delete(Uri.parse(photo.uri), null, null)
            model.photoRepository.forgetRecord(photo.id)
            if (state.canSwitch) {
                withContext(Dispatchers.Main) { model.bool("front", !state.front) }
                withTimeout(20_000) { while (!model.camera.state.value.ready || model.camera.state.value.front == state.front) delay(100) }
                withContext(Dispatchers.Main) { model.bool("front", state.front) }
                withTimeout(20_000) { while (!model.camera.state.value.ready || model.camera.state.value.front != state.front) delay(100) }
            }
            val originalPreset = requireNotNull(model.ui.value.selectedPreset)
            withContext(Dispatchers.Main) {
                model.beginEditor(null); model.editorName("集成测试临时预设"); model.saveEditor()
            }
            withTimeout(10_000) { while (model.ui.value.selectedPreset?.id == originalPreset.id) delay(100) }
            val temporaryPreset = requireNotNull(model.ui.value.selectedPreset)
            assertEquals(temporaryPreset.id, model.editor.value.id)
            assertFalse(model.editor.value.dirty)
            withContext(Dispatchers.Main) { model.delete(temporaryPreset) }
            withTimeout(10_000) { while (model.ui.value.selectedPreset?.id == temporaryPreset.id) delay(100) }
            assertNotNull(model.ui.value.selectedPreset)
            withContext(Dispatchers.Main) { model.select(originalPreset) }
            scenario.moveToState(Lifecycle.State.CREATED)
            withTimeout(15_000) { while (model.camera.state.value.ready) delay(100) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            withTimeout(20_000) { while (!model.camera.state.value.ready) delay(100) }
        } finally { scenario.close() }
    }
}
