package com.danpatten.politicalblocker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Local SQLite store: suppressed messages, user corrections, and allow-listed senders. */
class Store(context: Context) : SQLiteOpenHelper(context, "blocker.db", null, 1) {

    data class Suppressed(
        val id: Long,
        val sender: String,
        val body: String,
        val timestamp: Long,
        val probability: Double,
        val keywords: String,
        val sourcePackage: String,
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE suppressed (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT NOT NULL,
                body TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                probability REAL NOT NULL,
                keywords TEXT NOT NULL,
                source_package TEXT NOT NULL,
                UNIQUE(sender, body))"""
        )
        db.execSQL("CREATE TABLE feedback (id INTEGER PRIMARY KEY AUTOINCREMENT, political INTEGER NOT NULL, body TEXT NOT NULL)")
        db.execSQL("CREATE TABLE allowed_senders (sender TEXT PRIMARY KEY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Returns false if this exact message was already recorded (SMS apps re-post notifications). */
    fun addSuppressed(sender: String, body: String, probability: Double, keywords: List<String>, pkg: String): Boolean {
        val values = ContentValues().apply {
            put("sender", sender)
            put("body", body)
            put("timestamp", System.currentTimeMillis())
            put("probability", probability)
            put("keywords", keywords.joinToString(", "))
            put("source_package", pkg)
        }
        return writableDatabase.insertWithOnConflict("suppressed", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun suppressed(): List<Suppressed> =
        readableDatabase.rawQuery(
            "SELECT id, sender, body, timestamp, probability, keywords, source_package FROM suppressed ORDER BY timestamp DESC",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Suppressed(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getDouble(4), c.getString(5), c.getString(6)))
                }
            }
        }

    fun deleteSuppressed(id: Long) {
        writableDatabase.delete("suppressed", "id = ?", arrayOf(id.toString()))
    }

    fun clearSuppressed() {
        writableDatabase.delete("suppressed", null, null)
    }

    fun addFeedback(political: Boolean, body: String) {
        writableDatabase.insert("feedback", null, ContentValues().apply {
            put("political", if (political) 1 else 0)
            put("body", body)
        })
    }

    fun feedback(): List<PoliticalClassifier.Example> =
        readableDatabase.rawQuery("SELECT political, body FROM feedback", null).use { c ->
            buildList { while (c.moveToNext()) add(PoliticalClassifier.Example(c.getInt(0) == 1, c.getString(1))) }
        }

    fun allowSender(sender: String) {
        writableDatabase.insertWithOnConflict(
            "allowed_senders", null, ContentValues().apply { put("sender", normalizeSender(sender)) },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun isSenderAllowed(sender: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM allowed_senders WHERE sender = ?", arrayOf(normalizeSender(sender)))
            .use { it.moveToFirst() }

    private fun normalizeSender(sender: String): String {
        val digits = sender.filter { it.isDigit() }
        return if (ContactChecker.looksLikeNumber(sender)) digits.takeLast(10) else sender.trim().lowercase()
    }
}
