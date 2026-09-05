package com.beammental.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "beam_session")

/** Local session: JWT + profile flags. The token travels as a Bearer header. */
class Session(private val context: Context) {

    private val tokenKey = stringPreferencesKey("token")
    private val nameKey = stringPreferencesKey("name")
    private val onboardedKey = booleanPreferencesKey("onboarded")
    private val chatKey = stringPreferencesKey("chat_history")

    suspend fun token(): String? = context.dataStore.data.first()[tokenKey]

    suspend fun name(): String? = context.dataStore.data.first()[nameKey]

    suspend fun onboarded(): Boolean = context.dataStore.data.first()[onboardedKey] ?: false

    suspend fun chatHistory(): List<Pair<String, String>> {
        val raw = context.dataStore.data.first()[chatKey] ?: return emptyList()
        return raw.split("\u0001").mapNotNull { line ->
            val i = line.indexOf("\u0002")
            if (i <= 0) null else line.take(i) to line.substring(i + 1)
        }.takeLast(30)
    }

    suspend fun saveChatHistory(history: List<Pair<String, String>>) {
        context.dataStore.edit { prefs ->
            prefs[chatKey] = history.takeLast(30)
                .joinToString("\u0001") { "${it.first}\u0002${it.second}" }
        }
    }

    suspend fun saveAuth(token: String, name: String?) {
        context.dataStore.edit { prefs ->
            prefs[tokenKey] = token
            if (!name.isNullOrBlank()) prefs[nameKey] = name
        }
    }

    suspend fun setName(name: String) {
        context.dataStore.edit { it[nameKey] = name }
    }

    suspend fun setOnboarded() {
        context.dataStore.edit { it[onboardedKey] = true }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(tokenKey)
            prefs.remove(nameKey)
            prefs.remove(onboardedKey)
            prefs.remove(chatKey)
        }
    }
}
