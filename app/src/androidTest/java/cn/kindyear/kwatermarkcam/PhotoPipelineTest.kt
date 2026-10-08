package cn.kindyear.kwatermarkcam

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.kindyear.kwatermarkcam.core.database.AppDatabase
import cn.kindyear.kwatermarkcam.core.storage.PhotoRepository
import cn.kindyear.kwatermarkcam.core.watermark.WatermarkRenderer
import cn.kindyear.kwatermarkcam.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhotoPipelineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private val renderer = WatermarkRenderer()
    private lateinit var repository: PhotoRepository
    private val journalRoot = File(context.cacheDir, "photo-test-${UUID.randomUUID()}")
    private val uris = mutableListOf<Uri>()
    private val snapshot = WatermarkSnapshot("construction-default", "test", "工程项目水印", listOf(WatermarkRow("工程名称", "测试工程")), true, 1_791_441_025_000)
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build(); repository = PhotoRepository(context, db, renderer, journalRoot) }
    @After fun cleanup() { uris.forEach { context.contentResolver.delete(it, null, null) }; db.close(); journalRoot.deleteRecursively() }
    @Test fun longChineseTextStaysWithinCardAndPhoto() {
        val long = snapshot.copy(rows = (0..5).map { WatermarkRow("长字段", "超长中文工程记录".repeat(60)) })
        listOf(1080 to 1440, 1440 to 1080, 1080 to 1920, 1920 to 1080, 3000 to 4000).forEach { (w, h) ->
            val measured = renderer.measure(w, h, long)
            assertTrue(measured.bounds.left >= 0 && measured.bounds.top >= 0)
            assertTrue(measured.bounds.right <= w && measured.bounds.bottom <= h)
            measured.blocks.forEach { assertTrue(it.layout.lineCount <= 3); assertTrue(it.y + it.layout.height <= measured.bounds.height()) }
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            renderer.draw(Canvas(bitmap), w, h, long)
            assertTrue(Color.alpha(bitmap.getPixel(measured.bounds.centerX().toInt(), measured.bounds.centerY().toInt())) > 0)
            bitmap.recycle()
        }
    }
    @Test fun allExifOrientationsNormalizeAndGpsIsExcluded() = runBlocking {
        for (orientation in 1..8) {
            val pending = repository.create(snapshot.copy(visible = false))
            val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply { drawColor(Color.BLUE); drawRect(0f, 0f, 40f, 60f, Paint().apply { color = Color.RED }) }
            pending.original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bitmap.recycle()
            ExifInterface(pending.original).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); setLatLong(39.0, 98.0); saveAttributes() }
            val result = repository.save(pending)
            val uri = Uri.parse(result.record.uri); uris += uri
            assertTrue(result.recordWritten)
            assertEquals(if (orientation >= 5) 60 else 80, result.record.width)
            assertEquals(if (orientation >= 5) 80 else 60, result.record.height)
            context.contentResolver.openInputStream(uri)!!.use { input ->
                val exif = ExifInterface(input)
                assertEquals(ExifInterface.ORIENTATION_NORMAL, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
                assertNull(exif.latLong)
            }
            val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
            // Color split verifies reflection, not just dimensions.
            val redAtLeftOrTop = if (orientation >= 5) decoded.getPixel(30, 10) else decoded.getPixel(10, 30)
            assertEquals(orientation in listOf(1, 4, 5, 6), Color.red(redAtLeftOrTop) > Color.blue(redAtLeftOrTop))
            decoded.recycle()
            context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.IS_PENDING), null, null, null)!!.use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
            assertFalse(pending.directory.exists())
        }
    }
    @Test fun failureRetainsFrozenCaptureForRecovery() = runBlocking {
        val pending = repository.create(snapshot)
        pending.original.writeText("invalid JPEG")
        assertTrue(runCatching { repository.save(pending) }.isFailure)
        assertTrue(pending.original.exists())
        val recovered = repository.recover()!!
        assertEquals(snapshot, recovered.snapshot)
        repository.discard(recovered)
    }
    @Test fun publishedPhotoRecordFailureRetriesSameUri() = runBlocking {
        val pending = repository.create(snapshot.copy(visible = false))
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
        pending.original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bitmap.recycle()
        db.close()
        val first = repository.save(pending)
        uris += Uri.parse(first.record.uri)
        assertFalse(first.recordWritten)
        assertTrue(pending.journal.exists())
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = PhotoRepository(context, db, renderer, journalRoot)
        val recovered = repository.recover()!!
        val retried = repository.save(recovered)
        assertTrue(retried.recordWritten)
        assertEquals(first.record.uri, retried.record.uri)
        assertFalse(pending.directory.exists())
    }
    @Test fun hiddenWatermarkLeavesImageUnmarked() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(bitmap), 100, 100, snapshot.copy(visible = false))
        assertEquals(0, bitmap.getPixel(20, 80)); bitmap.recycle()
    }
}
