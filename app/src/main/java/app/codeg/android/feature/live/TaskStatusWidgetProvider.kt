package app.codeg.android.feature.live

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import app.codeg.android.MainActivity
import app.codeg.android.R
import app.codeg.android.core.model.ConversationStatus
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

class TaskStatusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val coordinator = entry(context).coordinator()
        bind(context, appWidgetManager, appWidgetIds, coordinator.snapshot)
        coordinator.requestRefresh()
    }

    override fun onEnabled(context: Context) {
        entry(context).coordinator().requestRefresh()
    }

    companion object {
        fun publish(context: Context, snapshot: LiveTaskSnapshot) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TaskStatusWidgetProvider::class.java))
            if (ids.isEmpty()) return
            bind(context, manager, ids, snapshot)
        }

        private fun bind(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            snapshot: LiveTaskSnapshot,
        ) {
            val title = if (snapshot.isIdle) {
                context.getString(R.string.live_status_idle_title)
            } else {
                snapshot.title.ifBlank { context.getString(R.string.session_untitled) }
            }
            val body = if (snapshot.isIdle) {
                context.getString(R.string.live_status_idle_body)
            } else {
                val status = when (snapshot.status) {
                    ConversationStatus.IN_PROGRESS -> context.getString(R.string.session_status_running)
                    ConversationStatus.PENDING_REVIEW -> context.getString(R.string.session_status_review)
                    ConversationStatus.COMPLETED -> context.getString(R.string.session_status_done)
                    ConversationStatus.CANCELLED -> context.getString(R.string.session_status_cancelled)
                    else -> context.getString(R.string.session_status_other)
                }
                if (snapshot.agentLabel.isBlank()) status else "${snapshot.agentLabel} · $status"
            }
            val uri = Uri.parse(
                snapshot.conversationId?.let { "codeg://conversation/$it" } ?: "codeg://tab/activity",
            )
            val click = PendingIntent.getActivity(
                context,
                snapshot.conversationId ?: 0,
                Intent(context, MainActivity::class.java).setData(uri)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_task_status)
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_body, body)
                views.setOnClickPendingIntent(R.id.widget_root, click)
                manager.updateAppWidget(id, views)
            }
        }

        private fun entry(context: Context): LiveTaskEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, LiveTaskEntryPoint::class.java)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LiveTaskEntryPoint {
    fun coordinator(): LiveTaskCoordinator
}
