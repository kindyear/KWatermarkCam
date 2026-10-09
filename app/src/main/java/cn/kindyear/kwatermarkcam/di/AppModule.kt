package cn.kindyear.kwatermarkcam.di

import android.content.Context
import androidx.room.Room
import cn.kindyear.kwatermarkcam.core.database.AppDatabase
import cn.kindyear.kwatermarkcam.core.database.MIGRATION_1_2
import cn.kindyear.kwatermarkcam.core.location.GeocodingRepository
import cn.kindyear.kwatermarkcam.core.location.SystemGeocodingRepository
import cn.kindyear.kwatermarkcam.data.repository.RoomPresetRepository
import cn.kindyear.kwatermarkcam.domain.repository.PresetRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "kwatermarkcam.db").addMigrations(MIGRATION_1_2).build()
    @Provides fun geocoding(repository: SystemGeocodingRepository): GeocodingRepository = repository
    @Provides fun presets(repository: RoomPresetRepository): PresetRepository = repository
}
