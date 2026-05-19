package com.pumpernickel.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.pumpernickel.android.R
import com.pumpernickel.infrastructure.geofence.formatGraceDuration

/**
 * D-19-15 — single notification channel + 5 notification triggers.
 * Channel registration is idempotent — safe to call from Application.onCreate
 * AND every notification post (the system de-duplicates by channel id).
 */
object GeofenceNotifications {

    const val CHANNEL_ID = "workout.geofence"
    private const val NOTIFICATION_ID_BASE = 190500

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService<NotificationManager>() ?: return
        val existing = mgr.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.geofence_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.geofence_channel_desc)
        }
        mgr.createNotificationChannel(channel)
    }

    // D-21-06 — dynamic grace-period: caller passes the user-configured seconds so the
    // notification body reflects Settings (10sec → "10 Sekunden", 300 → "5 Minuten", …).
    fun postExitDetected(context: Context, graceSeconds: Int) {
        post(context, NOTIFICATION_ID_BASE + 1,
            context.getString(R.string.geofence_notification_exit_title),
            context.getString(
                R.string.geofence_notification_exit_body,
                formatGraceDuration(graceSeconds)
            ))
    }

    fun postReEntered(context: Context) {
        post(context, NOTIFICATION_ID_BASE + 2,
            context.getString(R.string.geofence_notification_reenter_title),
            context.getString(R.string.geofence_notification_reenter_body))
    }

    fun postGraceExpired(context: Context, loggedSets: Int, penaltyXp: Int) {
        post(context, NOTIFICATION_ID_BASE + 3,
            context.getString(R.string.geofence_notification_grace_expired_title),
            context.getString(R.string.geofence_notification_grace_expired_body, loggedSets, penaltyXp))
    }

    fun postEarlyExitWithBudget(context: Context, remainingAfter: Int) {
        post(context, NOTIFICATION_ID_BASE + 4,
            context.getString(R.string.geofence_notification_grace_expired_title),
            context.getString(R.string.geofence_notification_early_exit_budget_body, remainingAfter))
    }

    fun postEarlyExitWithPenalty(context: Context, penaltyXp: Int) {
        post(context, NOTIFICATION_ID_BASE + 5,
            context.getString(R.string.geofence_notification_grace_expired_title),
            context.getString(R.string.geofence_notification_early_exit_penalty_body, penaltyXp))
    }

    private fun post(context: Context, id: Int, title: String, body: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)   // app does not yet ship a custom mono icon
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val mgr = NotificationManagerCompat.from(context)
        try {
            mgr.notify(id, notification)
        } catch (se: SecurityException) {
            // POST_NOTIFICATIONS not granted on API 33+ — silent fallback (UI banner already informs user).
        }
    }
}
