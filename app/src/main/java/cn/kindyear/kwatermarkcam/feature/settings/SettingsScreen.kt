package cn.kindyear.kwatermarkcam.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.kindyear.kwatermarkcam.BuildConfig
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.core.designsystem.PageScaffold
import cn.kindyear.kwatermarkcam.feature.camera.CameraViewModel

@Composable
fun SettingsScreen(vm: CameraViewModel, back: () -> Unit, navigate: (String) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings = ui.settings
    var licenses by remember { mutableStateOf(false) }
    PageScaffold(stringResource(R.string.settings), back) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionTitle(stringResource(R.string.appearance))
            listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark).forEach { (key, label) ->
                ListItem(headlineContent = { Text(stringResource(label)) }, trailingContent = { RadioButton(settings.theme == key, { vm.text("theme", key) }) }, modifier = Modifier.clickable { vm.text("theme", key) })
            }
            Toggle(stringResource(R.string.dynamic_color), settings.dynamicColor) { vm.bool("dynamic", it) }
            SectionTitle(stringResource(R.string.camera_defaults))
            Toggle(stringResource(R.string.default_lens), settings.frontCamera) { vm.bool("front", it) }
            Toggle(stringResource(R.string.default_aspect), settings.wideAspect) { vm.bool("wide", it) }
            Toggle(stringResource(R.string.grid), settings.grid) { vm.bool("grid", it) }
            Text(stringResource(R.string.default_flash), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("off" to R.string.flash_off, "on" to R.string.flash_on, "auto" to R.string.flash_auto).forEach { (key, label) -> FilterChip(settings.flash == key, { vm.text("flash", key) }, label = { Text(stringResource(label)) }) }
            }
            SectionTitle(stringResource(R.string.watermark_settings))
            ListItem(headlineContent = { Text(stringResource(R.string.default_template, stringResource(vm.registry.template(settings.templateId).nameRes))) }, modifier = Modifier.clickable { navigate("templates") })
            ListItem(headlineContent = { Text(stringResource(R.string.default_selected, ui.selectedPreset?.name.orEmpty())) }, modifier = Modifier.clickable { navigate("presets") })
            Toggle(stringResource(R.string.show_watermark), settings.watermark) { vm.bool("watermark", it) }
            Toggle(stringResource(R.string.location), settings.location) { vm.bool("location", it) }
            Text(stringResource(R.string.location_privacy), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            SectionTitle(stringResource(R.string.storage))
            Text(stringResource(R.string.storage_description), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            ListItem(headlineContent = { Text(stringResource(R.string.gallery)) }, modifier = Modifier.clickable { navigate("gallery") })
            SectionTitle(stringResource(R.string.about))
            ListItem(headlineContent = { Text(stringResource(R.string.app_name)) }, supportingContent = { Text(stringResource(R.string.version, BuildConfig.VERSION_NAME)) })
            ListItem(headlineContent = { Text(stringResource(R.string.licenses)) }, modifier = Modifier.clickable { licenses = true })
        }
    }
    if (licenses) AlertDialog(onDismissRequest = { licenses = false }, title = { Text(stringResource(R.string.licenses)) }, text = { Text(stringResource(R.string.licenses_body)) }, confirmButton = { TextButton(onClick = { licenses = false }) { Text(stringResource(R.string.confirm)) } })
}
@Composable private fun SectionTitle(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Text(title, Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
}
@Composable private fun Toggle(title: String, checked: Boolean, change: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, trailingContent = { Switch(checked, change) }, modifier = Modifier.clickable { change(!checked) })
}
