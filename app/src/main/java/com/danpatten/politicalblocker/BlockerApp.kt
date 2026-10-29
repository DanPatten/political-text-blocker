package com.danpatten.politicalblocker

import android.app.Application
import android.content.Context

class BlockerApp : Application() {

    val store: Store by lazy { Store(this) }

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

    private fun train(): PoliticalClassifier {
        val corpus = assets.open("training.tsv").bufferedReader().use { it.readText() }
        return PoliticalClassifier(PoliticalClassifier.parseCorpus(corpus) + store.feedback())
    }

    companion object {
        const val ACTION_SUPPRESSED_CHANGED = "com.danpatten.politicalblocker.SUPPRESSED_CHANGED"
        private const val PREFS = "settings"
        private const val KEY_ENABLED = "enabled"

        fun get(context: Context) = context.applicationContext as BlockerApp

        fun isEnabled(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
    }
}
