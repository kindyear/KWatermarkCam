package cn.kindyear.kwatermarkcam.domain.repository

import cn.kindyear.kwatermarkcam.domain.model.WatermarkPreset
import cn.kindyear.kwatermarkcam.domain.model.PresetFolder
import kotlinx.coroutines.flow.Flow

/** UI consumes this interface; all persistence invariants live in the implementation. */
interface PresetRepository {
    val presets: Flow<List<WatermarkPreset>>
    val folders: Flow<List<PresetFolder>>
    /** Creates a preset lazily when a template is first used. */
    suspend fun ensureTemplate(templateId: String): String
    suspend fun ensureDefaults()
    suspend fun saveDraft(id: String?, templateId: String, name: String, values: Map<String, String>, hidden: Set<String>, folderId: String? = null): String
    suspend fun rename(id: String, name: String)
    suspend fun duplicate(id: String): String
    /** Deletes the item and returns a surviving ID; only an empty library gets a default. */
    suspend fun delete(id: String): String
    suspend fun pin(id: String, pinned: Boolean)
    suspend fun createFolder(name: String, parentId: String?): String
    suspend fun renameFolder(id: String, name: String)
    /** Delete only the container: direct presets and subfolders are moved to its parent. */
    suspend fun deleteFolder(id: String)
    suspend fun move(id: String, folderId: String?)
    suspend fun reorderFolder(folderId: String?, ids: List<String>)
}
