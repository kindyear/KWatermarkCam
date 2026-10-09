package cn.kindyear.kwatermarkcam.feature.preset

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import cn.kindyear.kwatermarkcam.domain.model.PresetFolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import cn.kindyear.kwatermarkcam.domain.model.*
import cn.kindyear.kwatermarkcam.feature.watermark.WatermarkSample
import cn.kindyear.kwatermarkcam.feature.camera.CameraViewModel
import kotlinx.coroutines.launch

@Composable
fun PresetScreen(vm: CameraViewModel, back: () -> Unit, edit: () -> Unit, selecting: Boolean = false, manage: () -> Unit = {}) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    val folder = ui.folders.firstOrNull { it.id == folderId }
    val visiblePresets = ui.presets.filter { it.folderId == folderId }
    val children = ui.folders.filter { it.parentId == folderId }
    var ordered by remember(folderId) { mutableStateOf(visiblePresets) }
    var folderForm by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<PresetFolder?>(null) }
    var deletingFolder by remember { mutableStateOf<PresetFolder?>(null) }
    var newPreset by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<WatermarkPreset?>(null) }
    fun leave() { if (folderId != null) folderId = folder?.parentId else back() }
    BackHandler { leave() }
    LaunchedEffect(ui.folders, ui.loading) {
        if (!ui.loading && folderId != null && ui.folders.none { it.id == folderId }) folderId = null
    }
    var dragged by remember { mutableStateOf<String?>(null) }
    var center by remember { mutableFloatStateOf(0f) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<WatermarkPreset?>(null) }
    var renaming by remember { mutableStateOf<WatermarkPreset?>(null) }
    var renameValue by remember { mutableStateOf("") }
    // Database changes win over an unfinished drag. Otherwise a deleted/moved item can
    // survive in the local ordering list and expose actions for an already removed ID.
    LaunchedEffect(folderId, visiblePresets) {
        dragged = null
        ordered = visiblePresets
    }
    fun move(preset: WatermarkPreset, direction: Int) {
        val index = ordered.indexOfFirst { it.id == preset.id }
        val target = index + direction
        if (target in ordered.indices && ordered[target].isPinned == preset.isPinned) {
            ordered = ordered.toMutableList().apply { add(target, removeAt(index)) }
            vm.reorderFolder(folderId, ordered.map { it.id })
        }
    }
    PageScaffold(stringResource(if (selecting) R.string.switch_preset else R.string.preset_library), ::leave, actions = {
        if (selecting) TextButton(onClick = manage) { Text(stringResource(R.string.manage_presets)) }
        else {
            IconButton(onClick = { editingFolder = null; folderForm = true }) { Icon(Icons.Default.CreateNewFolder, stringResource(R.string.new_folder)) }
            IconButton(onClick = { newPreset = true }) { Icon(Icons.AutoMirrored.Filled.NoteAdd, stringResource(R.string.new_preset)) }
        }
    }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FolderBreadcrumb(ui.folders, folderId) { folderId = it }
                Text(stringResource(if (selecting) R.string.picker_hint else R.string.library_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
            }
            items(children, key = { "folder:" + it.id }) { child ->
                var folderMenu by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(child.name, maxLines = 2) },
                        supportingContent = { Text(stringResource(R.string.folder_items, ui.folders.count { it.parentId == child.id }, ui.presets.count { it.folderId == child.id })) },
                        leadingContent = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            if (!selecting) Box {
                                IconButton(onClick = { folderMenu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
                                DropdownMenu(folderMenu, { folderMenu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { folderMenu = false; editingFolder = child; folderForm = true })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { folderMenu = false; deletingFolder = child })
                                }
                            } else Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                        }, modifier = Modifier.clickable { folderId = child.id })
                }
            }
            if (ordered.isEmpty() && children.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.folder_empty), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Text(stringResource(R.string.folder_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    if (!selecting) TextButton(onClick = { newPreset = true }) { Text(stringResource(R.string.new_preset)) }
                }
            }
            items(ordered, key = { it.id }) { preset ->
                val isDragged = dragged == preset.id
                val isSelected = preset.id == ui.selectedPreset?.id
                val isSupported = vm.registry.templates.any { it.id == preset.templateId }
                val upDescription = stringResource(R.string.move_up)
                val downDescription = stringResource(R.string.move_down)
                Card(Modifier.fillMaxWidth().animateItem().zIndex(if (isDragged) 1f else 0f).graphicsLayer {
                    val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == preset.id }
                    translationY = if (isDragged && visible != null) center - visible.offset - visible.size / 2f else 0f
                }
                    .semantics { customActions = if (selecting) emptyList() else listOf(CustomAccessibilityAction(upDescription) { move(preset, -1); true }, CustomAccessibilityAction(downDescription) { move(preset, 1); true }) },
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)) {
                    Row(Modifier.fillMaxWidth().then(if (selecting) Modifier.selectable(selected = isSelected, enabled = isSupported, role = Role.RadioButton, onClick = { vm.select(preset); back() }) else Modifier)
                        .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val dragDescription = stringResource(R.string.drag_reorder)
                        if (!selecting) Icon(Icons.Default.DragHandle, dragDescription, Modifier.size(48.dp).padding(12.dp)
                            .pointerInput(preset.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragged = preset.id
                                        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == preset.id }
                                        center = (item?.offset ?: 0) + (item?.size ?: 0) / 2f
                                    },
                                    onDragEnd = { vm.reorderFolder(folderId, ordered.map { it.id }); dragged = null },
                                    onDragCancel = { dragged = null; ordered = visiblePresets },
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
                        if (selecting) Icon(Icons.Default.Description, null, Modifier.padding(horizontal = 12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f).then(if (!selecting) Modifier.clickable(enabled = isSupported) { vm.beginEditor(preset); edit() } else Modifier).padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(preset.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                if (preset.isPinned) Icon(Icons.Default.PushPin, stringResource(R.string.pin), Modifier.size(18.dp))
                            }
                            Text(stringResource(R.string.preset_template, templateName(vm, preset.templateId)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2)
                            Text(preset.fieldValues["project"].orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selecting || isSelected) Box(Modifier.padding(start = 8.dp).size(40.dp), contentAlignment = Alignment.Center) {
                            if (isSelected) Icon(Icons.Default.CheckCircle, if (selecting) null else stringResource(R.string.preset_selected_state), Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        if (!selecting) Box {
                            IconButton(onClick = { menu = preset.id }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
                            DropdownMenu(expanded = menu == preset.id, onDismissRequest = { menu = null }) {
                                DropdownMenuItem(enabled = vm.registry.templates.any { it.id == preset.templateId }, text = { Text(stringResource(R.string.edit_preset)) }, onClick = { menu = null; vm.beginEditor(preset); edit() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = null; renaming = preset; renameValue = preset.name })
                                DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = { menu = null; vm.duplicate(preset) })
                                DropdownMenuItem(text = { Text(stringResource(if (preset.isPinned) R.string.unpin else R.string.pin)) }, onClick = { menu = null; vm.pin(preset) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { menu = null; move(preset, -1) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { menu = null; move(preset, 1) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_to_folder)) }, onClick = { menu = null; moving = preset })
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, onClick = { menu = null; deleting = preset })
                            }
                        }
                    }
                }
            }
        }
    }
    if (folderForm) FolderNameDialog(editingFolder?.name.orEmpty(), editingFolder != null, ui.folders.filter { it.parentId == (editingFolder?.parentId ?: folderId) && it.id != editingFolder?.id }.map { it.name }, { folderForm = false }) { name ->
        val target = editingFolder
        if (target == null) vm.createFolder(name, folderId) else vm.renameFolder(target.id, name)
        folderForm = false
    }
    deletingFolder?.let { target -> AlertDialog(onDismissRequest = { deletingFolder = null }, title = { Text(stringResource(R.string.delete_folder_title)) }, text = { Text(stringResource(R.string.delete_folder_message, target.name)) },
        confirmButton = { TextButton(onClick = { vm.deleteFolder(target.id); deletingFolder = null }) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text(stringResource(R.string.cancel)) } }) }
    moving?.let { preset -> FolderDestinationDialog(ui.folders, preset.folderId, { moving = null }) { target -> vm.movePreset(preset.id, target); moving = null } }
    if (newPreset) AlertDialog(onDismissRequest = { newPreset = false }, title = { Text(stringResource(R.string.choose_new_template)) },
        text = { Column { vm.registry.templates.forEach { template ->
            ListItem(headlineContent = { Text(stringResource(template.nameRes)) }, leadingContent = { Icon(Icons.Default.Layers, null) }, modifier = Modifier.clickable {
                newPreset = false; vm.beginEditor(null, folderId, template.id); edit()
            })
        } } }, confirmButton = {}, dismissButton = { TextButton(onClick = { newPreset = false }) { Text(stringResource(R.string.cancel)) } })
    deleting?.let { preset -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_title)) }, text = { Text(stringResource(R.string.delete_message, preset.name)) },
        confirmButton = { TextButton(onClick = { vm.delete(preset); deleting = null }) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
    renaming?.let { preset -> AlertDialog(onDismissRequest = { renaming = null }, title = { Text(stringResource(R.string.rename)) },
        text = { OutlinedTextField(renameValue, { renameValue = it.take(80) }, label = { Text(stringResource(R.string.name)) }, singleLine = true) },
        confirmButton = { TextButton(enabled = renameValue.isNotBlank(), onClick = { vm.rename(preset.id, renameValue); renaming = null }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditorScreen(vm: CameraViewModel, back: () -> Unit) {
    val draft by vm.editor.collectAsStateWithLifecycle()
    var discard by remember { mutableStateOf(false) }
    var visibility by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    val timestamp = remember { System.currentTimeMillis() }
    fun leave() { if (!draft.saving) { if (draft.dirty) discard = true else back() } }
    BackHandler { leave() }
    val template = vm.registry.template(draft.templateId)
    val sampleAddress = stringResource(R.string.sample_address)
    val snapshot = vm.registry.snapshot(WatermarkPreset(draft.id ?: "draft", draft.templateId, draft.name, draft.values, draft.hidden), WatermarkContext(timestamp, LocationSnapshot(LocationStatus.SUCCESS, address = sampleAddress)), true)
    val content = template.fields.filter { it.editable && it.type != FieldType.ADDRESS }.sortedBy { it.order }
    val automatic = template.fields.filter { !it.editable || it.type == FieldType.ADDRESS }.sortedBy { it.order }
    PageScaffold(stringResource(if (draft.id == null) R.string.new_preset else R.string.edit_preset), ::leave, actions = {
        FilledTonalButton(onClick = vm::saveEditor, enabled = draft.name.isNotBlank() && !draft.saving, modifier = Modifier.padding(end = 12.dp)) {
            if (draft.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.save))
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorSectionTitle(stringResource(R.string.editor_preview), Icons.Default.Preview)
                    WatermarkSample(vm.renderer, snapshot, Modifier.fillMaxWidth().height(206.dp).clip(MaterialTheme.shapes.large))
                    Text(stringResource(R.string.editor_preview_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                EditorSectionTitle(stringResource(R.string.editor_identity), Icons.Default.FolderOpen)
                OutlinedCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(template.nameRes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.folder_label, uiFolderName(vm, draft.folderId)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(draft.name, vm::editorName, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.name)) }, singleLine = true, enabled = !draft.saving)
                    }
                }
            }
            if (content.isNotEmpty()) item {
                EditorSectionTitle(stringResource(R.string.editor_content), Icons.Default.Edit)
                OutlinedCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        content.forEach { field ->
                            if (field.id !in draft.hidden) EditorField(vm, draft.values[field.id].orEmpty(), field, !draft.saving)
                        }
                        if (content.all { it.id in draft.hidden }) Text(stringResource(R.string.editor_field_counts, 0, content.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                EditorSectionTitle(stringResource(R.string.editor_automatic), Icons.Default.Schedule)
                OutlinedCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.editor_auto_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        automatic.filter { it.id !in draft.hidden }.forEach { field ->
                            if (field.type == FieldType.ADDRESS) EditorField(vm, draft.values[field.id].orEmpty(), field, !draft.saving)
                            else Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(stringResource(field.labelRes), style = MaterialTheme.typography.bodyMedium)
                                    Text(stringResource(R.string.automatic_time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedCard(onClick = { if (!draft.saving) visibility = true }, Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(stringResource(R.string.editor_show_fields)) }, supportingContent = { Text(stringResource(R.string.editor_field_counts, template.fields.count { it.id !in draft.hidden }, template.fields.size)) }, leadingContent = { Icon(Icons.Default.Visibility, null) }, trailingContent = { Icon(Icons.Default.Tune, null) })
                }
                TextButton(onClick = { reset = true }, enabled = !draft.saving, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Default.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.reset)) }
            }
        }
    }
    if (visibility) ModalBottomSheet(onDismissRequest = { visibility = false }) {
        Text(stringResource(R.string.editor_visibility), Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
            items(template.fields.sortedBy { it.order }, key = { it.id }) { field ->
                ListItem(headlineContent = { Text(stringResource(field.labelRes)) }, trailingContent = { Switch(field.id !in draft.hidden, { vm.editorHidden(field.id, !it) }, enabled = field.hideable && !draft.saving) })
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text(stringResource(R.string.editor_reset_title)) }, text = { Text(stringResource(R.string.editor_reset_body)) }, confirmButton = { TextButton(onClick = { vm.resetEditor(); reset = false }) { Text(stringResource(R.string.reset)) } }, dismissButton = { TextButton(onClick = { reset = false }) { Text(stringResource(R.string.cancel)) } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.discard_title)) }, text = { Text(stringResource(R.string.discard_message)) },
        confirmButton = { TextButton(onClick = { discard = false; back() }) { Text(stringResource(R.string.discard)) } }, dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.continue_edit)) } })
}

@Composable
private fun EditorSectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun EditorField(vm: CameraViewModel, value: String, field: WatermarkField, enabled: Boolean) {
    val address = field.type == FieldType.ADDRESS
    OutlinedTextField(value, { vm.editorField(field.id, it) }, Modifier.fillMaxWidth(),
        label = { Text(stringResource(if (address) R.string.editor_manual_address else field.labelRes)) },
        placeholder = { Text(stringResource(if (address) R.string.editor_auto_address else R.string.editor_optional)) },
        enabled = enabled, minLines = 1, maxLines = if (field.id == "note") 4 else 2,
        keyboardOptions = KeyboardOptions(keyboardType = if (field.type == FieldType.NUMBER) KeyboardType.Decimal else KeyboardType.Text),
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { vm.editorField(field.id, "") }, enabled = enabled) { Icon(Icons.Default.Clear, stringResource(R.string.clear)) } })
}

@Composable
private fun templateName(vm: CameraViewModel, id: String): String = vm.registry.templates.firstOrNull { it.id == id }?.let { stringResource(it.nameRes) } ?: stringResource(R.string.unknown_template, id)

@Composable
private fun uiFolderName(vm: CameraViewModel, id: String?): String {
    val ui by vm.ui.collectAsStateWithLifecycle()
    return ui.folders.firstOrNull { it.id == id }?.name ?: stringResource(R.string.library_root)
}

/** Breadcrumbs use stable folder IDs so renames and process restoration remain safe. */
@Composable
private fun FolderBreadcrumb(folders: List<PresetFolder>, folderId: String?, open: (String?) -> Unit) {
    val path = mutableListOf<PresetFolder>()
    var current = folderId
    val visited = mutableSetOf<String>()
    while (current != null && visited.add(current)) {
        val parent = folders.firstOrNull { it.id == current } ?: break
        path.add(0, parent); current = parent.parentId
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { open(null) }) { Icon(Icons.Default.Home, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.library_root)) }
        path.forEach { item ->
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { open(item.id) }) { Text(item.name) }
        }
    }
}

@Composable
private fun FolderNameDialog(initial: String, renaming: Boolean, siblings: List<String>, dismiss: () -> Unit, save: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    val valid = value.isNotBlank() && siblings.none { it.equals(value.trim(), ignoreCase = true) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(if (renaming) R.string.rename else R.string.new_folder)) },
        text = { OutlinedTextField(value, { value = it.take(80) }, label = { Text(stringResource(R.string.folder_name)) }, singleLine = true,
            isError = value.isNotBlank() && !valid, supportingText = { if (value.isNotBlank() && !valid) Text(stringResource(R.string.folder_invalid)) }) },
        confirmButton = { TextButton(enabled = valid, onClick = { save(value.trim()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun FolderDestinationDialog(folders: List<PresetFolder>, sourceId: String?, dismiss: () -> Unit, move: (String?) -> Unit) {
    var current by rememberSaveable { mutableStateOf<String?>(null) }
    val folder = folders.firstOrNull { it.id == current }
    LaunchedEffect(folders) { if (current != null && folder == null) current = null }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.move_to_folder)) }, text = {
        Column {
            FolderBreadcrumb(folders, current) { current = it }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                if (current != null) item { ListItem(headlineContent = { Text(stringResource(R.string.parent_folder)) }, leadingContent = { Icon(Icons.Default.FolderOpen, null) }, modifier = Modifier.clickable { current = folder?.parentId }) }
                items(folders.filter { it.parentId == current }, key = { it.id }) { child ->
                    ListItem(headlineContent = { Text(child.name) }, leadingContent = { Icon(Icons.Default.Folder, null) }, modifier = Modifier.clickable { current = child.id })
                }
            }
        }
    }, confirmButton = { TextButton(enabled = current != sourceId, onClick = { move(current) }) { Text(stringResource(R.string.move_here)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}
