package cn.kindyear.kwatermarkcam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.kindyear.kwatermarkcam.core.database.AppDatabase
import cn.kindyear.kwatermarkcam.core.storage.*
import cn.kindyear.kwatermarkcam.core.watermark.WatermarkRenderer
import cn.kindyear.kwatermarkcam.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhotoEditingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun image(): Bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this); val paint = Paint()
        listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW).forEachIndexed { i, color ->
            paint.color = color
            canvas.drawRect((i % 2 * 300).toFloat(), (i / 2 * 200).toFloat(), (i % 2 * 300 + 300).toFloat(), (i / 2 * 200 + 200).toFloat(), paint)
        }
    }
    @Test fun rotationMirroringCropAndMonochromeUseOneGeometry() {
        val source = image()
        try {
            val rotated = PhotoEditEngine.render(source, PhotoEdits(quarterTurns = 1))
            try { assertEquals(400, rotated.width); assertEquals(600, rotated.height); assertEquals(Color.BLUE, rotated.getPixel(20,20)) } finally { rotated.recycle() }
            val mirrored = PhotoEditEngine.render(source, PhotoEdits(mirror = true))
            try { assertEquals(Color.GREEN, mirrored.getPixel(20,20)) } finally { mirrored.recycle() }
            val crop = PhotoEditEngine.render(source, PhotoEdits(cropRatio = 1f, monochrome = true, brightness = .1f))
            try { assertEquals(400, crop.width); assertEquals(400, crop.height); val pixel=crop.getPixel(20,20); assertEquals(Color.red(pixel), Color.green(pixel)); assertEquals(Color.green(pixel), Color.blue(pixel)) } finally { crop.recycle() }
            assertEquals(Color.RED, source.getPixel(20,20))
        } finally { source.recycle() }
    }
    @Test fun editedCopyIsRecoverableAndLeavesOriginalUriAndBytesUnchanged() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val root = File(context.cacheDir, "editing-${UUID.randomUUID()}")
        val repo = PhotoRepository(context, db, WatermarkRenderer(), root)
        val uris = mutableListOf<String>()
        fun digest(uri: String) = context.contentResolver.openInputStream(Uri.parse(uri))!!.use { MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
        try {
            val pending = repo.create(WatermarkSnapshot("daily-moment", "test", "", emptyList(), false, 123456789L, WatermarkLayoutSpec()))
            image().let { b -> try { pending.original.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG,95,it) } } finally { b.recycle() } }
            ExifInterface(pending.original).apply { setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:13 10:00:00"); saveAttributes() }
            val original = repo.save(pending).record; uris += original.uri
            val before = digest(original.uri)
            val edit = repo.prepareEdit(original, PhotoEdits(1, cropRatio=1f, monochrome=true))
            assertTrue(edit.rendered.exists())
            val recovered = requireNotNull(repo.recover()); assertEquals(edit.id, recovered.id); assertFalse(recovered.snapshot.visible)
            val copy = repo.save(recovered).record; uris += copy.uri
            assertNotEquals(original.uri, copy.uri); assertEquals(before, digest(original.uri))
            assertEquals(400,copy.width); assertEquals(400,copy.height); assertEquals(2,repo.photos.first().size)
            context.contentResolver.openInputStream(Uri.parse(copy.uri))!!.use { stream -> val exif=ExifInterface(stream); assertNull(exif.latLong); assertEquals("2026:10:13 10:00:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)); assertEquals(ExifInterface.ORIENTATION_NORMAL,exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,-1)) }
            assertNull(repo.recover())
        } finally { uris.forEach { context.contentResolver.delete(Uri.parse(it),null,null) }; db.close(); root.deleteRecursively() }
    }
    @Test fun invalidRecipeDoesNotProduceOrPublishEditedPixels() {
        val source = image()
        try {
            assertThrows(IllegalArgumentException::class.java) { PhotoEditEngine.render(source, PhotoEdits(brightness=Float.NaN)) }
            assertThrows(IllegalArgumentException::class.java) { PhotoEditEngine.render(source, PhotoEdits(cropRatio=-1f)) }
            assertEquals(Color.RED,source.getPixel(20,20))
        } finally { source.recycle() }
    }
}
