package cn.kindyear.kwatermarkcam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.kindyear.kwatermarkcam.core.database.AppDatabase
import cn.kindyear.kwatermarkcam.core.storage.PhotoRepository
import cn.kindyear.kwatermarkcam.core.watermark.TemplateRegistry
import cn.kindyear.kwatermarkcam.core.watermark.WatermarkRenderer
import cn.kindyear.kwatermarkcam.data.repository.RoomPresetRepository
import cn.kindyear.kwatermarkcam.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TemplateStylesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = TemplateRegistry(context)
    private val renderer = WatermarkRenderer()

    @Test fun catalogKeepsExistingIdsAndDoesNotPrintTemplateNames() {
        assertEquals(5, registry.templates.size)
        assertEquals(5, registry.templates.map { it.id }.toSet().size)
        assertEquals(5, registry.templates.map { it.layout.style }.toSet().size)
        assertEquals("construction-default", registry.templates.first().id)
        registry.templates.forEach { template ->
            assertEquals(template.fields.size, template.fields.map { it.id }.toSet().size)
            val snapshot = registry.snapshot(WatermarkPreset("sample", template.id, "预设", registry.defaults(template.id)), WatermarkContext(0, LocationSnapshot()), true)
            assertTrue(snapshot.title.isEmpty())
            assertEquals(snapshot.rows.size, renderer.measure(1080, 1440, snapshot).blocks.size)
        }
    }

    @Test fun everyStyleBoundsLongChineseTextAndProducesDistinctVisuals() {
        val backgrounds = mutableSetOf<Int>()
        registry.templates.forEach { template ->
            val values = template.fields.associate { it.id to "中文现场记录".repeat(60) }
            val snapshot = registry.snapshot(WatermarkPreset("p", template.id, "长内容", values), WatermarkContext(0, LocationSnapshot()), true)
            listOf(1080 to 1440, 1440 to 1080, 1080 to 1920, 1920 to 1080, 3000 to 4000).forEach { (width, height) ->
                val measured = renderer.measure(width, height, snapshot)
                assertTrue(measured.bounds.left >= 0 && measured.bounds.top >= 0)
                assertTrue(measured.bounds.right <= width && measured.bounds.bottom <= height)
                measured.blocks.forEach { assertTrue(it.layout.lineCount <= 3); assertTrue(it.y + it.layout.height <= measured.bounds.height()) }
            }
            val bitmap = Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.MAGENTA)
                renderer.draw(Canvas(bitmap), 1080, 1440, snapshot)
                val b = renderer.measure(1080, 1440, snapshot).bounds
                backgrounds += bitmap.getPixel((b.left + 6).toInt(), b.centerY().toInt())
            } finally { bitmap.recycle() }
        }
        assertEquals(5, backgrounds.size)
    }

    @Test fun templateDefaultsAreLazyAndSelectionTargetsCorrectTemplate() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = RoomPresetRepository(db, registry, context)
            repo.ensureDefaults()
            assertEquals(1, repo.presets.first().size)
            val id = repo.ensureTemplate("attendance-clock")
            assertEquals(id, repo.ensureTemplate("attendance-clock"))
            assertEquals(2, repo.presets.first().size)
            assertEquals("attendance-clock", repo.presets.first().first { it.id == id }.templateId)
            assertTrue(runCatching { repo.ensureTemplate("missing") }.isFailure)
        } finally { db.close() }
    }

    @Test fun everyStyleSurvivesPendingCaptureRecovery() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val root = File(context.cacheDir, "styles-${UUID.randomUUID()}")
        val repo = PhotoRepository(context, db, renderer, root)
        try {
            registry.templates.forEach { template ->
                val snapshot = registry.snapshot(WatermarkPreset("preset", template.id, "记录", registry.defaults(template.id)), WatermarkContext(0, LocationSnapshot()), true)
                val pending = repo.create(snapshot)
                pending.original.writeBytes(byteArrayOf(1, 2, 3))
                val recovered = requireNotNull(repo.recover())
                assertEquals(snapshot, recovered.snapshot)
                repo.discard(recovered)
            }
        } finally { db.close(); root.deleteRecursively() }
    }
}
