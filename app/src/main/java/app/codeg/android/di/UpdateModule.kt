package app.codeg.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.codeg.android.core.common.DispatcherProvider
import app.codeg.android.core.update.AppUpdateManager
import app.codeg.android.core.update.AppUpdatePrefs
import app.codeg.android.core.update.DataStoreAppUpdatePrefs
import app.codeg.android.core.update.OkHttpUpdateRemote
import app.codeg.android.core.update.UpdateRemote
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideUpdateRemote(): UpdateRemote = OkHttpUpdateRemote()

    @Provides
    @Singleton
    fun provideAppUpdatePrefs(dataStore: DataStore<Preferences>): AppUpdatePrefs =
        DataStoreAppUpdatePrefs(dataStore)

    @Provides
    @Singleton
    fun provideAppUpdateManager(
        @ApplicationContext context: Context,
        remote: UpdateRemote,
        prefs: AppUpdatePrefs,
        dispatchers: DispatcherProvider,
    ): AppUpdateManager = AppUpdateManager(
        currentVersionName = {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty()
        },
        remote = remote,
        prefs = prefs,
        cacheDir = context.cacheDir,
        clock = { System.currentTimeMillis() },
        io = dispatchers.io,
    )
}
