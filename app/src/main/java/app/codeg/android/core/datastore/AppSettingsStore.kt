package app.codeg.android.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Light / Dark / System appearance, mirroring the iOS `AppearanceMode`. */
enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun from(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** App-wide appearance preferences. [accentId] maps to a design-system palette. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentId: String = DEFAULT_ACCENT,
    val liveNotification: Boolean = false,
    val liveWidget: Boolean = false,
) {
    companion object {
        const val DEFAULT_ACCENT = "orange"
    }
}

/**
 * Persists appearance settings (theme mode + accent). Applied app-wide
 * instantly; never synced to the server (matches iOS).
 */
@Singleton
class AppSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.from(prefs[MODE]),
            accentId = prefs[ACCENT] ?: AppSettings.DEFAULT_ACCENT,
            liveNotification = prefs[LIVE_NOTIFICATION] ?: false,
            liveWidget = prefs[LIVE_WIDGET] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[MODE] = mode.id }
    }

    suspend fun setAccent(accentId: String) {
        dataStore.edit { it[ACCENT] = accentId }
    }

    suspend fun setLiveNotification(enabled: Boolean) {
        dataStore.edit { it[LIVE_NOTIFICATION] = enabled }
    }

    suspend fun setLiveWidget(enabled: Boolean) {
        dataStore.edit { it[LIVE_WIDGET] = enabled }
    }

    private companion object {
        val MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent_id")
        val LIVE_NOTIFICATION = booleanPreferencesKey("live_status_notification")
        val LIVE_WIDGET = booleanPreferencesKey("live_status_widget")
    }
}
