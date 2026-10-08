package cn.kindyear.kwatermarkcam.feature.preset

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.core.designsystem.PageScaffold
import cn.kindyear.kwatermarkcam.domain.model.WatermarkPreset
import cn.kindyear.kwatermarkcam.domain.model.FieldType
import cn.kindyear.kwatermarkcam.feature.camera.CameraViewModel
import kotlinx.coroutines.launch

@Composable
fun PresetScreen(vm: CameraViewModel, back: () -> Unit, edit: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var ordered by remember { mutableStateOf<List<WatermarkPreset>>(emptyList()) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var center by remember { mutableFloatStateOf(0f) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<WatermarkPreset?>(null) }
    var renaming by remember { mutableStateOf<WatermarkPreset?>(null) }
    var renameValue by remember { mutableStateOf("") }
    LaunchedEffect(ui.presets, dragged) { if (dragged == null) ordered = ui.presets }
    fun move(preset: WatermarkPreset, direction: Int) {
        val index = ordered.indexOfFirst { it.id == preset.id }
        val target = index + direction
        if (target in ordered.indices && ordered[target].isPinned == preset.isPinned) {
            ordered = ordered.toMutableList().apply { add(target, removeAt(index)) }
            vm.reorder(ordered.map { it.id })
        }
    }
    PageScaffold(stringResource(R.string.presets), back, actions = {
        IconButton(onClick = { vm.beginEditor(null); edit() }) { Icon(Icons.Default.Add, stringResource(R.string.new_preset)) }
    }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.preset_edit_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp)) }
            items(ordered, key = { it.id }) { preset ->
                val isDragged = dragged == preset.id
                val upDescription = stringResource(R.string.move_up)
                val downDescription = stringResource(R.string.move_down)
                Card(Modifier.fillMaxWidth().animateItem().zIndex(if (isDragged) 1f else 0f).graphicsLayer {
                    val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == preset.id }
                    translationY = if (isDragged && visible != null) center - visible.offset - visible.size / 2f else 0f
                }
                    .semantics { customActions = listOf(CustomAccessibilityAction(upDescription) { move(preset, -1); true }, CustomAccessibilityAction(downDescription) { move(preset, 1); true }) },
                    colors = CardDefaults.cardColors(containerColor = if (preset.id == ui.selectedPreset?.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val dragDescription = stringResource(R.string.drag_reorder)
                        Icon(Icons.Default.DragHandle, dragDescription, Modifier.size(48.dp).padding(12.dp)
                            .pointerInput(preset.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragged = preset.id
                                        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == preset.id }
                                        center = (item?.offset ?: 0) + (item?.size ?: 0) / 2f
                                    },
                                    onDragEnd = { vm.reorder(ordered.map { it.id }); dragged = null },
                                    onDragCancel = { dragged = null; ordered = ui.presets },
                                    onDrag = { change, delta ->
                                        change.consume(); center += delta.y
                                        val hover = listState.layoutInfo.visibleItemsInfo.firstOrNull { center >= it.offset && center <= it.offset + it.size }
                                        val from = ordered.indexOfFirst { it.id == dragged }
                                        val to = ordered.indexOfFirst { it.id == hover?.key }
                                        if (from >= 0 && to >= 0 && from != to && ordered[from].isPinned == ordered[to].isPinned) ordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
                                        val viewport = listState.layoutInfo.viewportEndOffset
                                        if (center < 80 || center > viewport - 80) scope.launch { val scroll = listState.scrollBy(if (center < 80) -18f else 18f); center -= scroll }
                                    },
                                )
                            })
                        Column(Modifier.weight(1f).clickable { vm.select(preset); back() }.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(preset.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                if (preset.isPinned) Icon(Icons.Default.PushPin, stringResource(R.string.pin), Modifier.size(18.dp))
                                if (preset.id == ui.selectedPreset?.id) Icon(Icons.Default.CheckCircle, stringResource(R.string.preset_selected_state), Modifier.padding(start = 6.dp).size(18.dp))
                            }
                            Text(preset.fieldValues["project"].orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { menu = preset.id }) { Icon(Icons.Default.MoreVert, stringResource(R.string.edit)) }
                            DropdownMenu(expanded = menu == preset.id, onDismissRequest = { menu = null }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.edit_preset)) }, onClick = { menu = null; vm.beginEditor(preset); edit() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = null; renaming = preset; renameValue = preset.name })
                                DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = { menu = null; vm.duplicate(preset) })
                                DropdownMenuItem(text = { Text(stringResource(if (preset.isPinned) R.string.unpin else R.string.pin)) }, onClick = { menu = null; vm.pin(preset) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { menu = null; move(preset, -1) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { menu = null; move(preset, 1) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, onClick = { menu = null; deleting = preset })
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { preset -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_title)) }, text = { Text(stringResource(R.string.delete_message, preset.name)) },
        confirmButton = { TextButton(onClick = { vm.delete(preset); deleting = null }) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
    renaming?.let { preset -> AlertDialog(onDismissRequest = { renaming = null }, title = { Text(stringResource(R.string.rename)) },
        text = { OutlinedTextField(renameValue, { renameValue = it.take(80) }, label = { Text(stringResource(R.string.name)) }, singleLine = true) },
        confirmButton = { TextButton(enabled = renameValue.isNotBlank(), onClick = { vm.rename(preset.id, renameValue); renaming = null }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable
fun PresetEditorScreen(vm: CameraViewModel, back: () -> Unit) {
    val draft by vm.editor.collectAsStateWithLifecycle()
    var discard by remember { mutableStateOf(false) }
    fun leave() { if (!draft.saving) { if (draft.dirty) discard = true else back() } }
    BackHandler { leave() }
    val template = vm.registry.template(draft.templateId)
    PageScaffold(stringResource(if (draft.id == null) R.string.new_preset else R.string.edit_preset), ::leave, actions = {
        TextButton(onClick = vm::saveEditor, enabled = draft.name.isNotBlank() && !draft.saving) { Text(stringResource(R.string.save)) }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(stringResource(template.nameRes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(draft.name, vm::editorName, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text(stringResource(R.string.name)) }, singleLine = true, enabled = !draft.saving,
                    supportingText = { Text(stringResource(R.string.validation_name)) })
            }
            items(template.fields.sortedBy { it.order }, key = { it.id }) { field ->
                Column(Modifier.fillMaxWidth().alpha(if (field.id in draft.hidden) .55f else 1f)) {
                    if (field.editable) OutlinedTextField(draft.values[field.id].orEmpty(), { vm.editorField(field.id, it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(field.labelRes)) },
                        enabled = !draft.saving, minLines = 1, maxLines = 4,
                        keyboardOptions = KeyboardOptions(keyboardType = if (field.type == FieldType.NUMBER) KeyboardType.Decimal else KeyboardType.Text),
                        supportingText = { Text(stringResource(if (field.type == FieldType.ADDRESS) R.string.automatic_address else R.string.field_limit)) },
                        trailingIcon = { if (!draft.values[field.id].isNullOrEmpty()) IconButton(onClick = { vm.editorField(field.id, "") }, enabled = !draft.saving) { Icon(Icons.Default.Clear, stringResource(R.string.clear)) } })
                    else ListItem(headlineContent = { Text(stringResource(field.labelRes)) }, supportingContent = { Text(stringResource(R.string.automatic_time)) }, leadingContent = { Icon(Icons.Default.Schedule, null) })
                    if (field.hideable) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(field.id !in draft.hidden, { vm.editorHidden(field.id, !it) }, enabled = !draft.saving)
                        Text(stringResource(R.string.hidden_field), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { OutlinedButton(onClick = vm::resetEditor, enabled = !draft.saving) { Text(stringResource(R.string.reset)) } }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.discard_title)) }, text = { Text(stringResource(R.string.discard_message)) },
        confirmButton = { TextButton(onClick = { discard = false; back() }) { Text(stringResource(R.string.discard)) } }, dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.continue_edit)) } })
}
