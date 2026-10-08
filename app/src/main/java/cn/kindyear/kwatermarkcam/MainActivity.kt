package cn.kindyear.kwatermarkcam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.kindyear.kwatermarkcam.core.designsystem.WatermarkTheme
import cn.kindyear.kwatermarkcam.feature.camera.CameraViewModel
import cn.kindyear.kwatermarkcam.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: CameraViewModel = viewModel()
            val settings = model.ui.collectAsStateWithLifecycle().value.settings
            WatermarkTheme(settings.theme, settings.dynamicColor) { AppNavigation(model) }
        }
    }
}
