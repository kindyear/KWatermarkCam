package cn.kindyear.kwatermarkcam

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.kindyear.kwatermarkcam.core.database.*
import cn.kindyear.kwatermarkcam.core.datastore.SettingsStore
import cn.kindyear.kwatermarkcam.core.watermark.TemplateRegistry
import cn.kindyear.kwatermarkcam.data.repository.RoomPresetRepository
import cn.kindyear.kwatermarkcam.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PresetDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var context: Context
    private lateinit var registry: TemplateRegistry
    private lateinit var repository: RoomPresetRepository
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        registry = TemplateRegistry(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = RoomPresetRepository(db, registry, context)
    }
    @After fun close() { db.close() }
    @Test fun templateDefaultsAndHiddenDynamicFields() {
        assertEquals(5, registry.templates.size)
        val template = registry.template("construction-default")
        assertEquals(6, template.fields.size)
        assertFalse(template.fields.first().editable)
        assertEquals(context.getString(R.string.default_project), registry.defaults(template.id)["project"])
        val preset = WatermarkPreset("p", template.id, "测试", registry.defaults(template.id), setOf("note"))
        val snapshot = registry.snapshot(preset, WatermarkContext(0, LocationSnapshot()), true)
        assertTrue(snapshot.rows.any { it.label == context.getString(R.string.field_time) })
        assertFalse(snapshot.rows.any { it.label == context.getString(R.string.field_note) })
    }
    @Test fun cachedLocationIsMarkedInPhotoButManualOverrideIsNot() {
        val preset = WatermarkPreset("p", "construction-default", "测试", registry.defaults("construction-default"))
        val context = WatermarkContext(1_791_441_000_000, LocationSnapshot(LocationStatus.CACHED, 39.0, 98.0, "缓存地址", 1_791_440_000_000))
        val locationLabel = this.context.getString(R.string.field_address)
        val automatic = registry.snapshot(preset, context, true).rows.first { it.label == locationLabel }.value
        assertTrue(automatic.contains("（缓存 "))
        val manual = preset.copy(fieldValues = preset.fieldValues + ("address" to "手动地点"))
        assertEquals("手动地点", registry.snapshot(manual, context, true).rows.first { it.label == locationLabel }.value)
    }
    @Test fun createEditRenameCopyPinSortAndDeleteLast() = runBlocking {
        repository.ensureDefaults()
        val default = repository.presets.first().single()
        val a = repository.saveDraft(null, default.templateId, "项目 A", mapOf("project" to "A工程"), emptySet())
        val b = repository.saveDraft(null, default.templateId, "项目 B", mapOf("project" to "B工程"), emptySet())
        repository.rename(a, "A改名")
        repository.saveDraft(a, default.templateId, "A改名", mapOf("project" to "新内容"), setOf("note"))
        val copy = repository.duplicate(a)
        var list = repository.presets.first()
        assertEquals("新内容", list.first { it.id == copy }.fieldValues["project"])
        assertTrue(list.first { it.id == copy }.hiddenFields.contains("note"))
        repository.pin(b, true); repository.pin(a, true)
        list = repository.presets.first()
        assertEquals(listOf(b, a), list.take(2).map { it.id })
        repository.reorderFolder(null, listOf(a, b, copy, default.id))
        list = repository.presets.first()
        assertEquals(listOf(a, b, copy, default.id), list.map { it.id })
        repository.pin(a, false)
        assertEquals(b, repository.presets.first().first().id)
        val replacement = repository.delete(b)
        assertTrue(repository.presets.first().any { it.id == replacement })
        repository.presets.first().forEach { repository.delete(it.id) }
        assertEquals(1, repository.presets.first().size)
    }
    @Test fun invalidReorderRollsBack() = runBlocking {
        repository.ensureDefaults()
        val original = repository.presets.first().single()
        val pinned = repository.saveDraft(null, original.templateId, "置顶", emptyMap(), emptySet())
        repository.pin(pinned, true)
        val result = runCatching { repository.reorderFolder(null, listOf(original.id, pinned)) }
        assertTrue(result.isFailure)
        assertEquals(pinned, repository.presets.first().first().id)
    }
    @Test fun selectionRepairsAfterDeletingCurrentAndPersists() = runBlocking {
        repository.ensureDefaults()
        val current = repository.presets.first().single()
        val store = SettingsStore(context)
        val previous = store.settings.first()
        try {
            store.select(current.templateId, current.id)
            val replacement = repository.delete(current.id)
            store.select(current.templateId, replacement)
            assertEquals(replacement, SettingsStore(context).settings.first().presetId)
            assertTrue(repository.presets.first().any { it.id == replacement })
        } finally { store.select(previous.templateId, previous.presetId) }
    }
    @Test fun diskDatabaseSurvivesReopenWithOrdering() = runBlocking {
        val file = "test-${UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, AppDatabase::class.java, file).build()
        try {
            var repo = RoomPresetRepository(disk, registry, context)
            repo.ensureDefaults()
            val initial = repo.presets.first().single()
            val added = repo.saveDraft(null, initial.templateId, "持久化工程", mapOf("project" to "中文长内容"), emptySet())
            repo.pin(added, true)
            disk.close()
            disk = Room.databaseBuilder(context, AppDatabase::class.java, file).build()
            repo = RoomPresetRepository(disk, registry, context)
            assertEquals(added, repo.presets.first().first().id)
            assertEquals("中文长内容", repo.presets.first().first().fieldValues["project"])
        } finally { disk.close(); context.deleteDatabase(file) }
    }
    @Test fun jsonRoundTripAndFutureSchemaProtection() {
        val values = mapOf("project" to "引号\"与换行\n中文", "future-field" to "保留")
        assertEquals(values to setOf("note"), PresetJson.decode(PresetJson.encode(values, setOf("note"))))
        assertTrue(runCatching { PresetJson.decode("{\"schemaVersion\":2,\"values\":{}}") }.isFailure)
    }
}
