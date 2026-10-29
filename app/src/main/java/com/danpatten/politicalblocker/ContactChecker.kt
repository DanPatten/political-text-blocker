package com.danpatten.politicalblocker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

/** Decides whether a message sender is someone in the user's contacts. */
object ContactChecker {

    private val numberPattern = Regex("^[+\\d\\s().\\-]+$")

    /** Phone numbers and short codes, e.g. "(555) 123-4567", "+15551234567", "88022". */
    fun looksLikeNumber(text: String): Boolean =
        numberPattern.matches(text.trim()) && text.count { it.isDigit() } >= 3

    /**
     * @param sender what the SMS app displays (a contact name for known senders, else the number)
     * @param personUri the sender's Person URI from the notification, if the SMS app provided one
     */
    fun isKnownContact(context: Context, sender: String, personUri: String?): Boolean {
        if (personUri != null && personUri.startsWith("content://com.android.contacts")) return true

        val number = when {
            personUri != null && personUri.startsWith("tel:") -> Uri.decode(personUri.removePrefix("tel:"))
            looksLikeNumber(sender) -> sender
            else -> null
        }

        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            // Without contacts access, SMS apps still show a name only for saved contacts,
            // so treat any non-number sender as known rather than risk blocking a friend.
            return number == null
        }

        return if (number != null) hasPhoneNumber(context, number) else hasDisplayName(context, sender)
    }

    private fun hasPhoneNumber(context: Context, number: String): Boolean {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    }

    private fun hasDisplayName(context: Context, name: String): Boolean =
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            "${ContactsContract.Contacts.DISPLAY_NAME} = ? COLLATE NOCASE",
            arrayOf(name.trim()),
            null,
        )?.use { it.count > 0 } ?: false
}
