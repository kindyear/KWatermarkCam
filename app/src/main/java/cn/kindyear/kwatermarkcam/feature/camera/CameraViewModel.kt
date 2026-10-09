package cn.kindyear.kwatermarkcam.feature.camera

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.core.camera.CameraController
import cn.kindyear.kwatermarkcam.core.datastore.SettingsStore
import cn.kindyear.kwatermarkcam.core.location.LocationSource
import cn.kindyear.kwatermarkcam.core.storage.*
import cn.kindyear.kwatermarkcam.core.watermark.*
import cn.kindyear.kwatermarkcam.domain.model.*
import cn.kindyear.kwatermarkcam.domain.repository.PresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/** Camera page data is derived from repositories plus a small immutable session state. */
data class CameraUiState(
    val settings: AppSettings = AppSettings(), val presets: List<WatermarkPreset> = emptyList(),
    val selectedPreset: WatermarkPreset? = null, val photos: List<PhotoRecord> = emptyList(),
    val loading: Boolean = true, val dataError: Boolean = false,
    val folders: List<PresetFolder> = emptyList(),
)
data class CaptureState(val capturing: Boolean = false, val saving: Boolean = false, val pending: PendingPhoto? = null, val recovering: Boolean = true, val lastPhoto: PhotoRecord? = null) {
    val busy get() = capturing || saving || recovering
}
data class EditorUiState(
    val id: String? = null, val templateId: String = "construction-default", val name: String = "",
    val values: Map<String, String> = emptyMap(), val hidden: Set<String> = emptySet(),
    val saving: Boolean = false, val originalName: String = "", val originalValues: Map<String, String> = values,
    val originalHidden: Set<String> = hidden, val folderId: String? = null,
) {
    val dirty get() = name != originalName || values != originalValues || hidden != originalHidden
}
sealed interface UiEvent {
    data class Message(val resource: Int, val retrySave: Boolean = false) : UiEvent
    data object EditorSaved : UiEvent
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    val camera: CameraController, val registry: TemplateRegistry, val renderer: WatermarkRenderer,
    private val presets: PresetRepository, private val settings: SettingsStore,
    val photoRepository: PhotoRepository, private val location: LocationSource,
) : ViewModel() {
    private val mutableUi = MutableStateFlow(CameraUiState())
    val ui = mutableUi.asStateFlow()
    private val mutableCapture = MutableStateFlow(CaptureState())
    val capture = mutableCapture.asStateFlow()
    private val mutableEditor = MutableStateFlow(EditorUiState())
    val editor = mutableEditor.asStateFlow()
    private val mutableLocation = MutableStateFlow(LocationSnapshot())
    val currentLocation = mutableLocation.asStateFlow()
    private val time = MutableStateFlow(System.currentTimeMillis())
    val watermark: StateFlow<WatermarkSnapshot?> = combine(ui, time, mutableLocation) { state, now, position ->
        state.selectedPreset?.let { registry.snapshot(it, WatermarkContext(now, position), state.settings.watermark) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var dataJob: Job? = null
    private var locationJob: Job? = null
    private var clockJob: Job? = null
    private var active = false

    init { loadData(); recover() }
    fun loadData() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            try {
                presets.ensureDefaults()
                combine(settings.settings, presets.presets, photoRepository.photos, presets.folders) { config, all, photos, folders ->
                    val template = registry.template(config.templateId)
                    val available = all.filter { it.templateId == template.id }
                    val selected = all.firstOrNull { it.id == config.presetId } ?: available.firstOrNull() ?: all.firstOrNull()
                    CameraUiState(config, all, selected, photos, loading = false, folders = folders)
                }.collect { value ->
                    val locationChanged = mutableUi.value.settings.location != value.settings.location
                    mutableUi.value = value
                    val selected = value.selectedPreset
                    if (selected != null && (selected.id != value.settings.presetId || selected.templateId != value.settings.templateId)) settings.select(selected.templateId, selected.id)
                    if (locationChanged && active) refreshLocation()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("CameraViewModel", "Data subscription failed", e)
                mutableUi.value = mutableUi.value.copy(loading = false, dataError = true)
                eventChannel.send(UiEvent.Message(R.string.data_failed))
            }
        }
    }
    fun foreground(value: Boolean) {
        active = value
        clockJob?.cancel()
        if (value) {
            clockJob = viewModelScope.launch { while (isActive) { time.value = System.currentTimeMillis(); delay(1_000) } }
            refreshLocation()
        } else { locationJob?.cancel(); locationJob = null }
    }
    fun refreshLocation() {
        locationJob?.cancel()
        if (!active) return
        if (!mutableUi.value.settings.location) { mutableLocation.value = LocationSnapshot(); return }
        locationJob = viewModelScope.launch { location.observe().collect { mutableLocation.value = it } }
    }
    fun bool(key: String, value: Boolean) = operation { settings.boolean(key, value) }
    fun text(key: String, value: String) = operation { settings.text(key, value) }
    fun select(preset: WatermarkPreset) = operation { settings.select(preset.templateId, preset.id) }
    fun template(id: String) = operation { settings.select(id, presets.ensureTemplate(id)) }
    fun pin(preset: WatermarkPreset) = operation { presets.pin(preset.id, !preset.isPinned) }
    fun duplicate(preset: WatermarkPreset) = operation { presets.duplicate(preset.id) }
    fun rename(id: String, value: String) = operation { presets.rename(id, value) }
    // The subscription repairs selection from the committed library snapshot. Reading
    // selectedPreset after deletion races Room's Flow and can miss the deleted selection.
    fun delete(preset: WatermarkPreset) = operation { presets.delete(preset.id) }
    fun reorderFolder(folderId: String?, ids: List<String>) = operation { presets.reorderFolder(folderId, ids) }
    fun createFolder(name: String, parentId: String?) = folderOperation { presets.createFolder(name, parentId) }
    fun renameFolder(id: String, name: String) = folderOperation { presets.renameFolder(id, name) }
    fun deleteFolder(id: String) = operation { presets.deleteFolder(id) }
    fun movePreset(id: String, folderId: String?) = operation { presets.move(id, folderId) }
    private fun folderOperation(block: suspend () -> Unit) {
        operation { try { block() } catch (e: IllegalArgumentException) {
            Log.w("CameraViewModel", "Invalid folder operation", e)
            eventChannel.send(UiEvent.Message(R.string.folder_invalid))
        } }
    }
    private fun operation(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e("CameraViewModel", "Operation failed", e); eventChannel.send(UiEvent.Message(R.string.data_failed)) }
        }
    }
    fun beginEditor(preset: WatermarkPreset?, folderId: String? = null, newTemplateId: String? = null) {
        val templateId = preset?.templateId ?: newTemplateId ?: mutableUi.value.settings.templateId
        val values = preset?.fieldValues?.toMap() ?: registry.defaults(templateId)
        val name = preset?.name.orEmpty()
        val hidden = preset?.hiddenFields?.toSet() ?: emptySet()
        mutableEditor.value = EditorUiState(preset?.id, templateId, name, values, hidden, originalName = name, originalValues = values, originalHidden = hidden, folderId = preset?.folderId ?: folderId)
    }
    fun editorName(value: String) { mutableEditor.update { it.copy(name = value.take(80)) } }
    fun editorField(id: String, value: String) { mutableEditor.update { it.copy(values = it.values + (id to value.take(500))) } }
    fun editorHidden(id: String, hidden: Boolean) { mutableEditor.update { it.copy(hidden = if (hidden) it.hidden + id else it.hidden - id) } }
    fun resetEditor() { mutableEditor.update { it.copy(values = registry.defaults(it.templateId), hidden = emptySet()) } }
    fun saveEditor() {
        val draft = mutableEditor.value
        if (draft.saving || draft.name.isBlank()) return
        mutableEditor.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val id = presets.saveDraft(draft.id, draft.templateId, draft.name, draft.values, draft.hidden, draft.folderId)
                settings.select(draft.templateId, id)
                mutableEditor.update { it.copy(id = id, saving = false, originalName = draft.name, originalValues = draft.values, originalHidden = draft.hidden) }
                eventChannel.send(UiEvent.EditorSaved)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e("CameraViewModel", "Draft save failed", e); mutableEditor.update { it.copy(saving = false) }; eventChannel.send(UiEvent.Message(R.string.data_failed)) }
        }
    }
    private fun recover() {
        viewModelScope.launch {
            try { mutableCapture.update { it.copy(pending = photoRepository.recover(), recovering = false) } }
            catch (e: Exception) { Log.e("CameraViewModel", "Capture recovery failed", e); mutableCapture.update { it.copy(recovering = false) }; eventChannel.send(UiEvent.Message(R.string.capture_recovery_failed)) }
        }
    }
    /** Synchronous busy guard freezes a new snapshot at the exact shutter action. */
    fun shoot() {
        val state = mutableCapture.value
        val selected = mutableUi.value.selectedPreset ?: return
        if (state.busy || state.pending != null || !camera.state.value.ready) return
        val frozen = registry.snapshot(selected, WatermarkContext(System.currentTimeMillis(), mutableLocation.value), mutableUi.value.settings.watermark)
        mutableCapture.value = state.copy(capturing = true)
        viewModelScope.launch {
            var pending: PendingPhoto? = null
            try {
                pending = photoRepository.create(frozen)
                camera.takePhoto(pending.original)
                mutableCapture.update { it.copy(capturing = false, saving = true, pending = pending) }
                savePending(pending)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("CameraViewModel", "Capture failed", e)
                if (pending != null && pending.original.length() > 0) mutableCapture.update { it.copy(pending = pending) }
                else if (pending != null) {
                    try { photoRepository.discard(pending) } catch (cleanup: Exception) { Log.e("CameraViewModel", "Failed to discard incomplete capture", cleanup) }
                }
                eventChannel.send(UiEvent.Message(R.string.capture_failed))
            } finally { mutableCapture.update { it.copy(capturing = false, saving = false) } }
        }
    }
    fun retrySave() {
        val state = mutableCapture.value
        val pending = state.pending ?: return
        if (state.busy) return
        mutableCapture.update { it.copy(saving = true) }
        viewModelScope.launch { try { savePending(pending) } finally { mutableCapture.update { it.copy(saving = false) } } }
    }
    private suspend fun savePending(pending: PendingPhoto) {
        try {
            val result = photoRepository.save(pending)
            mutableCapture.update { it.copy(pending = if (result.recordWritten) null else pending, lastPhoto = result.record) }
            eventChannel.send(UiEvent.Message(if (result.recordWritten) R.string.saved else R.string.save_record_warning))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.e("CameraViewModel", "Save failed; retained capture", e)
            eventChannel.send(UiEvent.Message(if (e is PhotoMemoryException) R.string.processing_memory else R.string.save_failed, retrySave = true))
        }
    }
    fun discardPending() {
        val pending = mutableCapture.value.pending ?: return
        if (mutableCapture.value.busy) return
        mutableCapture.update { it.copy(saving = true) }
        viewModelScope.launch {
            try { photoRepository.discard(pending); mutableCapture.update { it.copy(pending = photoRepository.recover()) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e("CameraViewModel", "Discard failed", e); eventChannel.send(UiEvent.Message(R.string.data_failed)) }
            finally { mutableCapture.update { it.copy(saving = false) } }
        }
    }
    override fun onCleared() { camera.unbind() }
}
