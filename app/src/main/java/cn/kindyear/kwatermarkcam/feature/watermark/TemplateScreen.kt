package cn.kindyear.kwatermarkcam.feature.watermark

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.core.designsystem.PageScaffold
import cn.kindyear.kwatermarkcam.feature.camera.CameraViewModel

@Composable
fun TemplateScreen(vm: CameraViewModel, back: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    PageScaffold(stringResource(R.string.templates), back) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.template_count), color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.registry.templates.forEach { template ->
                ElevatedCard(onClick = { vm.template(template.id); back() }, Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(stringResource(template.nameRes)) }, supportingContent = { Text(stringResource(R.string.template_description)) },
                        leadingContent = { Icon(Icons.Default.Layers, null) }, trailingContent = { if (template.id == ui.settings.templateId) Icon(Icons.Default.CheckCircle, stringResource(R.string.preset_selected_state), tint = MaterialTheme.colorScheme.primary) })
                    Text(stringResource(R.string.select_template), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
