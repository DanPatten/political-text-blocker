package com.danpatten.politicalblocker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Local SQLite store: suppressed messages, user corrections, and allow-listed senders. */
class Store(context: Context) : SQLiteOpenHelper(context, "blocker.db", null, 4) {

    data class Suppressed(
        val id: Long,
        val sender: String,
        val body: String,
        val timestamp: Long,
        val probability: Double,
        val keywords: String,
        val sourcePackage: String,
        /** [CATEGORY_POLITICAL] or [CATEGORY_SPAM]. */
        val category: String,
    ) {
        val isSpam: Boolean get() = category == CATEGORY_SPAM
    }

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
                category TEXT NOT NULL DEFAULT '$CATEGORY_POLITICAL',
                UNIQUE(sender, body))"""
        )
        db.execSQL("CREATE TABLE feedback (id INTEGER PRIMARY KEY AUTOINCREMENT, political INTEGER NOT NULL, body TEXT NOT NULL)")
        db.execSQL("CREATE TABLE allowed_senders (sender TEXT PRIMARY KEY)")
        createPendingArchive(db)
        createActivity(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createPendingArchive(db)
        if (oldVersion < 3) createActivity(db)
        // Everything blocked before v4 was political; that was the only category.
        if (oldVersion < 4) db.execSQL("ALTER TABLE suppressed ADD COLUMN category TEXT NOT NULL DEFAULT '$CATEGORY_POLITICAL'")
    }

    private fun createActivity(db: SQLiteDatabase) {
        // UNIQUE line: SMS apps re-post notifications constantly, so repeats just bump the timestamp.
        db.execSQL("CREATE TABLE activity (line TEXT PRIMARY KEY, timestamp INTEGER NOT NULL)")
    }

    fun addActivity(line: String) {
        writableDatabase.insertWithOnConflict("activity", null, ContentValues().apply {
            put("line", line)
            put("timestamp", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        writableDatabase.execSQL(
            "DELETE FROM activity WHERE line NOT IN (SELECT line FROM activity ORDER BY timestamp DESC LIMIT $ACTIVITY_LIMIT)"
        )
    }

    /** Most recent first. */
    fun activity(): List<Pair<Long, String>> =
        readableDatabase.rawQuery("SELECT timestamp, line FROM activity ORDER BY timestamp DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
        }

    private fun createPendingArchive(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE pending_archive (sender TEXT PRIMARY KEY)")
    }

    /** Queues a Google Messages conversation for [ArchiveService] to archive. */
    fun addPendingArchive(sender: String) {
        writableDatabase.insertWithOnConflict(
            "pending_archive", null, ContentValues().apply { put("sender", sender) }, SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun pendingArchive(): List<String> =
        readableDatabase.rawQuery("SELECT sender FROM pending_archive", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    fun removePendingArchive(sender: String) {
        writableDatabase.delete("pending_archive", "sender = ?", arrayOf(sender))
    }

    /** Returns false if this exact message was already recorded (SMS apps re-post notifications). */
    fun addSuppressed(sender: String, body: String, probability: Double, keywords: List<String>, pkg: String, category: String): Boolean {
        val values = ContentValues().apply {
            put("sender", sender)
            put("body", body)
            put("timestamp", System.currentTimeMillis())
            put("probability", probability)
            put("keywords", keywords.joinToString(", "))
            put("source_package", pkg)
            put("category", category)
        }
        return writableDatabase.insertWithOnConflict("suppressed", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun suppressed(): List<Suppressed> =
        readableDatabase.rawQuery(
            "SELECT id, sender, body, timestamp, probability, keywords, source_package, category FROM suppressed ORDER BY timestamp DESC",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Suppressed(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getDouble(4), c.getString(5), c.getString(6), c.getString(7)))
                }
            }
        }

    fun deleteSuppressed(id: Long) {
        writableDatabase.delete("suppressed", "id = ?", arrayOf(id.toString()))
    }

    fun clearSuppressed() {
        writableDatabase.delete("suppressed", null, null)
    }

    /** A correction from the user; [political] false means an ordinary text (neither political nor an ad). */
    fun addFeedback(political: Boolean, body: String) {
        writableDatabase.insert("feedback", null, ContentValues().apply {
            put("political", if (political) 1 else 0)
            put("body", body)
        })
    }

    fun feedback(): List<PoliticalClassifier.Example> =
        readableDatabase.rawQuery("SELECT political, body FROM feedback", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val label = if (c.getInt(0) == 1) PoliticalClassifier.Label.POLITICAL else PoliticalClassifier.Label.OTHER
                    add(PoliticalClassifier.Example(label, c.getString(1)))
                }
            }
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

    companion object {
        private const val ACTIVITY_LIMIT = 50
        const val CATEGORY_POLITICAL = "political"
        const val CATEGORY_SPAM = "spam"
    }
}
