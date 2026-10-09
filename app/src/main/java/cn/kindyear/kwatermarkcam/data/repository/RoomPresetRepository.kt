package cn.kindyear.kwatermarkcam.data.repository

import android.content.Context
import androidx.room.withTransaction
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.core.database.*
import cn.kindyear.kwatermarkcam.core.watermark.TemplateRegistry
import cn.kindyear.kwatermarkcam.domain.model.WatermarkPreset
import cn.kindyear.kwatermarkcam.domain.model.PresetFolder
import cn.kindyear.kwatermarkcam.domain.repository.PresetRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomPresetRepository @Inject constructor(
    private val db: AppDatabase, private val registry: TemplateRegistry,
    @param:ApplicationContext private val context: Context,
) : PresetRepository {
    private val dao get() = db.presets()
    override val folders = db.folders().observe().map { list -> list.map { PresetFolder(it.id, it.name, it.parentId, it.createdAt) } }
    override val presets = dao.observe().map { list -> list.map { it.model() } }
    override suspend fun ensureDefaults() = db.withTransaction {
        val first = registry.templates.first()
        if (dao.observe().first().isEmpty()) dao.insert(default(first.id))
    }
    override suspend fun ensureTemplate(templateId: String): String = db.withTransaction {
        require(registry.templates.any { it.id == templateId })
        dao.list(templateId).firstOrNull()?.id ?: default(templateId).also { dao.insert(it) }.id
    }
    override suspend fun saveDraft(id: String?, templateId: String, name: String, values: Map<String, String>, hidden: Set<String>, folderId: String?): String = db.withTransaction {
        validateFolder(folderId)
        require(name.isNotBlank() && name.length <= 80)
        require(values.values.all { it.length <= 500 })
        val existing = id?.let { requireNotNull(dao.get(it)) }
        val now = System.currentTimeMillis()
        val key = existing?.id ?: UUID.randomUUID().toString()
        dao.run {
            val entity = WatermarkPresetEntity(key, existing?.templateId ?: templateId, name.trim(),
                PresetJson.encode(values, hidden), existing?.isPinned ?: false,
                existing?.sortOrder ?: (inFolder(folderId).filterNot { it.isPinned }.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0),
                existing?.createdAt ?: now, now, existing?.folderId ?: folderId)
            if (existing == null) insert(entity) else update(entity)
        }
        key
    }
    override suspend fun rename(id: String, name: String) = db.withTransaction {
        require(name.isNotBlank() && name.length <= 80)
        dao.update(requireNotNull(dao.get(id)).copy(name = name.trim(), updatedAt = System.currentTimeMillis()))
    }
    override suspend fun duplicate(id: String): String = db.withTransaction {
        val original = requireNotNull(dao.get(id))
        val key = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.insert(original.copy(id = key, name = context.getString(R.string.copy_name, original.name.take(70)), isPinned = false,
            sortOrder = (dao.inFolder(original.folderId).filterNot { it.isPinned }.maxOfOrNull { it.sortOrder } ?: -1) + 1, createdAt = now, updatedAt = now))
        key
    }
    /** Deletion and default replacement are atomic. Selection can be repaired after a process crash. */
    override suspend fun delete(id: String): String = db.withTransaction {
        val original = requireNotNull(dao.get(id))
        dao.delete(id)
        // Prefer another preset of the same template, then any remaining library item.
        // A template with no presets is valid: choosing it again lazily creates a new preset.
        var remaining = dao.list(original.templateId).ifEmpty { dao.observe().first() }
        if (remaining.isEmpty()) {
            val replacement = default(registry.templates.first().id)
            dao.insert(replacement)
            remaining = listOf(replacement)
        }
        remaining.first().id
    }
    override suspend fun pin(id: String, pinned: Boolean) = db.withTransaction {
        val original = requireNotNull(dao.get(id))
        val order = (dao.inFolder(original.folderId).filter { it.isPinned == pinned }.maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.update(original.copy(isPinned = pinned, sortOrder = order, updatedAt = System.currentTimeMillis()))
    }
    private suspend fun validateFolder(id: String?) {
        if (id != null) requireNotNull(db.folders().get(id))
    }
    override suspend fun createFolder(name: String, parentId: String?): String = db.withTransaction {
        validateFolder(parentId)
        require(name.isNotBlank() && name.length <= 80)
        require(db.folders().children(parentId).none { it.name.equals(name.trim(), ignoreCase = true) })
        val id = UUID.randomUUID().toString()
        db.folders().insert(PresetFolderEntity(id, name.trim(), parentId, System.currentTimeMillis()))
        id
    }
    override suspend fun renameFolder(id: String, name: String) = db.withTransaction {
        val folder = requireNotNull(db.folders().get(id))
        require(name.isNotBlank() && name.length <= 80)
        require(db.folders().children(folder.parentId).none { it.id != id && it.name.equals(name.trim(), ignoreCase = true) })
        db.folders().update(folder.copy(name = name.trim()))
    }
    override suspend fun deleteFolder(id: String) = db.withTransaction {
        val folder = requireNotNull(db.folders().get(id))
        dao.inFolder(id).forEach { move(it.id, folder.parentId) }
        // Rehome subfolders too. Resolve sibling name collisions without losing any content.
        val names = db.folders().children(folder.parentId).filter { it.id != id }.map { it.name.lowercase(java.util.Locale.ROOT) }.toMutableSet()
        db.folders().children(id).forEach { child ->
            var name = child.name
            var suffix = 2
            while (name.lowercase(java.util.Locale.ROOT) in names) { name = child.name.take(70) + " ($suffix)"; suffix++ }
            names += name.lowercase(java.util.Locale.ROOT)
            db.folders().update(child.copy(parentId = folder.parentId, name = name))
        }
        db.folders().delete(id)
    }
    override suspend fun move(id: String, folderId: String?) = db.withTransaction {
        validateFolder(folderId)
        val original = requireNotNull(dao.get(id))
        if (original.folderId != folderId) {
            val order = (dao.inFolder(folderId).filter { it.isPinned == original.isPinned }.maxOfOrNull { it.sortOrder } ?: -1) + 1
            dao.update(original.copy(folderId = folderId, sortOrder = order, updatedAt = System.currentTimeMillis()))
        }
    }
    override suspend fun reorderFolder(folderId: String?, ids: List<String>) = db.withTransaction {
        validateFolder(folderId)
        val all = dao.inFolder(folderId)
        require(ids.size == ids.toSet().size && ids.toSet() == all.map { it.id }.toSet())
        val ordered = ids.map { id -> all.first { it.id == id } }
        require(ordered.dropWhile { it.isPinned }.none { it.isPinned })
        val counters = mutableMapOf(true to 0, false to 0)
        ordered.forEach { entity ->
            val index = counters.getValue(entity.isPinned)
            counters[entity.isPinned] = index + 1
            dao.update(entity.copy(sortOrder = index, updatedAt = System.currentTimeMillis()))
        }
    }
    private fun default(templateId: String): WatermarkPresetEntity {
        val now = System.currentTimeMillis()
        return WatermarkPresetEntity(UUID.randomUUID().toString(), templateId, if (templateId == "construction-default") context.getString(R.string.default_preset) else context.getString(R.string.default_template_preset, context.getString(registry.template(templateId).nameRes)),
            PresetJson.encode(registry.defaults(templateId), emptySet()), false, 0, now, now)
    }
}
fun WatermarkPresetEntity.model(): WatermarkPreset {
    val (values, hidden) = PresetJson.decode(fieldsJson)
    return WatermarkPreset(id, templateId, name, values, hidden, isPinned, sortOrder, createdAt, updatedAt, folderId)
}
