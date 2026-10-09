package cn.kindyear.kwatermarkcam.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "preset_folders", indices = [Index("parentId")], foreignKeys = [ForeignKey(
    entity = PresetFolderEntity::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.SET_NULL,
)])
data class PresetFolderEntity(
    @PrimaryKey val id: String, val name: String,
    @ColumnInfo(defaultValue = "NULL") val parentId: String?, val createdAt: Long,
)
@Entity(tableName = "presets", indices = [Index("templateId"), Index("folderId")], foreignKeys = [ForeignKey(
    entity = PresetFolderEntity::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.SET_NULL,
)])
data class WatermarkPresetEntity(
    @PrimaryKey val id: String, val templateId: String, val name: String,
    val fieldsJson: String, val isPinned: Boolean, val sortOrder: Int,
    val createdAt: Long, val updatedAt: Long,
    @ColumnInfo(defaultValue = "NULL") val folderId: String? = null,
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
    @Query("SELECT * FROM presets WHERE folderId IS :folderId ORDER BY isPinned DESC, sortOrder, createdAt, id")
    suspend fun inFolder(folderId: String?): List<WatermarkPresetEntity>
    @Query("SELECT * FROM presets WHERE id = :id") suspend fun get(id: String): WatermarkPresetEntity?
    @Insert suspend fun insert(entity: WatermarkPresetEntity)
    @Update suspend fun update(entity: WatermarkPresetEntity)
    @Query("DELETE FROM presets WHERE id = :id") suspend fun delete(id: String)
}
@Dao
interface FolderDao {
    @Query("SELECT * FROM preset_folders ORDER BY name COLLATE NOCASE, createdAt, id")
    fun observe(): Flow<List<PresetFolderEntity>>
    @Query("SELECT * FROM preset_folders WHERE id = :id") suspend fun get(id: String): PresetFolderEntity?
    @Query("SELECT * FROM preset_folders WHERE parentId IS :parentId") suspend fun children(parentId: String?): List<PresetFolderEntity>
    @Insert suspend fun insert(entity: PresetFolderEntity)
    @Update suspend fun update(entity: PresetFolderEntity)
    @Query("DELETE FROM preset_folders WHERE id = :id") suspend fun delete(id: String)
}
@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY createdAt DESC") fun observe(): Flow<List<PhotoRecordEntity>>
    @Upsert suspend fun save(entity: PhotoRecordEntity)
    @Query("DELETE FROM photos WHERE id = :id") suspend fun delete(id: String)
}
/** Explicit migration preserves v1 preset IDs, content, selection and photo records at the library root. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS preset_folders (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, parentId TEXT DEFAULT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(parentId) REFERENCES preset_folders(id) ON DELETE SET NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_preset_folders_parentId ON preset_folders(parentId)")
        db.execSQL("ALTER TABLE presets ADD COLUMN folderId TEXT DEFAULT NULL REFERENCES preset_folders(id) ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_presets_folderId ON presets(folderId)")
    }
}
@Database(entities = [WatermarkPresetEntity::class, PhotoRecordEntity::class, PresetFolderEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun presets(): PresetDao
    abstract fun folders(): FolderDao
    abstract fun photos(): PhotoDao
}
