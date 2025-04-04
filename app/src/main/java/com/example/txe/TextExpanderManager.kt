package com.example.txe

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TextExpanderManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "text_expander_prefs"
        private const val KEY_EXPANDERS = "expanders"
    }

    fun saveExpander(expander: TextExpander) {
        val expanders = getExpanders().toMutableList()
        expanders.add(expander)
        saveExpanders(expanders)
    }

    fun removeExpander(expander: TextExpander) {
        val expanders = getExpanders().toMutableList()
        expanders.removeAll { it.key == expander.key }
        saveExpanders(expanders)
    }

    fun getExpanders(): List<TextExpander> {
        val json = prefs.getString(KEY_EXPANDERS, "[]")
        val type = object : TypeToken<List<TextExpander>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun saveExpanders(expanders: List<TextExpander>) {
        val json = gson.toJson(expanders)
        prefs.edit().putString(KEY_EXPANDERS, json).apply()
    }

    fun getExpanderMap(): Map<String, String> {
        return getExpanders().associate { it.key to it.value }
    }
} 