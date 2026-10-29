package com.danpatten.politicalblocker

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Watches notifications from SMS apps. When an incoming text from an unknown sender is
 * classified as political, the notification is cancelled, the SMS app's own
 * "Archive" (or "Mark as read") action is fired, and the message is saved to the app's list.
 */
class SmsNotificationListener : NotificationListenerService() {

    private data class IncomingText(val sender: String, val personUri: String?, val body: String)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!BlockerApp.isEnabled(this) || !isSmsApp(sbn.packageName)) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val text = extract(notification) ?: return
        val app = BlockerApp.get(this)
        if (app.store.isSenderAllowed(text.sender)) return
        if (ContactChecker.isKnownContact(this, text.sender, text.personUri)) return

        val result = app.classifier.classify(text.body)
        if (!result.isPolitical) return

        Log.i(TAG, "Suppressing political text from ${text.sender} (p=${"%.2f".format(result.probability)})")
        cancelNotification(sbn.key)
        archiveInSmsApp(notification)
        cancelOrphanedSummary(sbn)

        if (app.store.addSuppressed(text.sender, text.body, result.probability, result.matchedKeywords, sbn.packageName)) {
            sendBroadcast(Intent(BlockerApp.ACTION_SUPPRESSED_CHANGED).setPackage(packageName))
        }
    }

    private fun isSmsApp(pkg: String): Boolean =
        pkg in KNOWN_SMS_APPS || pkg == Telephony.Sms.getDefaultSmsPackage(this)

    /** Pulls the latest incoming message out of a (usually MessagingStyle) SMS notification. */
    private fun extract(notification: Notification): IncomingText? {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        if (style != null) {
            if (style.isGroupConversation) return null
            val message = style.messages.lastOrNull() ?: return null
            // A null person is the user's own outgoing message.
            val person = message.person ?: return null
            val sender = person.name?.toString() ?: style.conversationTitle?.toString() ?: return null
            val body = message.text?.toString() ?: return null
            return IncomingText(sender, person.uri, body)
        }

        val extras = notification.extras
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return null
        return IncomingText(sender, null, body)
    }

    /** Uses the SMS app's own notification action so the thread leaves the inbox / unread count. */
    private fun archiveInSmsApp(notification: Notification) {
        val actions = notification.actions?.filter { it.remoteInputs.isNullOrEmpty() } ?: return
        val action = actions.firstOrNull { it.title?.toString()?.contains("archive", ignoreCase = true) == true }
            ?: actions.firstOrNull { it.title?.toString()?.contains("read", ignoreCase = true) == true }
            ?: return
        try {
            action.actionIntent?.send()
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "SMS app action '${action.title}' was cancelled", e)
        }
    }

    /** If the cancelled text was the last one in its group, drop the leftover group summary too. */
    private fun cancelOrphanedSummary(sbn: StatusBarNotification) {
        val group = sbn.notification.group ?: return
        val siblings = try {
            activeNotifications.filter { it.packageName == sbn.packageName && it.notification.group == group && it.key != sbn.key }
        } catch (e: SecurityException) {
            return
        }
        if (siblings.all { it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 }) {
            siblings.forEach { cancelNotification(it.key) }
        }
    }

    companion object {
        private const val TAG = "PoliticalBlocker"

        private val KNOWN_SMS_APPS = setOf(
            "com.google.android.apps.messaging",   // Google Messages
            "com.samsung.android.messaging",       // Samsung Messages
            "com.android.mms",
            "com.android.messaging",
            "com.motorola.messaging",
            "com.oneplus.mms",
            "com.verizon.messaging.vzmsgs",
            "com.textra",
        )
    }
}
