package cn.kindyear.kwatermarkcam

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.kindyear.kwatermarkcam.core.database.AppDatabase
import cn.kindyear.kwatermarkcam.core.watermark.TemplateRegistry
import cn.kindyear.kwatermarkcam.data.repository.RoomPresetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Regression: the file library must not resurrect the last preset of each template. */
@RunWith(AndroidJUnit4::class)
class PresetDeletionTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RoomPresetRepository
    @Before fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = RoomPresetRepository(db, TemplateRegistry(context), context)
    }
    @After fun close() { db.close() }

    @Test fun deletingLastOfOneTemplateKeepsOtherTemplatesWithoutRecreatingIt() = runBlocking {
        repo.ensureDefaults()
        val engineering = repo.presets.first().single()
        val travel = repo.ensureTemplate("travel-postcard")
        assertEquals(engineering.id, repo.delete(travel))
        assertEquals(listOf(engineering.id), repo.presets.first().map { it.id })
        repo.ensureDefaults()
        assertEquals(listOf(engineering.id), repo.presets.first().map { it.id })
    }

    @Test fun startupDoesNotRecreateDeletedEngineeringWhenAnotherTemplateExists() = runBlocking {
        repo.ensureDefaults()
        val engineering = repo.presets.first().single()
        val travel = repo.ensureTemplate("travel-postcard")
        assertEquals(travel, repo.delete(engineering.id))
        repo.ensureDefaults()
        assertEquals(listOf(travel), repo.presets.first().map { it.id })
    }

    @Test fun deletingWholeLibraryCreatesExactlyOneUsableDefault() = runBlocking {
        repo.ensureDefaults()
        repo.ensureTemplate("travel-postcard")
        repo.ensureTemplate("attendance-clock")
        val ids = repo.presets.first().map { it.id }
        ids.forEach { repo.delete(it) }
        val remaining = repo.presets.first().single()
        assertFalse(remaining.id in ids)
        assertEquals("construction-default", remaining.templateId)
        repo.ensureDefaults()
        assertEquals(remaining.id, repo.presets.first().single().id)
    }

    @Test fun sameTemplateReplacementIsPreferredAcrossFoldersAndDeleteDoesNotTouchContent() = runBlocking {
        repo.ensureDefaults()
        val original = repo.presets.first().single()
        val folder = repo.createFolder("临时目录", null)
        val next = repo.saveDraft(null, original.templateId, "保留", mapOf("project" to "真实内容"), setOf("note"), folder)
        repo.ensureTemplate("travel-postcard")
        assertEquals(next, repo.delete(original.id))
        val preserved = repo.presets.first().first { it.id == next }
        assertEquals(folder, preserved.folderId)
        assertEquals("真实内容", preserved.fieldValues["project"])
        assertEquals(setOf("note"), preserved.hiddenFields)
    }
}
