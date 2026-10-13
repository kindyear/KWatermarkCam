package cn.kindyear.kwatermarkcam.feature.watermark

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(vm: CameraViewModel, back: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var inspecting by rememberSaveable { mutableStateOf<String?>(null) }
    val resources = androidx.compose.ui.platform.LocalResources.current
    val timestamp = remember { System.currentTimeMillis() }
    val address = stringResource(R.string.sample_address)
    val sampleValues = mapOf("photographer" to stringResource(R.string.sample_person), "asset" to stringResource(R.string.sample_asset),
        "project" to stringResource(R.string.sample_task), "place" to stringResource(R.string.sample_place), "note" to stringResource(R.string.sample_caption),
        "team" to stringResource(R.string.sample_team), "status" to stringResource(R.string.sample_status))
    val scenarioValues = mapOf(
        "construction-default" to mapOf("project" to stringResource(R.string.sample_project)),
        "property-handover" to mapOf("project" to stringResource(R.string.sample_property), "status" to stringResource(R.string.sample_completed)),
        "safety-patrol" to mapOf("place" to stringResource(R.string.sample_check_area), "status" to stringResource(R.string.sample_completed)),
        "delivery-receipt" to mapOf("asset" to stringResource(R.string.sample_delivery), "status" to stringResource(R.string.sample_completed)),
        "daily-moment" to mapOf("place" to stringResource(R.string.sample_daily)),
    )
    fun sample(template: WatermarkTemplate) = vm.registry.snapshot(WatermarkPreset("sample", template.id, "",
        vm.registry.defaults(template.id) + sampleValues + scenarioValues[template.id].orEmpty()),
        WatermarkContext(timestamp, LocationSnapshot(LocationStatus.SUCCESS, address = address)), true)
    val filtered = vm.registry.templates.filter { category == null || it.category.name == category }
    PageScaffold(stringResource(R.string.templates), back) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Style, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.template_count, vm.registry.templates.size), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.template_catalog_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val choices = listOf(null to R.string.template_all, TemplateCategory.CONSTRUCTION.name to R.string.template_category_construction,
                    TemplateCategory.WORK.name to R.string.template_category_work, TemplateCategory.LIFE.name to R.string.template_category_life)
                choices.forEach { (key, label) -> item { FilterChip(selected = category == key, onClick = { category = key }, label = { Text(stringResource(label)) }) } }
            }
            LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(filtered, key = { it.id }) { template ->
                    val active = template.id == ui.settings.templateId
                    OutlinedCard(onClick = { inspecting = template.id }, border = BorderStroke(if (active) 2.dp else 1.dp,
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        WatermarkSample(vm.renderer, sample(template), Modifier.fillMaxWidth().height(150.dp).clip(MaterialTheme.shapes.medium))
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(template.nameRes), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                if (active) Icon(Icons.Default.CheckCircle, stringResource(R.string.preset_selected_state), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(stringResource(template.descriptionRes), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, minLines = 2,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    inspecting?.let { id ->
        val template = vm.registry.template(id)
        ModalBottomSheet(onDismissRequest = { inspecting = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(template.nameRes), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(template.descriptionRes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                WatermarkSample(vm.renderer, sample(template), Modifier.fillMaxWidth().height(240.dp).clip(MaterialTheme.shapes.large))
                Text(stringResource(R.string.template_preview_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(template.fields.joinToString(" · ") { resources.getString(it.labelRes) }, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { vm.template(template.id); inspecting = null; back() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.template_use)) }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
