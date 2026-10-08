package cn.kindyear.kwatermarkcam.data.repository

import android.content.Context
import androidx.room.withTransaction
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.core.database.*
import cn.kindyear.kwatermarkcam.core.watermark.TemplateRegistry
import cn.kindyear.kwatermarkcam.domain.model.WatermarkPreset
import cn.kindyear.kwatermarkcam.domain.repository.PresetRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomPresetRepository @Inject constructor(
    private val db: AppDatabase, private val registry: TemplateRegistry,
    @param:ApplicationContext private val context: Context,
) : PresetRepository {
    private val dao get() = db.presets()
    override val presets = dao.observe().map { list -> list.map { it.model() } }
    override suspend fun ensureDefaults() = db.withTransaction {
        registry.templates.forEach { if (dao.list(it.id).isEmpty()) dao.insert(default(it.id)) }
    }
    override suspend fun saveDraft(id: String?, templateId: String, name: String, values: Map<String, String>, hidden: Set<String>): String = db.withTransaction {
        require(name.isNotBlank() && name.length <= 80)
        require(values.values.all { it.length <= 500 })
        val existing = id?.let { requireNotNull(dao.get(it)) }
        val now = System.currentTimeMillis()
        val key = existing?.id ?: UUID.randomUUID().toString()
        dao.run {
            val entity = WatermarkPresetEntity(key, existing?.templateId ?: templateId, name.trim(),
                PresetJson.encode(values, hidden), existing?.isPinned ?: false,
                existing?.sortOrder ?: (list(templateId).filterNot { it.isPinned }.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0),
                existing?.createdAt ?: now, now)
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
            sortOrder = (dao.list(original.templateId).filterNot { it.isPinned }.maxOfOrNull { it.sortOrder } ?: -1) + 1, createdAt = now, updatedAt = now))
        key
    }
    /** Deletion and default replacement are atomic. Selection can be repaired after a process crash. */
    override suspend fun delete(id: String): String = db.withTransaction {
        val original = requireNotNull(dao.get(id))
        dao.delete(id)
        var remaining = dao.list(original.templateId)
        if (remaining.isEmpty()) {
            val replacement = default(original.templateId)
            dao.insert(replacement); remaining = listOf(replacement)
        }
        remaining.first().id
    }
    override suspend fun pin(id: String, pinned: Boolean) = db.withTransaction {
        val original = requireNotNull(dao.get(id))
        val order = (dao.list(original.templateId).filter { it.isPinned == pinned }.maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.update(original.copy(isPinned = pinned, sortOrder = order, updatedAt = System.currentTimeMillis()))
    }
    override suspend fun reorder(templateId: String, ids: List<String>) = db.withTransaction {
        val all = dao.list(templateId)
        require(ids.size == ids.toSet().size && ids.toSet() == all.map { it.id }.toSet())
        val ordered = ids.map { key -> all.first { it.id == key } }
        // Pinned groups cannot cross: the UI and repository both enforce this invariant.
        require(ordered.dropWhile { it.isPinned }.none { it.isPinned })
        val counters = mutableMapOf(true to 0, false to 0)
        ordered.forEach { entity ->
            val order = counters.getValue(entity.isPinned)
            counters[entity.isPinned] = order + 1
            dao.update(entity.copy(sortOrder = order, updatedAt = System.currentTimeMillis()))
        }
    }
    private fun default(templateId: String): WatermarkPresetEntity {
        val now = System.currentTimeMillis()
        return WatermarkPresetEntity(UUID.randomUUID().toString(), templateId, context.getString(R.string.default_preset),
            PresetJson.encode(registry.defaults(templateId), emptySet()), false, 0, now, now)
    }
}
fun WatermarkPresetEntity.model(): WatermarkPreset {
    val (values, hidden) = PresetJson.decode(fieldsJson)
    return WatermarkPreset(id, templateId, name, values, hidden, isPinned, sortOrder, createdAt, updatedAt)
}
