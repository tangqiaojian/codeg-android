package app.codeg.android.feature.live

import android.content.Context
import app.codeg.android.core.data.ServerRepository
import app.codeg.android.core.datastore.AppSettingsStore
import app.codeg.android.core.datastore.ServerProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the notch notification and home widget in sync with the latest
 * dispatched conversation. Polls while either surface is enabled.
 */
@Singleton
class LiveTaskCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ServerRepository,
    private val settings: AppSettingsStore,
    private val notifier: TaskStatusNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var latest = LiveTaskSnapshot()
    private var started = false
    private val prefs by lazy { context.getSharedPreferences("live_task_snapshot", Context.MODE_PRIVATE) }

    fun start() {
        if (started) return
        started = true
        latest = LiveTaskFreshness.mark(LiveTaskSnapshotCodec.decode(prefs.getString("snap", null)))
        scope.launch {
            combine(settings.settings, repository.selectedProfile) { prefs, profile -> prefs to profile }
                .collectLatest { (prefs, profile) ->
                    if (!prefs.liveNotification && !prefs.liveWidget) {
                        notifier.cancel()
                        TaskStatusWidgetProvider.publish(context, LiveTaskSnapshot())
                        return@collectLatest
                    }
                    refresh(profile, prefs.liveNotification, prefs.liveWidget)
                    while (true) {
                        delay(12_000)
                        refresh(profile, prefs.liveNotification, prefs.liveWidget)
                    }
                }
        }
        scope.launch {
            repository.conversationsChanged.collect { requestRefresh() }
        }
    }

    fun requestRefresh(): Job = scope.launch {
        val prefs = settings.settings.first()
        val profile = repository.selectedProfile.first()
        if (prefs.liveNotification || prefs.liveWidget) {
            refresh(profile, prefs.liveNotification, prefs.liveWidget)
        }
    }

    val snapshot: LiveTaskSnapshot get() = latest

    private suspend fun refresh(profile: ServerProfile?, notify: Boolean, widget: Boolean) {
        mutex.withLock {
            val now = java.time.Instant.now()
            val snap = if (profile == null) {
                LiveTaskSnapshot()
            } else {
                val client = repository.client(profile)
                val convs = client?.let { runCatching { it.listConversations() }.getOrNull() }
                if (convs == null && latest.conversationId != null) {
                    LiveTaskFreshness.mark(latest.copy(stale = true), now)
                } else {
                    LiveTaskPicker.pick(convs.orEmpty()).copy(fetchedAt = now, stale = false)
                }
            }
            latest = snap
            prefs.edit().putString("snap", LiveTaskSnapshotCodec.encode(snap)).apply()
            if (notify) notifier.publish(snap) else notifier.cancel()
            if (widget) TaskStatusWidgetProvider.publish(context, snap)
            else TaskStatusWidgetProvider.publish(context, LiveTaskSnapshot())
        }
    }
}
