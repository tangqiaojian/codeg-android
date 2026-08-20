package app.codeg.android.core.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

class DataStoreAppUpdatePrefs(
    private val dataStore: DataStore<Preferences>,
) : AppUpdatePrefs {
    override suspend fun lastCheckEpochMs(): Long =
        dataStore.data.first()[LAST] ?: 0L

    override suspend fun setLastCheckEpochMs(value: Long) {
        dataStore.edit { it[LAST] = value }
    }

    override suspend fun dismissedTag(): String? =
        dataStore.data.first()[DISMISSED]

    override suspend fun setDismissedTag(tag: String?) {
        dataStore.edit {
            if (tag.isNullOrBlank()) it.remove(DISMISSED) else it[DISMISSED] = tag
        }
    }

    private companion object {
        val LAST = longPreferencesKey("app_update_last_check_ms")
        val DISMISSED = stringPreferencesKey("app_update_dismissed_tag")
    }
}
