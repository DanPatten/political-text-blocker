package com.danpatten.politicalblocker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import kotlin.math.abs

/**
 * Reads a text straight from the SMS inbox. Needed because Android 15+ can redact a notification's
 * sender and body ("Sensitive notification content hidden") before passing it to listener apps.
 */
object SmsReader {

    data class Sms(val address: String, val body: String)

    private const val WINDOW_MS = 30 * 1000L

    /** The inbox message received closest to [timeMs] (within 30 seconds), if any. */
    fun receivedNear(context: Context, timeMs: Long): Sms? {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return null
        return context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} BETWEEN ? AND ?",
            arrayOf((timeMs - WINDOW_MS).toString(), (timeMs + WINDOW_MS).toString()),
            null,
        )?.use { c ->
            var best: Sms? = null
            var bestDelta = Long.MAX_VALUE
            while (c.moveToNext()) {
                val address = c.getString(0) ?: continue
                val body = c.getString(1) ?: continue
                val delta = abs(c.getLong(2) - timeMs)
                if (delta < bestDelta) {
                    bestDelta = delta
                    best = Sms(address, body)
                }
            }
            best
        }
    }
}
