package cn.kindyear.kwatermarkcam.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import cn.kindyear.kwatermarkcam.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appDataStore by preferencesDataStore("settings")
/** Single DataStore instance. Edit individual keys to avoid concurrent lost updates. */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.appDataStore
    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            theme = p[stringPreferencesKey("theme")]?.takeIf { it in listOf("light", "dark", "system") } ?: "system",
            dynamicColor = p[booleanPreferencesKey("dynamic")] ?: true,
            frontCamera = p[booleanPreferencesKey("front")] ?: false,
            wideAspect = p[booleanPreferencesKey("wide")] ?: false,
            flash = p[stringPreferencesKey("flash")]?.takeIf { it in listOf("off", "on", "auto") } ?: "off",
            grid = p[booleanPreferencesKey("grid")] ?: false,
            watermark = p[booleanPreferencesKey("watermark")] ?: true,
            location = p[booleanPreferencesKey("location")] ?: false,
            templateId = p[stringPreferencesKey("template")] ?: "construction-default",
            presetId = p[stringPreferencesKey("preset")] ?: "",
        )
    }
    suspend fun boolean(key: String, value: Boolean) { store.edit { it[booleanPreferencesKey(key)] = value } }
    suspend fun text(key: String, value: String) { store.edit { it[stringPreferencesKey(key)] = value } }
    suspend fun select(templateId: String, presetId: String) {
        store.edit { it[stringPreferencesKey("template")] = templateId; it[stringPreferencesKey("preset")] = presetId }
    }
}
