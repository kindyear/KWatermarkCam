package cn.kindyear.kwatermarkcam

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.kindyear.kwatermarkcam.core.database.*
import cn.kindyear.kwatermarkcam.core.datastore.SettingsStore
import cn.kindyear.kwatermarkcam.core.watermark.TemplateRegistry
import cn.kindyear.kwatermarkcam.data.repository.RoomPresetRepository
import cn.kindyear.kwatermarkcam.domain.model.CustomWatermarkField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in public demo content for genuine device screenshots; never touches the release app. */
@RunWith(AndroidJUnit4::class)
class ReadmeScreenshotsFixture {
    @Test fun prepareOrCleanDemoContent() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("readmeScreenshots") == "prepare" || args.getString("readmeScreenshots") == "cleanup")
        val context: Context = ApplicationProvider.getApplicationContext()
        require(context.packageName == "cn.kindyear.kwatermarkcam.debug")
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "kwatermarkcam.db").addMigrations(MIGRATION_1_2).build()
        val repo = RoomPresetRepository(db, TemplateRegistry(context), context)
        val settings = SettingsStore(context)
        val marker = context.getSharedPreferences("readme_fixture", Context.MODE_PRIVATE)
        try {
            if (args.getString("readmeScreenshots") == "cleanup") {
                val folder = marker.getString("folder", null)
                if (folder != null) {
                    val demoPresets = repo.presets.first().filter { it.folderId == folder }
                    val demoIds = demoPresets.map { it.id }.toSet()
                    db.photos().observe().first().filter { it.presetId in demoIds }.forEach { photo ->
                        context.contentResolver.delete(android.net.Uri.parse(photo.uri), null, null)
                        db.photos().delete(photo.id)
                    }
                    demoPresets.forEach { repo.delete(it.id) }
                    if (repo.folders.first().any { it.id == folder }) repo.deleteFolder(folder)
                    settings.select(requireNotNull(marker.getString("template", null)), requireNotNull(marker.getString("preset", null)))
                    settings.text("theme", requireNotNull(marker.getString("theme", null)))
                    settings.text("location_display", requireNotNull(marker.getString("location_display", null)))
                    settings.boolean("location", marker.getBoolean("location", false))
                    marker.edit().clear().commit()
                }
            } else {
                require(!marker.contains("folder")) { "Clean the existing fixture before preparing another" }
                val original = settings.settings.first()
                val folder = repo.createFolder("示例记录", null)
                marker.edit().putString("folder", folder).putString("template", original.templateId).putString("preset", original.presetId)
                    .putString("theme", original.theme).putString("location_display", original.locationDisplay.name).putBoolean("location", original.location).commit()
                val engineering = repo.saveDraft(null, "construction-default", "滨河公园 · 施工记录", mapOf("project" to "滨河公园 · 示例工程", "company" to "示例施工公司", "supervision_company" to "示例监理公司", "design_company" to "示例设计公司", "construction_company" to "示例建设公司", "address" to "示例地点 · 项目现场", "photographer" to "现场记录员", "note" to "施工进度记录"), emptySet(), folder,
                    listOf(CustomWatermarkField("custom:demo1", "验收编号", "A-102"), CustomWatermarkField("custom:demo2", "天气", "晴"), CustomWatermarkField("custom:demo3", "作业区域", "东区")))
                repo.pin(engineering, true)
                repo.saveDraft(null, "attendance-clock", "团队 · 到场记录", mapOf("photographer" to "记录人员", "team" to "现场团队", "address" to "示例地点", "note" to "现场工作记录"), emptySet(), folder)
                repo.saveDraft(null, "inspection-record", "设备 · 巡检记录", mapOf("asset" to "样例配电箱", "status" to "正常", "photographer" to "巡检人员", "address" to "示例地点"), emptySet(), folder)
                settings.select("construction-default", engineering)
                settings.text("theme", "light")
                settings.boolean("location", false)
            }
        } finally { db.close() }
    }
    @Test fun verifyManuallyCapturedDemoPhotos() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("readmeScreenshots") == "verifyPhotos")
        val context: Context = ApplicationProvider.getApplicationContext()
        require(context.packageName.endsWith(".debug"))
        val folder = requireNotNull(context.getSharedPreferences("readme_fixture", Context.MODE_PRIVATE).getString("folder", null))
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "kwatermarkcam.db").addMigrations(MIGRATION_1_2).build()
        try {
            val ids = db.presets().inFolder(folder).map { it.id }.toSet()
            val photos = db.photos().observe().first().filter { it.presetId in ids }
            org.junit.Assert.assertTrue("Capture both back and front demo photos before verification", photos.size >= 2)
            photos.forEach { photo ->
                context.contentResolver.openInputStream(android.net.Uri.parse(photo.uri))!!.use { stream ->
                    val info = androidx.exifinterface.media.ExifInterface(stream)
                    org.junit.Assert.assertEquals(androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL, info.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, -1))
                    org.junit.Assert.assertNull(info.latLong)
                }
                org.junit.Assert.assertTrue(photo.width > 0 && photo.height > 0)
                val ratio = minOf(photo.width, photo.height).toDouble() / maxOf(photo.width, photo.height)
                org.junit.Assert.assertEquals(3.0 / 4, ratio, .015)
                android.util.Log.i("ReadmePhotoVerification", "Readable normalized JPEG ${photo.width} x ${photo.height}, preset=${photo.presetId}")
            }
        } finally { db.close() }
    }

}
