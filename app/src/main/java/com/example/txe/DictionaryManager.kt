package com.example.txe

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.provider.UserDictionary
import android.util.Log
import java.util.Locale
import java.io.StringReader
import java.io.StringWriter
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DictionaryManager(private val context: Context) {
    private val TAG = "DictionaryManager"

    private val dictionaryUri = UserDictionary.Words.CONTENT_URI
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun addWord(shortcut: String, value: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Adding word to dictionary: $shortcut -> $value")
            
            // Add to system dictionary
            val values = ContentValues().apply {
                put(UserDictionary.Words.SHORTCUT, shortcut)
                put(UserDictionary.Words.WORD, value) // Store the value as the word
                put(UserDictionary.Words.FREQUENCY, 100)
                put(UserDictionary.Words.LOCALE, "")
                put(UserDictionary.Words.APP_ID, 0)
            }

            // First remove any existing entry with the same value
            val selection = "${UserDictionary.Words.WORD} = ?"
            val selectionArgs = arrayOf(value)
            context.contentResolver.delete(UserDictionary.Words.CONTENT_URI, selection, selectionArgs)
            
            // Then insert the new entry
            val uri = context.contentResolver.insert(UserDictionary.Words.CONTENT_URI, values)
            Log.d(TAG, "Added word to system dictionary: $shortcut -> $value, URI: $uri")

            // Save to local storage
            val words = prefs.getStringSet(KEY_WORDS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            words.removeAll { it.startsWith("$shortcut:") } // Remove any existing entry
            words.add("$shortcut:$value")
            prefs.edit().putStringSet(KEY_WORDS, words).apply()
            Log.d(TAG, "Saved word to local storage: $shortcut -> $value")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding word", e)
            throw e
        }
    }

    suspend fun removeWord(shortcut: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Removing word with shortcut: $shortcut")
            
            // Remove from system dictionary using shortcut
            val selection = "${UserDictionary.Words.SHORTCUT} = ?"
            val selectionArgs = arrayOf(shortcut)
            val deletedRows = context.contentResolver.delete(UserDictionary.Words.CONTENT_URI, selection, selectionArgs)
            Log.d(TAG, "Removed $deletedRows entries from system dictionary")

            // Remove from local storage
            val words = prefs.getStringSet(KEY_WORDS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            words.removeAll { it.startsWith("$shortcut:") }
            prefs.edit().putStringSet(KEY_WORDS, words).apply()
            Log.d(TAG, "Removed word from local storage: $shortcut")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing word", e)
            throw e
        }
    }

    suspend fun getAllWords(): List<Expander> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting all words from dictionary")
            val words = mutableListOf<Expander>()

            // Get from system dictionary
            val projection = arrayOf(
                UserDictionary.Words.WORD,      // The expanded word
                UserDictionary.Words.SHORTCUT   // The shortcut
            )
            val selection = "${UserDictionary.Words.APP_ID} = ?"
            val selectionArgs = arrayOf("0")
            val cursor = context.contentResolver.query(
                UserDictionary.Words.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                val wordIndex = it.getColumnIndex(UserDictionary.Words.WORD)
                val shortcutIndex = it.getColumnIndex(UserDictionary.Words.SHORTCUT)

                while (it.moveToNext()) {
                    val word = it.getString(wordIndex)
                    val shortcut = it.getString(shortcutIndex)

                    Log.d(TAG, "Found entry - shortcut: $shortcut, word: $word")

                    // Only add entries that have both shortcut and word
                    if (!shortcut.isNullOrEmpty()) {
                        words.add(Expander(shortcut, word))
                    }
                }
            }

            Log.d(TAG, "Found ${words.size} word pairs in dictionary")
            words
        } catch (e: Exception) {
            Log.e(TAG, "Error getting words", e)
            emptyList()
        }
    }

    suspend fun syncFromGoogleSheets(expanders: List<Expander>) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to sync ${expanders.size} expanders from Google Sheets")
            
            // Clear existing words
            Log.d(TAG, "Clearing existing words from system dictionary")
            context.contentResolver.delete(UserDictionary.Words.CONTENT_URI, null, null)
            
            Log.d(TAG, "Clearing existing words from local storage")
            prefs.edit().clear().apply()

            // Add new words
            expanders.forEachIndexed { index, expander ->
                Log.d(TAG, "Adding expander ${index + 1}/${expanders.size}: ${expander.shortcut} -> ${expander.value}")
                addWord(expander.shortcut, expander.value)
            }

            Log.d(TAG, "Successfully synced ${expanders.size} words from Google Sheets")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing from Google Sheets", e)
            throw e
        }
    }

    companion object {
        private const val PREFS_NAME = "dictionary_prefs"
        private const val KEY_WORDS = "words"
    }
} 