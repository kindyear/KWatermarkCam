package cn.kindyear.kwatermarkcam

import cn.kindyear.kwatermarkcam.core.camera.CameraController
import org.junit.Assert.*
import org.junit.Test

class CameraCapabilitiesTest {
    @Test fun zoomPresetsExcludeUnsupportedRatios() {
        val state = CameraController.State(minZoom = 1f, maxZoom = 2.4f)
        assertEquals(listOf(1f, 2f), state.zoomStops)
    }
    @Test fun ultraWideOnlyAppearsWhenExposedByCamera() {
        val state = CameraController.State(minZoom = .7f, maxZoom = 1.5f)
        assertEquals(listOf(.7f, 1f), state.zoomStops)
        assertFalse(state.zoomStops.contains(.5f))
    }
}
