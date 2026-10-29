package com.danpatten.politicalblocker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * "Quiet mode": when the user sets their SMS app's notifications to Silent, political texts never
 * pop up or make a sound, and this app raises the alert for every other text instead.
 */
object Alerts {

    private const val CHANNEL = "texts"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.alert_channel_name), NotificationManager.IMPORTANCE_HIGH)
            .apply { description = context.getString(R.string.alert_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * @param smsKey the SMS app's notification key, so ours is cleared when theirs is (text read).
     * @param open the SMS app's own tap action, which opens the conversation.
     * @param markRead the SMS app's "Mark as read" intent. Reply isn't copied: a reply sent from
     *   our copy would leave a spinner that only the SMS app could clear.
     */
    fun show(context: Context, smsKey: String, sender: String, body: String, `when`: Long, open: PendingIntent?, markRead: PendingIntent?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(sender)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setWhen(`when`)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (markRead != null) {
            builder.addAction(
                Notification.Action.Builder(null, context.getString(R.string.mark_as_read), markRead)
                    .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .build()
            )
        }
        context.getSystemService(NotificationManager::class.java).notify(smsKey.hashCode(), builder.build())
    }

    fun cancel(context: Context, smsKey: String) {
        context.getSystemService(NotificationManager::class.java).cancel(smsKey.hashCode())
    }
}
