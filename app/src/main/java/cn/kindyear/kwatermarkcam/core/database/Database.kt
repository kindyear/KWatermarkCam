package cn.kindyear.kwatermarkcam.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "presets", indices = [Index("templateId")])
data class WatermarkPresetEntity(
    @PrimaryKey val id: String, val templateId: String, val name: String,
    val fieldsJson: String, val isPinned: Boolean, val sortOrder: Int,
    val createdAt: Long, val updatedAt: Long,
)
@Entity(tableName = "photos", indices = [Index(value = ["uri"], unique = true)])
data class PhotoRecordEntity(
    @PrimaryKey val id: String, val uri: String, val createdAt: Long,
    val templateId: String, val presetId: String, val width: Int, val height: Int,
)
@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY isPinned DESC, sortOrder ASC, createdAt ASC, id ASC")
    fun observe(): Flow<List<WatermarkPresetEntity>>
    @Query("SELECT * FROM presets WHERE templateId = :templateId ORDER BY isPinned DESC, sortOrder ASC, createdAt ASC, id ASC")
    suspend fun list(templateId: String): List<WatermarkPresetEntity>
    @Query("SELECT * FROM presets WHERE id = :id") suspend fun get(id: String): WatermarkPresetEntity?
    @Insert suspend fun insert(entity: WatermarkPresetEntity)
    @Update suspend fun update(entity: WatermarkPresetEntity)
    @Query("DELETE FROM presets WHERE id = :id") suspend fun delete(id: String)
}
@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY createdAt DESC") fun observe(): Flow<List<PhotoRecordEntity>>
    @Upsert suspend fun save(entity: PhotoRecordEntity)
    @Query("DELETE FROM photos WHERE id = :id") suspend fun delete(id: String)
}
/** v1 is exported under app/schemas. Every future version requires an explicit tested Migration. */
@Database(entities = [WatermarkPresetEntity::class, PhotoRecordEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun presets(): PresetDao
    abstract fun photos(): PhotoDao
}
