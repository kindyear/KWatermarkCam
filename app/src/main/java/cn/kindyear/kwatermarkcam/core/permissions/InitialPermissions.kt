package cn.kindyear.kwatermarkcam.core.permissions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.feature.camera.CameraViewModel

val LocalPermissionRefresh = compositionLocalOf { 0 }

/** A single introductory decision precedes the platform's grouped permission request. */
@Composable
fun InitialPermissions(vm: CameraViewModel, content: @Composable () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var revision by rememberSaveable { mutableIntStateOf(0) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val permissions = remember { arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        revision++
        vm.bool("initial_permissions", true)
        if (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true || result[Manifest.permission.ACCESS_FINE_LOCATION] == true) vm.bool("location", true)
        vm.refreshLocation()
    }
    CompositionLocalProvider(LocalPermissionRefresh provides revision) { content() }
    val allGranted = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    LaunchedEffect(ui.loading, ui.settings.initialPermissionsHandled, allGranted) {
        if (!ui.loading && !ui.settings.initialPermissionsHandled && allGranted) vm.bool("initial_permissions", true)
    }
    if (!ui.loading && !ui.dataError && !ui.settings.initialPermissionsHandled && !submitted && !allGranted) {
        AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.initial_permissions_title)) },
            text = { Text(stringResource(R.string.initial_permissions_body)) },
            confirmButton = { TextButton(onClick = {
                submitted = true
                // Persist the introductory decision even if the app is killed in the system dialog.
                vm.bool("initial_permissions", true)
                launcher.launch(permissions)
            }) { Text(stringResource(R.string.initial_permissions_allow)) } },
            dismissButton = { TextButton(onClick = { submitted = true; vm.bool("initial_permissions", true) }) { Text(stringResource(R.string.initial_permissions_later)) } })
    }
}
