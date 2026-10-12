package cn.kindyear.kwatermarkcam

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.kindyear.kwatermarkcam.core.database.*
import cn.kindyear.kwatermarkcam.core.datastore.SettingsStore
import cn.kindyear.kwatermarkcam.core.watermark.*
import cn.kindyear.kwatermarkcam.data.repository.RoomPresetRepository
import cn.kindyear.kwatermarkcam.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CustomFieldsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = TemplateRegistry(context)

    @Test fun customFieldsSurviveEditCopyAndDatabaseReopen() = runBlocking {
        val name = "custom-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            var repo = RoomPresetRepository(db, registry, context)
            val extra = listOf(CustomWatermarkField("custom:a", "天气", "晴"), CustomWatermarkField("custom:b", "验收编号", "A-001", false))
            val id = repo.saveDraft(null, "construction-default", "现场", registry.defaults("construction-default"), emptySet(), customFields = extra)
            repo.rename(id, "重命名")
            // Existing callers editing only built-in values must not silently lose custom fields.
            repo.saveDraft(id, "construction-default", "重命名", mapOf("project" to "新内容"), setOf("note"))
            assertEquals(extra, repo.presets.first().single().customFields)
            val copy = repo.duplicate(id)
            val changed = listOf(extra[1].copy(name = "质检编号", visible = true), extra[0].copy(value = "多云"))
            repo.saveDraft(copy, "construction-default", "副本", emptyMap(), emptySet(), customFields = changed)
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            repo = RoomPresetRepository(db, registry, context)
            assertEquals(extra, repo.presets.first().first { it.id == id }.customFields)
            assertEquals(changed, repo.presets.first().first { it.id == copy }.customFields)
            repo.delete(copy)
            assertEquals(extra, repo.presets.first().single().customFields)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun oldJsonLoadsAndInvalidCustomFieldEditRollsBack() = runBlocking {
        val legacy = """{"schemaVersion":1,"values":{"project":"旧工程"},"hidden":["note"]}"""
        assertTrue(PresetJson.customFields(legacy).isEmpty())
        assertEquals("旧工程", PresetJson.decode(legacy).first["project"])
        assertTrue(runCatching { PresetJson.decode("""{"schemaVersion":3,"values":{}}""") }.isFailure)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = RoomPresetRepository(db, registry, context)
            val id = repo.saveDraft(null, "travel-postcard", "旅行", emptyMap(), emptySet())
            val malformed = listOf(CustomWatermarkField("custom:a", ""))
            assertTrue(runCatching { repo.saveDraft(id, "travel-postcard", "不能覆盖", emptyMap(), emptySet(), customFields = malformed) }.isFailure)
            assertEquals("旅行", repo.presets.first().single().name)
            val tooMany = (0..CustomWatermarkField.MAX_COUNT).map { CustomWatermarkField("custom:$it", "字段 $it") }
            assertTrue(runCatching { repo.saveDraft(id, "travel-postcard", "不能覆盖", emptyMap(), emptySet(), customFields = tooMany) }.isFailure)
        } finally { db.close() }
    }

    @Test fun everyTemplateRendersCustomFieldsWithBoundedLayoutAndFrozenLocationMode() {
        val renderer = WatermarkRenderer()
        registry.templates.forEach { template ->
            val extras = (0 until CustomWatermarkField.MAX_COUNT).map { CustomWatermarkField("custom:$it", "字段$it", "中文现场记录".repeat(60), it != 0) }
            val preset = WatermarkPreset("p", template.id, "记录", template.fields.associate { it.id to "长内容".repeat(100) }, customFields = extras)
            val snapshot = registry.snapshot(preset, WatermarkContext(0, LocationSnapshot()), true)
            assertFalse(snapshot.rows.any { it.label == "字段0" })
            assertEquals(extras.drop(1).map { it.name }, snapshot.rows.takeLast(extras.size - 1).map { it.label })
            listOf(1080 to 1440, 1440 to 1080, 3000 to 4000).forEach { (w, h) ->
                val m = renderer.measure(w, h, snapshot)
                assertTrue(m.bounds.top >= 0 && m.bounds.bottom <= h)
                assertTrue(m.bounds.height() <= h * template.layout.maxHeightFraction)
                assertTrue(m.blocks.all { it.y + it.layout.height <= m.bounds.height() })
            }
        }
        val preset = WatermarkPreset("p", "construction-default", "工程", emptyMap())
        val captured = registry.snapshot(preset, WatermarkContext(0, LocationSnapshot(LocationStatus.SUCCESS, 39.12345, 98.54321, "附近名称"), locationDisplay = LocationDisplay.COORDINATES), true)
        val next = registry.snapshot(preset, WatermarkContext(0, LocationSnapshot(LocationStatus.SUCCESS, 39.12345, 98.54321, "附近名称")), true)
        assertTrue(captured.rows.any { it.value == "39.12345, 98.54321" })
        assertTrue(next.rows.any { it.value == "附近名称" })
    }

    @Test fun locationModePersistsAndConstructionNewFieldsKeepStableExistingIds() = runBlocking {
        val store = SettingsStore(context)
        val original = store.settings.first().locationDisplay
        try {
            store.text("location_display", LocationDisplay.COORDINATES.name)
            assertEquals(LocationDisplay.COORDINATES, SettingsStore(context).settings.first().locationDisplay)
            store.text("location_display", "future-mode")
            assertEquals(LocationDisplay.NEARBY_NAME, store.settings.first().locationDisplay)
        } finally { store.text("location_display", original.name) }
        val fields = registry.template("construction-default").fields
        assertEquals(9, fields.size)
        assertTrue(fields.map { it.id }.containsAll(listOf("company", "supervision_company", "design_company", "construction_company", "project", "address")))
        assertTrue(fields.filter { it.id in setOf("supervision_company", "design_company", "construction_company") }.all { it.defaultValue.isEmpty() })
    }
}
