package cn.kindyear.kwatermarkcam.feature.watermark

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.core.designsystem.PageScaffold
import cn.kindyear.kwatermarkcam.domain.model.*
import cn.kindyear.kwatermarkcam.feature.camera.CameraViewModel

@Composable
fun TemplateScreen(vm: CameraViewModel, back: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val timestamp = remember { System.currentTimeMillis() }
    val address = stringResource(R.string.sample_address)
    val person = stringResource(R.string.sample_person)
    val asset = stringResource(R.string.sample_asset)
    val task = stringResource(R.string.sample_task)
    val place = stringResource(R.string.sample_place)
    val caption = stringResource(R.string.sample_caption)
    val team = stringResource(R.string.sample_team)
    val status = stringResource(R.string.sample_status)
    PageScaffold(stringResource(R.string.templates), back) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.template_catalog_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(vm.registry.templates, key = { it.id }) { template ->
                val values = vm.registry.defaults(template.id) + mapOf("photographer" to person, "asset" to asset, "project" to task, "place" to place, "note" to caption, "team" to team, "status" to status)
                val sample = vm.registry.snapshot(WatermarkPreset("sample", template.id, "", values), WatermarkContext(timestamp, LocationSnapshot(LocationStatus.SUCCESS, address = address)), true)
                ElevatedCard(onClick = { vm.template(template.id); back() }, Modifier.fillMaxWidth()) {
                    WatermarkSample(vm.renderer, sample, Modifier.fillMaxWidth().height(182.dp).clip(MaterialTheme.shapes.medium))
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(template.nameRes), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(template.descriptionRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (template.id == ui.settings.templateId) Icon(Icons.Default.CheckCircle, stringResource(R.string.preset_selected_state), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
