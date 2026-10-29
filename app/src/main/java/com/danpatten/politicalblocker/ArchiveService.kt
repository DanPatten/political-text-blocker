package com.danpatten.politicalblocker

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Archives blocked conversations in Google Messages, which has no API or notification action
 * for it: opens the conversation list, long-presses the sender's row and taps Archive, the same
 * as a person would. Runs only while the phone is unlocked and the user isn't in Google Messages.
 */
class ArchiveService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var current: String? = null
    private var deadline = 0L

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = processQueue()
    }

    override fun onServiceConnected() {
        instance = this
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        processQueue()
    }

    override fun onDestroy() {
        instance = null
        unregisterReceiver(unlockReceiver)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Retry anything deferred because the user was in Google Messages, once they leave it.
        if (!running && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.packageName != MESSAGES) {
            processQueue()
        }
    }

    override fun onInterrupt() = Unit

    fun processQueue() {
        if (running || !BlockerApp.isAutoArchiveOn(this) || BlockerApp.get(this).store.pendingArchive().isEmpty()) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        if (keyguard.isKeyguardLocked || !power.isInteractive) return
        if (rootInActiveWindow?.packageName == MESSAGES) return

        val launch = packageManager.getLaunchIntentForPackage(MESSAGES) ?: return
        running = true
        current = null
        // CLEAR_TASK so Messages opens on its conversation list rather than a previously open thread.
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        handler.postDelayed(::step, STEP_MS)
    }

    private fun step() {
        val store = BlockerApp.get(this).store
        val sender = current ?: store.pendingArchive().firstOrNull()?.also {
            current = it
            deadline = System.currentTimeMillis() + SENDER_TIMEOUT_MS
        }
        if (sender == null) return finish()

        val root = rootInActiveWindow
        if (root?.packageName == MESSAGES) {
            val archive = root.findAccessibilityNodeInfosByViewId("$MESSAGES:id/action_archive").firstOrNull()
            if (archive != null) {
                // Selection mode is only entered by our own long-press, so this is the sender's row.
                archive.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                BlockerApp.get(this).record("Archived $sender in Google Messages")
                store.removePendingArchive(sender)
                current = null
                handler.postDelayed(::step, ARCHIVE_SETTLE_MS)
                return
            }
            val row = root.findAccessibilityNodeInfosByViewId("$MESSAGES:id/conversation_name")
                .firstOrNull { sameSender(it.text?.toString().orEmpty(), sender) }
                ?.let(::longClickableAncestor)
            if (row != null && row.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                handler.postDelayed(::step, STEP_MS)
                return
            }
        }

        if (System.currentTimeMillis() > deadline) {
            // Leave it unarchived (it was already marked read) rather than keep flashing Messages open.
            BlockerApp.get(this).record("Couldn't find $sender in Google Messages to archive it (left marked read)")
            store.removePendingArchive(sender)
            current = null
        }
        handler.postDelayed(::step, STEP_MS)
    }

    private fun finish() {
        // Back out of Messages to whatever the user was doing.
        if (rootInActiveWindow?.packageName == MESSAGES) performGlobalAction(GLOBAL_ACTION_BACK)
        running = false
        current = null
    }

    private fun longClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isLongClickable) n = n.parent
        return n
    }

    private fun sameSender(shown: String, sender: String): Boolean =
        if (ContactChecker.looksLikeNumber(shown) && ContactChecker.looksLikeNumber(sender)) {
            shown.filter { it.isDigit() }.takeLast(10) == sender.filter { it.isDigit() }.takeLast(10)
        } else {
            shown.trim().equals(sender.trim(), ignoreCase = true)
        }

    companion object {
        const val MESSAGES = "com.google.android.apps.messaging"
        private const val STEP_MS = 400L
        private const val ARCHIVE_SETTLE_MS = 800L
        private const val SENDER_TIMEOUT_MS = 6_000L

        @Volatile
        var instance: ArchiveService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(context, ArchiveService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
