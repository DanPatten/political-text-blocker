package com.danpatten.politicalblocker

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Watches notifications from SMS apps. When an incoming text from an unknown sender is
 * classified as political or as a spam ad (each can be switched off), the notification is cancelled, the SMS app's own
 * "Archive" (or "Mark as read") action is fired, and the message is saved to the app's list.
 */
class SmsNotificationListener : NotificationListenerService() {

    private data class IncomingText(val sender: String, val personUri: String?, val body: String)

    /** "sender|body" of texts we've raised a quiet-mode alert for, oldest first. */
    private val alerted = LinkedHashSet<String>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    /** Catches texts that arrived while the listener was disconnected (e.g. during an app update). */
    override fun onListenerConnected() {
        BlockerApp.get(this).record("Notification access connected; watching for texts")
        // Train now rather than on the first text, so that one isn't held up.
        Thread { BlockerApp.get(this).classifier }.start()
        val active = try {
            activeNotifications
        } catch (e: SecurityException) {
            return
        }
        active?.forEach(::handle)
    }

    private fun handle(sbn: StatusBarNotification) {
        val blockPolitical = BlockerApp.isEnabled(this)
        val blockSpam = BlockerApp.isSpamBlockingOn(this)
        if (!(blockPolitical || blockSpam) || !isSmsApp(sbn.packageName)) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val app = BlockerApp.get(this)
        val text = extract(sbn)?.let { it.copy(sender = ContactChecker.canonicalSender(it.sender)) }
        if (text == null) {
            app.record("Skipped a text notification: couldn't tell who sent it (hidden by Android, or your own message)")
            return
        }
        val snippet = "\"${text.body.take(BlockerApp.SNIPPET)}…\""
        if (app.store.isSenderAllowed(text.sender)) {
            allow(sbn, text, "Allowed ${text.sender} (marked not political): $snippet")
            return
        }
        if (ContactChecker.isKnownContact(this, text.sender, text.personUri)) {
            allow(sbn, text, "Allowed ${text.sender} (in contacts): $snippet")
            return
        }

        val result = app.classifier.classify(text.body)
        val political = (result.politicalProbability * 100).toInt()
        val spam = (result.spamProbability * 100).toInt()
        val (category, probability, keywords) = when {
            result.isPolitical && blockPolitical -> Triple(Store.CATEGORY_POLITICAL, result.politicalProbability, result.politicalKeywords)
            result.isSpam && blockSpam -> Triple(Store.CATEGORY_SPAM, result.spamProbability, result.spamKeywords)
            else -> {
                val why = when {
                    result.isPolitical -> "political, but political blocking is off"
                    result.isSpam -> "spam ad, but spam blocking is off"
                    else -> "$political% political, $spam% spam ad"
                }
                allow(sbn, text, "Allowed ${text.sender} ($why): $snippet")
                return
            }
        }

        val label = if (category == Store.CATEGORY_SPAM) "spam ad" else "political"
        app.record("Blocked ${text.sender} (${(probability * 100).toInt()}% $label): $snippet")
        cancelNotification(sbn.key)
        archiveInSmsApp(notification)
        cancelOrphanedSummary(sbn)

        // SMS apps re-post the same notification several times; only act on the first sighting.
        if (!app.store.addSuppressed(text.sender, text.body, probability, keywords, sbn.packageName, category)) return

        // Google Messages has no archive action, so ArchiveService archives it through the UI.
        if (sbn.packageName == ArchiveService.MESSAGES && BlockerApp.isAutoArchiveOn(this) && ArchiveService.isEnabled(this)) {
            app.store.addPendingArchive(text.sender)
            ArchiveService.instance?.processQueue()
        }
    }

    /**
     * Clears our quiet-mode alert once the SMS app's notification goes away (read or dismissed).
     * Messages also removes and re-posts its notifications whenever it refreshes them, so only
     * clear ours if theirs hasn't come back shortly after.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!isSmsApp(sbn.packageName)) return
        handler.postDelayed({
            val back = try {
                activeNotifications.any { it.key == sbn.key }
            } catch (e: SecurityException) {
                false
            }
            if (!back) Alerts.cancel(this, sbn.key)
        }, REPOST_GRACE_MS)
    }

    /**
     * Lets the text through. If the SMS app's notifications are set to Silent ("quiet mode"),
     * nothing has alerted the user yet, so raise the alert ourselves, once per message.
     */
    private fun allow(sbn: StatusBarNotification, text: IncomingText, line: String) {
        val app = BlockerApp.get(this)
        app.record(line)

        val ranking = Ranking()
        if (!currentRanking.getRanking(sbn.key, ranking)) return
        val silenced = ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
        if (silenced != BlockerApp.isQuietMode(this)) {
            BlockerApp.setQuietMode(this, silenced)
            app.record(if (silenced) "Quiet mode on: SMS app is silent, this app alerts you" else "Quiet mode off: SMS app alerts you itself")
        }
        if (!silenced) return

        // Messages re-posts every conversation's notification whenever any text arrives;
        // only alert for messages that are new and that we haven't alerted for already.
        val messageTime = sbn.notification.`when`
        if (System.currentTimeMillis() - messageTime > FRESH_MS) return
        synchronized(alerted) {
            if (!alerted.add("${text.sender}|${text.body}")) return
            if (alerted.size > ALERTED_LIMIT) alerted.remove(alerted.first())
        }
        Alerts.show(this, sbn.key, text.sender, text.body, messageTime, sbn.notification.contentIntent, markReadAction(sbn.notification)?.actionIntent)
    }

    private fun isSmsApp(pkg: String): Boolean =
        pkg in KNOWN_SMS_APPS || pkg == Telephony.Sms.getDefaultSmsPackage(this)

    /** Pulls the latest incoming message out of a (usually MessagingStyle) SMS notification. */
    private fun extract(sbn: StatusBarNotification): IncomingText? {
        val notification = sbn.notification
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        if (style != null) {
            if (style.isGroupConversation) return null
            val message = style.messages.lastOrNull() ?: return null
            // A null person normally means the user's own message, but it's also how Messages shows a
            // text the user sent to their own number. Look for a matching incoming SMS in that case.
            val person = message.person ?: return fromInbox(sbn)
            val sender = person.name?.toString().orEmpty().ifBlank { style.conversationTitle?.toString().orEmpty() }
            if (sender.isBlank()) return fromInbox(sbn)
            val body = message.text?.toString() ?: return null
            return IncomingText(sender, person.uri, body)
        }

        val extras = notification.extras
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        if (sender.isBlank()) return fromInbox(sbn)
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return null
        return IncomingText(sender, null, body)
    }

    /** When the notification doesn't say who sent the text (redacted or self-sent), read it from the SMS inbox. */
    private fun fromInbox(sbn: StatusBarNotification): IncomingText? {
        // `when` is the message's own timestamp and survives re-posts; postTime changes every time
        // Messages refreshes its notifications, which would match the wrong (newest) text.
        val messageTime = sbn.notification.`when`.takeIf { it > 0 } ?: return null
        val sms = SmsReader.receivedNear(this, messageTime) ?: return null
        return IncomingText(sms.address, null, sms.body)
    }

    /** Uses the SMS app's own notification action so the thread leaves the inbox / unread count. */
    private fun archiveInSmsApp(notification: Notification) {
        val action = archiveAction(notification) ?: markReadAction(notification) ?: return
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

        // Buttons are matched by semantic action first: when Android redacts a notification it
        // blanks the button labels too, so "Mark as read" arrives as "".
        private fun buttons(notification: Notification) =
            notification.actions?.filter { it.remoteInputs.isNullOrEmpty() }.orEmpty()

        private fun archiveAction(notification: Notification): Notification.Action? =
            buttons(notification).firstOrNull {
                it.semanticAction == Notification.Action.SEMANTIC_ACTION_ARCHIVE ||
                    it.title?.toString()?.contains("archive", ignoreCase = true) == true
            }

        private fun markReadAction(notification: Notification): Notification.Action? {
            val buttons = buttons(notification)
            return buttons.firstOrNull {
                it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ ||
                    it.title?.toString()?.contains("read", ignoreCase = true) == true
            } ?: buttons.singleOrNull()?.takeIf { it.title.isNullOrBlank() } // redacted: the one non-reply button
        }
        private const val FRESH_MS = 2 * 60 * 1000L
        private const val REPOST_GRACE_MS = 3_000L
        private const val ALERTED_LIMIT = 200

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
