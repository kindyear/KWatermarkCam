package cn.kindyear.kwatermarkcam.feature.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.*
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.core.camera.CameraController
import cn.kindyear.kwatermarkcam.core.storage.PhotoRepository
import cn.kindyear.kwatermarkcam.domain.model.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

private val cameraBackground = Color(0xFF101B20)
private val cameraForeground = Color(0xFFF2F7F8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(vm: CameraViewModel, navigate: (String) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val capture by vm.capture.collectAsStateWithLifecycle()
    val hardware by vm.camera.state.collectAsStateWithLifecycle()
    val position by vm.currentLocation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var discardPending by remember { mutableStateOf(false) }
    var rebind by remember { mutableIntStateOf(0) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshLocation() }
    LaunchedEffect(Unit) { if (!permission && !requested) { requested = true; cameraPermission.launch(Manifest.permission.CAMERA) } }
    LifecycleResumeEffect(Unit) {
        permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        vm.foreground(true)
        onPauseOrDispose { vm.foreground(false) }
    }
    fun requestLocation() {
        vm.bool("location", true)
        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    Column(Modifier.fillMaxSize().background(cameraBackground).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CameraIcon(
                when (ui.settings.flash) { "on" -> Icons.Default.FlashOn; "auto" -> Icons.Default.FlashAuto; else -> Icons.Default.FlashOff },
                stringResource(when (ui.settings.flash) { "on" -> R.string.flash_on; "auto" -> R.string.flash_auto; else -> R.string.flash_off }),
                enabled = hardware.hasFlash && !capture.busy,
            ) { vm.text("flash", when (ui.settings.flash) { "off" -> "on"; "on" -> "auto"; else -> "off" }) }
            TextButton(onClick = { vm.bool("wide", !ui.settings.wideAspect) }, enabled = !capture.busy) {
                Text(stringResource(if (ui.settings.wideAspect) R.string.aspect_wide else R.string.aspect_standard), color = cameraForeground)
            }
            Spacer(Modifier.weight(1f))
            CameraIcon(if (ui.settings.watermark) Icons.Default.Layers else Icons.Default.LayersClear, stringResource(R.string.show_watermark), !capture.busy) { vm.bool("watermark", !ui.settings.watermark) }
            CameraIcon(Icons.Default.Settings, stringResource(R.string.settings), !capture.busy) { navigate("settings") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (landscape) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                PreviewArea(vm, ui, capture, hardware, permission, rebind, Modifier.weight(1f).fillMaxHeight()) { rebind++ }
                Column(Modifier.width(236.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp)) {
                    LocationLine(position, ui.settings.location, ::requestLocation, vm::refreshLocation)
                    CameraBottom(vm, ui, capture, hardware, navigate, { sheet = true })
                }
            } else Column(Modifier.fillMaxSize()) {
                PreviewArea(vm, ui, capture, hardware, permission, rebind, Modifier.weight(1f).fillMaxWidth()) { rebind++ }
                LocationLine(position, ui.settings.location, ::requestLocation, vm::refreshLocation)
            }
        }
        if (!permission) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = { requested = true; cameraPermission.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.grant_camera)) }
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text(stringResource(R.string.open_settings), color = cameraForeground) }
            }
        }
        // In landscape the controls are beside the preview, leaving the photo unobstructed.
        Box(Modifier.fillMaxWidth()) {
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            if (configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                CameraBottom(vm, ui, capture, hardware, navigate, { sheet = true })
        }
        AnimatedVisibility(capture.pending != null && !capture.busy) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.recover_title), Modifier.weight(1f), color = cameraForeground, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = vm::retrySave) { Text(stringResource(R.string.retry_save)) }
                CameraIcon(Icons.Default.DeleteOutline, stringResource(R.string.pending_discard)) { discardPending = true }
            }
        }
        if (ui.dataError) TextButton(onClick = vm::loadData, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.retry)) }
    }
    if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false }) {
        Text(stringResource(R.string.switch_preset), Modifier.padding(24.dp), style = MaterialTheme.typography.headlineSmall)
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
            items(ui.presets.size, key = { ui.presets[it].id }) { index ->
                val preset = ui.presets[index]
                ListItem(
                    headlineContent = { Text(preset.name) }, supportingContent = { Text(preset.fieldValues["project"].orEmpty(), maxLines = 2) },
                    leadingContent = { Icon(if (preset.isPinned) Icons.Default.PushPin else Icons.Default.WorkOutline, null) },
                    trailingContent = { if (preset.id == ui.selectedPreset?.id) Icon(Icons.Default.CheckCircle, stringResource(R.string.preset_selected_state), tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { vm.select(preset); sheet = false },
                )
            }
        }
        TextButton(onClick = { sheet = false; navigate("presets") }, modifier = Modifier.padding(16.dp)) { Text(stringResource(R.string.presets)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
        Spacer(Modifier.navigationBarsPadding())
    }
    if (discardPending) AlertDialog(onDismissRequest = { discardPending = false }, title = { Text(stringResource(R.string.pending_discard_title)) }, text = { Text(stringResource(R.string.pending_discard_body)) },
        confirmButton = { TextButton(onClick = { vm.discardPending(); discardPending = false }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { discardPending = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun PreviewArea(vm: CameraViewModel, ui: CameraUiState, capture: CaptureState, hardware: CameraController.State, permission: Boolean, rebind: Int, modifier: Modifier, retry: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(context).apply { keepScreenOn = true; scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var focus by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focus) { if (focus != null) { delay(900); focus = null } }
    DisposableEffect(owner, size, ui.settings.frontCamera, permission, rebind) {
        if (permission && size.width > 0 && size.height > 0) vm.camera.bind(owner, view, ui.settings.frontCamera, ui.settings.flash)
        onDispose { vm.camera.unbind() }
    }
    LaunchedEffect(ui.settings.flash, hardware.hasFlash) { vm.camera.setFlash(ui.settings.flash) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val ratio = if (ui.settings.wideAspect) (if (landscape) 16f / 9 else 9f / 16) else (if (landscape) 4f / 3 else 3f / 4)
        val width = min(maxWidth.value, maxHeight.value * ratio).dp
        Box(Modifier.width(width).aspectRatio(ratio).clip(RoundedCornerShape(16.dp)).onSizeChanged { size = it }.background(Color.Black)) {
            if (permission) {
                AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
                val currentHardware by rememberUpdatedState(hardware)
                val currentBusy by rememberUpdatedState(capture.busy)
                Box(Modifier.fillMaxSize()
                    .pointerInput(vm.camera) { detectTapGestures { p -> if (!currentBusy) { vm.camera.focus(p.x, p.y); focus = p } } }
                    .pointerInput(vm.camera) { detectTransformGestures { _, _, zoom, _ -> if (!currentBusy && zoom != 1f) vm.camera.zoom(currentHardware.zoom * zoom) } })
                if (ui.settings.grid) Canvas(Modifier.fillMaxSize()) {
                    for (i in 1..2) { drawLine(Color.White.copy(alpha = .35f), Offset(this.size.width * i / 3f, 0f), Offset(this.size.width * i / 3f, this.size.height)); drawLine(Color.White.copy(alpha = .35f), Offset(0f, this.size.height * i / 3f), Offset(this.size.width, this.size.height * i / 3f)) }
                }
                WatermarkOverlay(vm)
                focus?.let { p -> Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFFF5BD4F), 27.dp.toPx(), p, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())) } }
                if (!hardware.ready || hardware.error) Column(Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(if (hardware.error) R.string.camera_unavailable else R.string.camera_loading), color = cameraForeground)
                    if (hardware.error) Button(onClick = retry) { Text(stringResource(R.string.retry)) } else CircularProgressIndicator(Modifier.padding(12.dp))
                }
                if (hardware.ready) Row(Modifier.align(Alignment.TopCenter).padding(12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    hardware.zoomStops.forEach { zoom ->
                        val text = stringResource(R.string.zoom_value, String.format(Locale.ROOT, "%.1f", zoom))
                        val zoomDescription = stringResource(R.string.zoom, zoom.toString())
                        TextButton(onClick = { vm.camera.zoom(zoom) }, enabled = !capture.busy,
                            colors = ButtonDefaults.textButtonColors(containerColor = if (kotlin.math.abs(hardware.zoom - zoom) < .1f) Color(0xFF246A7A) else Color.Black.copy(alpha = .5f), contentColor = cameraForeground),
                            modifier = Modifier.semantics { contentDescription = zoomDescription }) { Text(text) }
                    }
                }
            } else Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CameraAlt, null, Modifier.size(56.dp), tint = Color(0xFF8CD2E2))
                Text(stringResource(R.string.camera_permission_title), color = cameraForeground, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.camera_permission_body), color = cameraForeground.copy(alpha = .7f))
            }
            if (capture.capturing || capture.saving) Surface(Modifier.align(Alignment.Center), color = Color.Black.copy(alpha = .8f), shape = MaterialTheme.shapes.medium) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(if (capture.capturing) R.string.capturing else R.string.saving), Modifier.padding(start = 12.dp), color = cameraForeground)
                }
            }
        }
    }
}

@Composable
private fun CameraBottom(vm: CameraViewModel, ui: CameraUiState, capture: CaptureState, hardware: CameraController.State, navigate: (String) -> Unit, openSheet: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { navigate("templates") }, enabled = !capture.busy) { Text(stringResource(vm.registry.template(ui.settings.templateId).nameRes), color = cameraForeground.copy(alpha = .7f), style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.weight(1f))
            CameraIcon(Icons.Default.Edit, stringResource(R.string.edit_preset), enabled = ui.selectedPreset != null && !capture.busy) { vm.beginEditor(ui.selectedPreset); navigate("editor") }
        }
        Surface(onClick = openSheet, enabled = !capture.busy, color = Color(0xFF25363D), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WorkOutline, null, tint = Color(0xFFF5BD4F))
                Text(ui.selectedPreset?.name ?: stringResource(R.string.empty_preset), Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = cameraForeground, style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Default.ExpandMore, stringResource(R.string.switch_preset), tint = cameraForeground)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            val last = capture.lastPhoto?.uri ?: ui.photos.firstOrNull()?.uri
            PhotoThumbnail(last, vm.photoRepository, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = !capture.busy) { navigate("gallery") })
            val shutterDescription = stringResource(R.string.shutter)
            Box(Modifier.size(78.dp).clip(CircleShape).border(3.dp, if (hardware.ready && !capture.busy) Color.White else Color.Gray, CircleShape)
                .clickable(enabled = hardware.ready && !capture.busy && capture.pending == null && !ui.loading && !ui.dataError, onClick = vm::shoot)
                .semantics { contentDescription = shutterDescription }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(62.dp).background(if (capture.busy) Color.Gray else Color.White, CircleShape))
            }
            CameraIcon(Icons.Default.Cameraswitch, stringResource(R.string.switch_camera), hardware.canSwitch && !capture.busy) { vm.bool("front", !hardware.front) }
        }
    }
}
@Composable
private fun CameraIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean = true, action: () -> Unit) {
    IconButton(onClick = action, enabled = enabled) { Icon(icon, description, tint = if (enabled) cameraForeground else cameraForeground.copy(alpha = .3f)) }
}
@Composable
fun PhotoThumbnail(uri: String?, repository: PhotoRepository, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) { value = uri?.let { repository.thumbnail(it) } }
    Box(modifier.background(Color(0xFF25363D)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(requireNotNull(bitmap).asImageBitmap(), stringResource(R.string.gallery), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.PhotoLibrary, stringResource(R.string.gallery), tint = Color.LightGray)
    }
}
@Composable
private fun LocationLine(location: LocationSnapshot, enabled: Boolean, request: () -> Unit, refresh: () -> Unit) {
    val cachedTime = location.measuredAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) }.orEmpty()
    val text = when (location.status) {
        LocationStatus.DISABLED -> stringResource(R.string.location_disabled)
        LocationStatus.PERMISSION_REQUIRED -> stringResource(R.string.location_permission)
        LocationStatus.SERVICES_OFF -> stringResource(R.string.location_off)
        LocationStatus.ACQUIRING -> stringResource(R.string.location_loading)
        LocationStatus.SUCCESS -> stringResource(R.string.location_success)
        LocationStatus.CACHED -> stringResource(R.string.location_cached, cachedTime)
        LocationStatus.TIMEOUT -> stringResource(R.string.location_timeout)
        LocationStatus.FAILED -> stringResource(R.string.location_failed)
        LocationStatus.GEOCODING_FAILED -> stringResource(R.string.location_geocode_failed)
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.LocationOn, null, Modifier.size(14.dp), tint = Color(0xFF8CD2E2))
        Text(text, Modifier.weight(1f).padding(start = 6.dp), color = cameraForeground.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall, maxLines = 2)
        IconButton(onClick = if (!enabled || location.status == LocationStatus.PERMISSION_REQUIRED) request else refresh) { Icon(if (!enabled) Icons.Default.AddLocationAlt else Icons.Default.Refresh, stringResource(R.string.location_refresh), Modifier.size(20.dp), tint = cameraForeground) }
    }
}

/** Dynamic clock changes invalidate only the overlay, keeping camera controls stable. */
@Composable
private fun WatermarkOverlay(vm: CameraViewModel) {
    val snapshot by vm.watermark.collectAsStateWithLifecycle()
    Canvas(Modifier.fillMaxSize()) {
        snapshot?.let { frozen -> drawIntoCanvas { vm.renderer.draw(it.nativeCanvas, size.width.toInt(), size.height.toInt(), frozen) } }
    }
}
