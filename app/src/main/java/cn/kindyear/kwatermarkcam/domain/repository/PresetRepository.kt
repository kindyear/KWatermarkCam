package cn.kindyear.kwatermarkcam.domain.repository

import cn.kindyear.kwatermarkcam.domain.model.WatermarkPreset
import kotlinx.coroutines.flow.Flow

/** UI consumes this interface; all persistence invariants live in the implementation. */
interface PresetRepository {
    val presets: Flow<List<WatermarkPreset>>
    suspend fun ensureDefaults()
    suspend fun saveDraft(id: String?, templateId: String, name: String, values: Map<String, String>, hidden: Set<String>): String
    suspend fun rename(id: String, name: String)
    suspend fun duplicate(id: String): String
    suspend fun delete(id: String): String
    suspend fun pin(id: String, pinned: Boolean)
    suspend fun reorder(templateId: String, ids: List<String>)
}
