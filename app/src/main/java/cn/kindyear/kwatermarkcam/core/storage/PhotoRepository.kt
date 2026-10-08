package cn.kindyear.kwatermarkcam.core.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import cn.kindyear.kwatermarkcam.core.database.AppDatabase
import cn.kindyear.kwatermarkcam.core.database.PhotoRecordEntity
import cn.kindyear.kwatermarkcam.core.watermark.WatermarkRenderer
import cn.kindyear.kwatermarkcam.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Durable capture journal: original JPEG + frozen snapshot survive saving failures and process death. */
data class PendingPhoto(val id: String, val directory: File, val snapshot: WatermarkSnapshot) {
    val original get() = File(directory, "original.jpg")
    val rendered get() = File(directory, "rendered.jpg")
    val journal get() = File(directory, "capture.json")
}
data class SaveResult(val record: PhotoRecord, val recordWritten: Boolean)
class PhotoMemoryException : Exception("Insufficient memory for full-resolution photo")

@Singleton
class PhotoRepository internal constructor(
    private val context: Context, private val db: AppDatabase,
    private val renderer: WatermarkRenderer, private val journalRoot: File,
) {
    @Inject constructor(@ApplicationContext context: Context, db: AppDatabase, renderer: WatermarkRenderer) :
        this(context, db, renderer, File(context.filesDir, "pending_photos"))
    private val root get() = journalRoot.apply { mkdirs() }
    val photos = db.photos().observe().map { list -> list.map { PhotoRecord(it.id, it.uri, it.createdAt, it.templateId, it.presetId, it.width, it.height) } }

    suspend fun create(snapshot: WatermarkSnapshot): PendingPhoto = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val pending = PendingPhoto(id, File(root, id).apply { check(mkdirs()) }, snapshot)
        writeJournal(pending, null)
        pending
    }
    suspend fun recover(): PendingPhoto? = withContext(Dispatchers.IO) {
        root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.lastModified() }?.firstNotNullOfOrNull { directory ->
            val journal = File(directory, "capture.json")
            if (!journal.exists()) return@firstNotNullOfOrNull null
            val doc = JSONObject(journal.readText())
            require(doc.getInt("version") == 1) { "Unsupported capture journal" }
            val rows = doc.getJSONArray("rows")
            val layout = doc.optJSONObject("layout")?.let { spec -> WatermarkLayoutSpec(
                spec.getDouble("width").toFloat(), spec.getDouble("maxWidth").toFloat(),
                spec.getDouble("margin").toFloat(), spec.getDouble("padding").toFloat(),
                spec.getDouble("font").toFloat(), spec.getDouble("maxHeight").toFloat(), spec.getInt("maxLines"),
            ) } ?: WatermarkLayoutSpec()
            val snapshot = WatermarkSnapshot(doc.getString("template"), doc.getString("preset"), doc.getString("title"),
                (0 until rows.length()).map { index -> rows.getJSONObject(index).let { WatermarkRow(it.getString("label"), it.getString("value")) } },
                doc.getBoolean("visible"), doc.getLong("time"), layout)
            val pending = PendingPhoto(directory.name, directory, snapshot)
            if (pending.original.length() > 0 || pending.rendered.length() > 0) pending else { directory.deleteRecursively(); null }
        }
    }
    suspend fun discard(pending: PendingPhoto) = withContext(Dispatchers.IO) {
        val uri = JSONObject(pending.journal.readText()).optString("uri").takeIf { it.isNotBlank() }
        // A published MediaStore entry is never deleted here; journal only owns pending entries.
        uri?.let { value ->
            context.contentResolver.query(Uri.parse(value), arrayOf(MediaStore.Images.Media.IS_PENDING), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) == 1) context.contentResolver.delete(Uri.parse(value), null, null)
            }
        }
        check(pending.directory.deleteRecursively())
    }

    /** Compose off-main at original dimensions; no screenshot or silent resolution reduction. */
    suspend fun save(pending: PendingPhoto): SaveResult = withContext(Dispatchers.IO) {
        if (!pending.rendered.exists()) render(pending)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(pending.rendered.path, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0)
        val resolver = context.contentResolver
        val document = JSONObject(pending.journal.readText())
        var uri = document.optString("uri").takeIf { it.isNotBlank() }?.let(Uri::parse)
        var published = false
        if (uri != null) {
            resolver.query(uri, arrayOf(MediaStore.Images.Media.IS_PENDING), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) published = cursor.getInt(0) == 0 else uri = null
            } ?: run { uri = null }
        }
        if (uri == null) {
            val time = Instant.ofEpochMilli(pending.snapshot.timestamp).atZone(ZoneId.systemDefault())
            val name = "WM_${time.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))}_${pending.id.take(8)}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WatermarkCamera/")
                put(MediaStore.Images.Media.DATE_TAKEN, pending.snapshot.timestamp)
                put(MediaStore.Images.Media.WIDTH, bounds.outWidth); put(MediaStore.Images.Media.HEIGHT, bounds.outHeight)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            try { writeJournal(pending, uri.toString()) }
            catch (e: Exception) {
                try { resolver.delete(requireNotNull(uri), null, null) } catch (cleanup: Exception) { Log.e("PhotoRepository", "Could not remove orphan pending URI", cleanup) }
                throw e
            }
        }
        val destination = requireNotNull(uri)
        if (!published) {
            resolver.openOutputStream(destination, "w")?.use { output -> pending.rendered.inputStream().use { it.copyTo(output) }; output.flush() }
                ?: error("Cannot open MediaStore output")
            check(resolver.update(destination, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
        }
        val record = PhotoRecord(pending.id, destination.toString(), pending.snapshot.timestamp,
            pending.snapshot.templateId, pending.snapshot.presetId, bounds.outWidth, bounds.outHeight)
        val recorded = try {
            db.photos().save(PhotoRecordEntity(record.id, record.uri, record.createdAt, record.templateId, record.presetId, record.width, record.height)); true
        } catch (e: Exception) { Log.e("PhotoRepository", "Photo published but record write failed", e); false }
        // Retain journal on record failure: retry writes the same URI, never publishes duplicates.
        if (recorded) pending.directory.deleteRecursively()
        SaveResult(record, recorded)
    }
    private fun render(pending: PendingPhoto) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(pending.original.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid captured image" }
        val runtime = Runtime.getRuntime()
        val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val required = bounds.outWidth.toLong() * bounds.outHeight * 4
        if (required > available * .65 || required > runtime.maxMemory() * .55) throw PhotoMemoryException()
        var bitmap: Bitmap? = null
        val temporary = File(pending.directory, "rendered.tmp")
        try {
            // ImageDecoder applies all eight EXIF orientations, including front-camera reflection.
            bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(pending.original)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            }
            renderer.draw(Canvas(bitmap), bitmap.width, bitmap.height, pending.snapshot)
            FileOutputStream(temporary).use { output -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)); output.flush(); output.fd.sync() }
            val old = ExifInterface(pending.original)
            val final = ExifInterface(temporary)
            // Explicit allowlist excludes GPS, device serial numbers and MakerNote.
            listOf(ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_WHITE_BALANCE).forEach { tag -> old.getAttribute(tag)?.let { final.setAttribute(tag, it) } }
            final.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            final.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, bitmap.width.toString())
            final.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, bitmap.height.toString())
            final.setAttribute(ExifInterface.TAG_SOFTWARE, "KWatermarkCam")
            final.saveAttributes()
            check(temporary.renameTo(pending.rendered))
        } catch (e: OutOfMemoryError) {
            Log.e("PhotoRepository", "Image allocation failed", e)
            throw PhotoMemoryException()
        } finally { bitmap?.recycle(); temporary.delete() }
    }
    private fun writeJournal(pending: PendingPhoto, uri: String?) {
        val s = pending.snapshot
        val doc = JSONObject().apply {
            put("layout", JSONObject().apply {
                put("width", s.layout.widthFraction); put("maxWidth", s.layout.maxWidthOn1080)
                put("margin", s.layout.marginOn1080); put("padding", s.layout.paddingOn1080)
                put("font", s.layout.fontOn1080); put("maxHeight", s.layout.maxHeightFraction); put("maxLines", s.layout.maxLines)
            })
            put("version", 1); put("template", s.templateId); put("preset", s.presetId); put("title", s.title)
            put("time", s.timestamp); put("visible", s.visible); put("uri", uri ?: "")
            put("rows", JSONArray(s.rows.map { JSONObject().put("label", it.label).put("value", it.value) }))
        }
        val temporary = File(pending.directory, "journal.tmp")
        FileOutputStream(temporary).use { stream -> stream.write(doc.toString().toByteArray()); stream.fd.sync() }
        check(temporary.renameTo(pending.journal))
    }
    /** Removes a local record only; it never deletes a user's system photo. */
    suspend fun forgetRecord(id: String) { db.photos().delete(id) }
    suspend fun thumbnail(uri: String, size: Int = 256): Bitmap? = withContext(Dispatchers.IO) {
        try { context.contentResolver.loadThumbnail(Uri.parse(uri), android.util.Size(size, size), null) }
        catch (e: Exception) { Log.w("PhotoRepository", "Owned photo is unavailable", e); null }
    }
    suspend fun preview(uri: String, maximum: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val sample = (maxOf(info.size.width, info.size.height) + maximum - 1) / maximum
                decoder.setTargetSampleSize(sample.coerceAtLeast(1))
            }
        } catch (e: Exception) { Log.w("PhotoRepository", "Photo preview failed", e); null }
        catch (e: OutOfMemoryError) { Log.w("PhotoRepository", "Preview memory exhausted", e); null }
    }
}
