package com.danpatten.politicalblocker

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.material.color.DynamicColors

class BlockerApp : Application() {

    val store: Store by lazy { Store(this) }

    override fun onCreate() {
        super.onCreate()
        // Match the phone's wallpaper colours on Android 12+.
        DynamicColors.applyToActivitiesIfAvailable(this)
        Alerts.createChannel(this)
    }

    @Volatile
    private var cachedClassifier: PoliticalClassifier? = null

    /** The model is trained on first use from the bundled corpus plus the user's corrections. */
    val classifier: PoliticalClassifier
        get() = cachedClassifier ?: synchronized(this) {
            cachedClassifier ?: train().also { cachedClassifier = it }
        }

    /** Call after adding feedback so the next classification retrains with it. */
    fun invalidateClassifier() {
        cachedClassifier = null
    }

    /** Adds a line to the in-app "Recent activity" log and refreshes the screen if it's open. */
    fun record(line: String) {
        Log.i(TAG, line)
        store.addActivity(line)
        sendBroadcast(Intent(ACTION_CHANGED).setPackage(packageName))
    }

    private fun train(): PoliticalClassifier {
        val corpus = assets.open("training.tsv").bufferedReader().use { it.readText() }
        return PoliticalClassifier(PoliticalClassifier.parseCorpus(corpus) + store.feedback())
    }

    companion object {
        const val ACTION_CHANGED = "com.danpatten.politicalblocker.CHANGED"
        const val SNIPPET = 40
        private const val TAG = "PoliticalBlocker"
        private const val PREFS = "settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BLOCK_SPAM = "block_spam"
        private const val KEY_AUTO_ARCHIVE = "auto_archive"
        private const val KEY_QUIET_MODE = "quiet_mode"
        private const val KEY_RESTRICTED_INTRO = "restricted_intro_seen"

        fun get(context: Context) = context.applicationContext as BlockerApp

        /** Whether political texts are blocked. Keeps the pre-v1.3 "enabled" key so existing settings carry over. */
        fun isEnabled(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        }

        /** Whether commercial spam ads (sales, promos, loan offers) are blocked too. On by default. */
        fun isSpamBlockingOn(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BLOCK_SPAM, true)

        fun setSpamBlockingOn(context: Context, on: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_BLOCK_SPAM, on).apply()
        }

        fun hasSeenRestrictedIntro(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RESTRICTED_INTRO, false)

        fun setSeenRestrictedIntro(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_RESTRICTED_INTRO, true).apply()
        }

        /** Whether the SMS app's notifications were last seen set to Silent, so this app alerts instead. */
        fun isQuietMode(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_QUIET_MODE, false)

        fun setQuietMode(context: Context, on: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_QUIET_MODE, on).apply()
        }

        /** Off by default: the accessibility-driven archive needs the phone unlocked and opens Messages on screen. */
        fun isAutoArchiveOn(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_ARCHIVE, false)

        fun setAutoArchiveOn(context: Context, on: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_ARCHIVE, on).apply()
        }
    }
}
