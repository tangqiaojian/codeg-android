package app.codeg.android

import android.app.Application
import app.codeg.android.feature.live.LiveTaskCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. [@HiltAndroidApp][HiltAndroidApp] triggers Hilt's
 * code generation and creates the application-level dependency container.
 */
@HiltAndroidApp
class CodegApplication : Application() {

    @Inject lateinit var liveTaskCoordinator: LiveTaskCoordinator

    override fun onCreate() {
        super.onCreate()
        liveTaskCoordinator.start()
    }
}
