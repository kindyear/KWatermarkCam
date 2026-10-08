package cn.kindyear.kwatermarkcam.navigation

import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.navigation.compose.*
import kotlinx.coroutines.launch
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.feature.camera.*
import cn.kindyear.kwatermarkcam.feature.gallery.GalleryScreen
import cn.kindyear.kwatermarkcam.feature.preset.*
import cn.kindyear.kwatermarkcam.feature.settings.SettingsScreen
import cn.kindyear.kwatermarkcam.feature.watermark.TemplateScreen

@Composable
fun AppNavigation(vm: CameraViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val entry by nav.currentBackStackEntryAsState()
    val darkBars = entry?.destination?.route == "camera" || MaterialTheme.colorScheme.surface.luminance() < .5f
    val activity = LocalActivity.current
    val view = LocalView.current
    SideEffect {
        activity?.let { host -> WindowCompat.getInsetsController(host.window, view).apply {
            isAppearanceLightStatusBars = !darkBars
            isAppearanceLightNavigationBars = !darkBars
        } }
    }
    LaunchedEffect(vm, resources) {
        vm.events.collect { event -> when (event) {
            UiEvent.EditorSaved -> if (nav.currentDestination?.route == "editor") nav.popBackStack()
            is UiEvent.Message -> launch {
                val result = snackbar.showSnackbar(resources.getString(event.resource), if (event.retrySave) resources.getString(R.string.retry) else null, withDismissAction = true)
                if (result == SnackbarResult.ActionPerformed && event.retrySave) vm.retrySave()
            }
        } }
    }
    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = "camera") {
            composable("camera") { CameraScreen(vm) { nav.navigate(it) { launchSingleTop = true } } }
            composable("presets") { PresetScreen(vm, { nav.popBackStack() }, { nav.navigate("editor") }) }
            composable("editor") { PresetEditorScreen(vm) { nav.popBackStack() } }
            composable("templates") { TemplateScreen(vm) { nav.popBackStack() } }
            composable("gallery") { GalleryScreen(vm) { nav.popBackStack() } }
            composable("settings") { SettingsScreen(vm, { nav.popBackStack() }) { nav.navigate(it) } }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}
