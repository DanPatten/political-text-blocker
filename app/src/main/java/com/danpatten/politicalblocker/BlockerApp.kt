package com.danpatten.politicalblocker

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log

class BlockerApp : Application() {

    val store: Store by lazy { Store(this) }

    override fun onCreate() {
        super.onCreate()
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

    /**
     * Texts a sample political message to [number] (the user's own) after a delay, so the user can
     * leave the app and watch it get blocked like a real one. Held by the Application, not the
     * Activity, so closing the app doesn't cancel it.
     */
    fun sendTestTextLater(number: String, delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val body = resources.getStringArray(R.array.test_messages).random()
            try {
                sms.sendMultipartTextMessage(number, null, sms.divideMessage(body), null, null)
                record("Test text sent to $number: \"${body.take(SNIPPET)}…\"")
            } catch (e: Exception) {
                record("Test text to $number failed: ${e.message}")
            }
        }, delayMs)
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
        private const val KEY_OWN_NUMBER = "own_number"
        private const val KEY_AUTO_ARCHIVE = "auto_archive"
        private const val KEY_QUIET_MODE = "quiet_mode"

        fun get(context: Context) = context.applicationContext as BlockerApp

        fun isEnabled(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
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

        fun ownNumber(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_OWN_NUMBER, "").orEmpty()

        fun setOwnNumber(context: Context, number: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_OWN_NUMBER, number).apply()
        }
    }
}
