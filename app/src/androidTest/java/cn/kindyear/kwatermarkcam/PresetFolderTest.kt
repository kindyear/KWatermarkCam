package cn.kindyear.kwatermarkcam

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.kindyear.kwatermarkcam.core.database.*
import cn.kindyear.kwatermarkcam.core.watermark.TemplateRegistry
import cn.kindyear.kwatermarkcam.data.repository.RoomPresetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PresetFolderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: RoomPresetRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = RoomPresetRepository(db, TemplateRegistry(context), context)
    }
    @After fun close() { db.close() }

    @Test fun nestedFoldersMoveCopyRenameAndSafeDelete() = runBlocking {
        repo.ensureDefaults()
        val a = repo.createFolder("项目A", null)
        val b = repo.createFolder("项目B", null)
        val child = repo.createFolder("现场", a)
        repo.createFolder("现场", null)
        val id = repo.saveDraft(null, "construction-default", "记录", mapOf("project" to "原始内容"), emptySet(), a)
        val copy = repo.duplicate(id)
        assertEquals(a, repo.presets.first().first { it.id == copy }.folderId)
        repo.renameFolder(a, "改名")
        assertEquals("改名", repo.folders.first().first { it.id == a }.name)
        assertTrue(runCatching { repo.createFolder("改名", null) }.isFailure)
        assertTrue(runCatching { repo.move(id, "missing") }.isFailure)
        assertEquals(a, repo.presets.first().first { it.id == id }.folderId)
        repo.pin(id = id, pinned = true)
        repo.move(id, b)
        assertTrue(repo.presets.first().first { it.id == id }.isPinned)
        repo.deleteFolder(a)
        val remaining = repo.presets.first()
        assertEquals(b, remaining.first { it.id == id }.folderId)
        assertNull(remaining.first { it.id == copy }.folderId)
        assertEquals("原始内容", remaining.first { it.id == copy }.fieldValues["project"])
        val rehomed = repo.folders.first().first { it.id == child }
        assertNull(rehomed.parentId)
        assertEquals("现场 (2)", rehomed.name)
        assertFalse(repo.folders.first().any { it.id == a })
    }

    @Test fun folderOrderingIncludesDifferentTemplatesAndRejectsOtherFolders() = runBlocking {
        val folder = repo.createFolder("多模板", null)
        val a = repo.saveDraft(null, "construction-default", "工程", emptyMap(), emptySet(), folder)
        // Unknown future template data must remain visible and manageable in the repository.
        db.presets().insert(WatermarkPresetEntity("future", "future-template", "其他模板", PresetJson.encode(emptyMap(), emptySet()), false, 1, 1, 1, folder))
        repo.reorderFolder(folder, listOf("future", a))
        assertEquals(listOf("future", a), repo.presets.first().filter { it.folderId == folder }.map { it.id })
        repo.ensureDefaults()
        val root = repo.saveDraft(null, "construction-default", "根目录预设", emptyMap(), emptySet())
        assertTrue(runCatching { repo.reorderFolder(folder, listOf(root, a)) }.isFailure)
        repo.pin(a, true)
        assertTrue(runCatching { repo.reorderFolder(folder, listOf("future", a)) }.isFailure)
        assertEquals(a, repo.presets.first().filter { it.folderId == folder }.first().id)
        repo.delete("future")
        assertFalse(repo.presets.first().any { it.templateId == "future-template" })
    }

    @Test fun foldersAndMembershipSurviveDatabaseReopen() = runBlocking {
        val name = "folders-${UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            var repository = RoomPresetRepository(disk, TemplateRegistry(context), context)
            val parent = repository.createFolder("持久化", null)
            val child = repository.createFolder("子目录", parent)
            val id = repository.saveDraft(null, "construction-default", "照片内容", mapOf("note" to "保留"), emptySet(), child)
            disk.close()
            disk = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            repository = RoomPresetRepository(disk, TemplateRegistry(context), context)
            assertEquals(parent, repository.folders.first().first { it.id == child }.parentId)
            assertEquals(child, repository.presets.first().single().folderId)
            assertEquals(id, repository.presets.first().single().id)
        } finally { disk.close(); context.deleteDatabase(name) }
    }

    @Test fun versionOneMigratesWithoutLosingPresetsOrPhotos() = runBlocking {
        val name = "migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val source = InstrumentationRegistry.getInstrumentation().context.assets
            .open("cn.kindyear.kwatermarkcam.core.database.AppDatabase/1.json").bufferedReader().use { it.readText() }
        val schema = JSONObject(source).getJSONObject("database")
        val fields = PresetJson.encode(mapOf("project" to "升级前工程"), setOf("note"))
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO presets VALUES (?, ?, ?, ?, ?, ?, ?, ?)", arrayOf("old-id", "construction-default", "保留名称", fields, 1, 7, 100L, 200L))
            old.execSQL("INSERT INTO photos VALUES (?, ?, ?, ?, ?, ?, ?)", arrayOf("photo-id", "content://test/1", 300L, "construction-default", "old-id", 3016, 4022))
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            val preset = requireNotNull(migrated.presets().get("old-id"))
            assertNull(preset.folderId)
            assertEquals(fields, preset.fieldsJson)
            assertEquals("保留名称", preset.name)
            assertTrue(preset.isPinned)
            assertEquals(7, preset.sortOrder)
            assertEquals(100L, preset.createdAt)
            assertEquals(200L, preset.updatedAt)
            assertEquals("photo-id", migrated.photos().observe().first().single().id)
            assertTrue(migrated.folders().observe().first().isEmpty())
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
