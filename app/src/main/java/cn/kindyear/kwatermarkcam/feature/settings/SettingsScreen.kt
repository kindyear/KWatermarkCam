package cn.kindyear.kwatermarkcam.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.kindyear.kwatermarkcam.BuildConfig
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.core.designsystem.PageScaffold
import cn.kindyear.kwatermarkcam.domain.model.LocationDisplay
import cn.kindyear.kwatermarkcam.feature.camera.CameraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: CameraViewModel, back: () -> Unit, navigate: (String) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings = ui.settings
    val hardware by vm.camera.state.collectAsStateWithLifecycle()
    PageScaffold(stringResource(R.string.settings), back) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(stringResource(R.string.settings_intro), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(stringResource(R.string.settings_intro_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            item { SettingsGroup(R.string.appearance, Icons.Default.Palette) {
                ChoiceSetting(R.string.theme_mode, settings.theme, listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark)) { vm.text("theme", it) }
                SettingToggle(R.string.dynamic_color, R.string.dynamic_color_hint, Icons.Default.ColorLens, settings.dynamicColor) { vm.bool("dynamic", it) }
            } }
            item { SettingsGroup(R.string.camera_defaults, Icons.Default.CameraAlt) {
                ChoiceSetting(R.string.camera_lens_default, if (settings.frontCamera) "front" else "back", listOf("back" to R.string.camera_back, "front" to R.string.camera_front)) { vm.bool("front", it == "front") }
                ChoiceSetting(R.string.camera_aspect_default, if (settings.wideAspect) "wide" else "standard", listOf("standard" to R.string.aspect_standard, "wide" to R.string.aspect_wide)) { vm.bool("wide", it == "wide") }
                ChoiceSetting(R.string.default_flash, settings.flash, listOf("off" to R.string.flash_off, "on" to R.string.flash_on, "auto" to R.string.flash_auto)) { vm.text("flash", it) }
                SettingToggle(R.string.grid, R.string.grid_hint, Icons.Default.GridOn, settings.grid) { vm.bool("grid", it) }
                if (hardware.lenses.isNotEmpty()) ListItem(headlineContent = { Text(stringResource(R.string.camera_detected, hardware.lenses.size)) }, supportingContent = { Text(stringResource(R.string.camera_open_capabilities)) }, leadingContent = { Icon(Icons.Default.Cameraswitch, null) })
            } }
            item { SettingsGroup(R.string.watermark_settings, Icons.Default.Layers) {
                SettingLink(R.string.templates, stringResource(vm.registry.template(settings.templateId).nameRes), Icons.Default.Style) { navigate("templates") }
                SettingLink(R.string.content_preset_label, ui.selectedPreset?.name.orEmpty(), Icons.Default.FolderOpen) { navigate("preset-picker") }
                SettingToggle(R.string.show_watermark, R.string.watermark_show_hint, Icons.Default.Visibility, settings.watermark) { vm.bool("watermark", it) }
            } }
            item { SettingsGroup(R.string.location_settings, Icons.Default.LocationOn) {
                SettingToggle(R.string.location, R.string.location_privacy, Icons.Default.MyLocation, settings.location) { vm.bool("location", it) }
                ChoiceSetting(R.string.location_display, settings.locationDisplay.name, listOf(LocationDisplay.NEARBY_NAME.name to R.string.location_nearby_name, LocationDisplay.COORDINATES.name to R.string.location_coordinates)) { vm.text("location_display", it) }
                Text(stringResource(R.string.location_display_hint), Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { SettingsGroup(R.string.storage, Icons.Default.PhotoLibrary) {
                ListItem(headlineContent = { Text(stringResource(R.string.storage_path_title)) }, supportingContent = { Text(stringResource(R.string.storage_path)) }, leadingContent = { Icon(Icons.Default.Folder, null) })
                SettingLink(R.string.gallery, stringResource(R.string.gallery_entry_hint), Icons.Default.Collections) { navigate("gallery") }
            } }
            item {
                OutlinedCard(onClick = { navigate("about") }, Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(stringResource(R.string.app_name)) }, supportingContent = { Text(stringResource(R.string.version, BuildConfig.VERSION_NAME)) },
                        leadingContent = { Image(painterResource(R.drawable.ic_app), null, Modifier.size(44.dp)) }, trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) })
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: Int, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(title), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        OutlinedCard(Modifier.fillMaxWidth()) { Column(content = content) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSetting(title: Int, selected: String, choices: List<Pair<String, Int>>, select: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            choices.forEachIndexed { index, (key, label) -> SegmentedButton(selected = key == selected, onClick = { select(key) }, shape = SegmentedButtonDefaults.itemShape(index, choices.size)) { Text(stringResource(label)) } }
        }
    }
}

@Composable
private fun SettingToggle(title: Int, hint: Int, icon: ImageVector, checked: Boolean, change: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(stringResource(title)) }, supportingContent = { Text(stringResource(hint)) }, leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Switch(checked, change) }, modifier = Modifier.clickable { change(!checked) })
}

@Composable
private fun SettingLink(title: Int, subtitle: String, icon: ImageVector, click: () -> Unit) {
    ListItem(headlineContent = { Text(stringResource(title)) }, supportingContent = { Text(subtitle) }, leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }, modifier = Modifier.clickable(onClick = click))
}
