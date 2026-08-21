package app.codeg.android.feature.live

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.codeg.android.MainActivity
import app.codeg.android.R
import app.codeg.android.core.model.ConversationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskStatusNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun publish(snapshot: LiveTaskSnapshot) {
        if (!hasPermission()) return
        ensureChannel()
        if (snapshot.isIdle) {
            val n = XiaomiFocusParam.attach(
                baseBuilder(
                    title = context.getString(R.string.live_status_idle_title),
                    text = context.getString(R.string.live_status_idle_body),
                    ticker = context.getString(R.string.live_status_idle_ticker),
                    conversationId = null,
                ),
                XiaomiFocusParam.encode(
                    title = context.getString(R.string.live_status_idle_title),
                    content = context.getString(R.string.live_status_idle_body),
                    ticker = context.getString(R.string.live_status_idle_ticker),
                ),
            )
            manager.notify(NOTIFICATION_ID, n)
            return
        }
        val title = snapshot.title.ifBlank { context.getString(R.string.session_untitled) }
        val statusLine = statusLine(snapshot)
        val ticker = "${snapshot.agentLabel} · ${statusShort(snapshot.status)}"
        val n = XiaomiFocusParam.attach(
            baseBuilder(title, statusLine, ticker, snapshot.conversationId),
            XiaomiFocusParam.encode(title = title, content = statusLine, ticker = ticker),
        )
        manager.notify(NOTIFICATION_ID, n)
    }

    fun cancel() {
        if (!hasPermission()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        ensureChannel()
        val n = XiaomiFocusParam.attach(
            baseBuilder(
                title = context.getString(R.string.live_status_idle_title),
                text = context.getString(R.string.live_status_off),
                ticker = context.getString(R.string.live_status_off),
                conversationId = null,
            ).setOngoing(false),
            XiaomiFocusParam.cancel(),
        )
        manager.notify(NOTIFICATION_ID, n)
        manager.cancel(NOTIFICATION_ID)
    }

    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.live_status_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.live_status_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun baseBuilder(
        title: String,
        text: String,
        ticker: String,
        conversationId: Int?,
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse(
                if (conversationId != null) "codeg://conversation/$conversationId" else "codeg://tab/activity",
            )
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            conversationId ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_codeg)
            .setContentTitle(title)
            .setContentText(text)
            .setTicker(ticker)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    private fun statusLine(snapshot: LiveTaskSnapshot): String {
        val status = statusShort(snapshot.status)
        val base = if (snapshot.agentLabel.isBlank()) status else "${snapshot.agentLabel} · $status"
        return if (snapshot.stale) context.getString(R.string.live_status_stale, base) else base
    }

    private fun statusShort(status: ConversationStatus?): String = when (status) {
        ConversationStatus.IN_PROGRESS -> context.getString(R.string.session_status_running)
        ConversationStatus.PENDING_REVIEW -> context.getString(R.string.session_status_review)
        ConversationStatus.COMPLETED -> context.getString(R.string.session_status_done)
        ConversationStatus.CANCELLED -> context.getString(R.string.session_status_cancelled)
        else -> context.getString(R.string.session_status_other)
    }

    companion object {
        const val CHANNEL_ID = "codeg_live_task"
        const val NOTIFICATION_ID = 4101
    }
}
