package cn.kindyear.kwatermarkcam

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.kindyear.kwatermarkcam.core.designsystem.WatermarkTheme
import cn.kindyear.kwatermarkcam.feature.camera.CameraViewModel
import cn.kindyear.kwatermarkcam.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        val startedAt = SystemClock.uptimeMillis()
        var startupDataLoading = true
        super.onCreate(savedInstanceState)
        // Wait only for initial settings/presets, never camera permission or hardware.
        // A bounded handoff leaves the existing loading/error UI available if storage stalls.
        splashScreen.setKeepOnScreenCondition {
            startupDataLoading && SystemClock.uptimeMillis() - startedAt < STARTUP_HANDOFF_LIMIT_MS
        }
        enableEdgeToEdge()
        setContent {
            val model: CameraViewModel = viewModel()
            val state = model.ui.collectAsStateWithLifecycle().value
            SideEffect { startupDataLoading = state.loading }
            val settings = state.settings
            WatermarkTheme(settings.theme, settings.dynamicColor) { AppNavigation(model) }
        }
    }

    private companion object {
        const val STARTUP_HANDOFF_LIMIT_MS = 2_000L
    }
}
