package cn.kindyear.kwatermarkcam

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.kindyear.kwatermarkcam.core.database.*
import cn.kindyear.kwatermarkcam.core.storage.PhotoRepository
import cn.kindyear.kwatermarkcam.core.watermark.WatermarkRenderer
import cn.kindyear.kwatermarkcam.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit opt-in fixture for safe public samples; never release data. */
@RunWith(AndroidJUnit4::class)
class GalleryDeviceFixture {
    @Test fun prepareOrCleanSamples() = runBlocking {
        val mode=InstrumentationRegistry.getArguments().getString("galleryFixture")
        assumeTrue(mode in listOf("prepare","cleanup"))
        val context: Context = ApplicationProvider.getApplicationContext()
        require(context.packageName=="cn.kindyear.kwatermarkcam.debug")
        val db=Room.databaseBuilder(context,AppDatabase::class.java,"kwatermarkcam.db").addMigrations(MIGRATION_1_2).build()
        val root=File(context.cacheDir,"gallery-fixture")
        val repo=PhotoRepository(context,db,WatermarkRenderer(),root)
        try {
            val current=repo.photos.first().filter { it.presetId=="qa-gallery-1013" }
            if(mode=="cleanup") { current.forEach { context.contentResolver.delete(Uri.parse(it.uri),null,null); db.photos().delete(it.id) } }
            else {
                require(current.isEmpty())
                repeat(6) { index ->
                    val width=if(index%2==0) 900 else 1200; val height=if(index%2==0) 1200 else 900
                    val b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                    val canvas=Canvas(b)
                    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { shader=LinearGradient(0f,0f,width.toFloat(),height.toFloat(),Color.rgb(60+index*20,130,165),Color.rgb(220,180-index*12,100),Shader.TileMode.CLAMP) }
                    canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
                    paint.shader=null;paint.color=Color.WHITE;paint.textSize=56f
                    canvas.drawText("样例照片 ${index+1}",60f,120f,paint)
                    val pending=repo.create(WatermarkSnapshot("daily-moment","qa-gallery-1013","",emptyList(),false,System.currentTimeMillis()-index*86400000L,WatermarkLayoutSpec()))
                    try { pending.original.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG,95,it) } } finally { b.recycle() }
                    check(repo.save(pending).recordWritten)
                }
            }
        } finally { db.close();root.deleteRecursively() }
    }
}
