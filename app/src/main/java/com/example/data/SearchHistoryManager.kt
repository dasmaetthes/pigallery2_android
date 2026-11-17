package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

val Context.dataStore by preferencesDataStore(name = "search_history")

class SearchHistoryManager(private val context: Context) {

    companion object {
        private val HISTORY_KEY = stringPreferencesKey("search_history_list")
        private const val MAX_HISTORY_SIZE = 10
    }

    val searchHistory: Flow<List<String>> = context.dataStore.data.map { preferences ->
        try {
            val jsonStr = preferences[HISTORY_KEY] ?: "[]"
            val array = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addSearchQuery(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return

        context.dataStore.edit { preferences ->
            val jsonStr = preferences[HISTORY_KEY] ?: "[]"
            val array = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            
            // Remove if exists to move to top
            list.remove(trimmedQuery)
            list.add(0, trimmedQuery)
            
            // Trim size
            while (list.size > MAX_HISTORY_SIZE) {
                list.removeAt(list.lastIndex)
            }
            
            preferences[HISTORY_KEY] = JSONArray(list).toString()
        }
    }
    
    suspend fun clearHistory() {
        context.dataStore.edit { preferences ->
            preferences.remove(HISTORY_KEY)
        }
    }
}
