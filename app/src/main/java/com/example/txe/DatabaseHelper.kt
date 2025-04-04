package com.example.txe

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "TxEDatabase"
        private const val DATABASE_VERSION = 1
        private const val TABLE_SHORTCUTS = "shortcuts"
        private const val COLUMN_ID = "id"
        private const val COLUMN_SHORTCUT = "shortcut"
        private const val COLUMN_VALUE = "value"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_SHORTCUTS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SHORTCUT TEXT UNIQUE NOT NULL,
                $COLUMN_VALUE TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SHORTCUTS")
        onCreate(db)
    }

    fun addShortcut(shortcut: String, value: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SHORTCUT, shortcut)
            put(COLUMN_VALUE, value)
        }
        return try {
            db.insertOrThrow(TABLE_SHORTCUTS, null, values)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getValue(shortcut: String): String? {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_SHORTCUTS,
            arrayOf(COLUMN_VALUE),
            "$COLUMN_SHORTCUT = ?",
            arrayOf(shortcut),
            null,
            null,
            null
        )
        return if (cursor.moveToFirst()) {
            cursor.getString(0)
        } else {
            null
        }
    }

    fun removeShortcut(shortcut: String): Boolean {
        val db = this.writableDatabase
        val result = db.delete(TABLE_SHORTCUTS, "$COLUMN_SHORTCUT = ?", arrayOf(shortcut))
        return result > 0
    }

    fun getAllShortcuts(): List<Pair<String, String>> {
        val shortcuts = mutableListOf<Pair<String, String>>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_SHORTCUTS,
            arrayOf(COLUMN_SHORTCUT, COLUMN_VALUE),
            null,
            null,
            null,
            null,
            "$COLUMN_SHORTCUT ASC"
        )
        
        while (cursor.moveToNext()) {
            shortcuts.add(Pair(cursor.getString(0), cursor.getString(1)))
        }
        cursor.close()
        return shortcuts
    }
} 