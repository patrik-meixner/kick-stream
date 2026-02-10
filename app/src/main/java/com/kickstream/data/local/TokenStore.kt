package com.kickstream.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "kick_tokens")

class TokenStore(private val context: Context) {

    // App-lifetime scope for non-blocking cache priming (replaces GlobalScope)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cachedAccessToken: String? = null

    init {
        // Prime the token cache from DataStore on creation (non-blocking)
        scope.launch {
            cachedAccessToken = context.dataStore.data.first()[ACCESS_TOKEN]
        }
    }

    companion object {
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val EXPIRES_AT = longPreferencesKey("expires_at")
    }

    val hasToken: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ACCESS_TOKEN] != null
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String?, expiresIn: Long) {
        cachedAccessToken = accessToken
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = accessToken
            refreshToken?.let { prefs[REFRESH_TOKEN] = it }
            prefs[EXPIRES_AT] = System.currentTimeMillis() + (expiresIn * 1000)
        }
    }

    suspend fun getAccessToken(): String? {
        val token = context.dataStore.data.first()[ACCESS_TOKEN]
        cachedAccessToken = token
        return token
    }

    fun getAccessTokenSync(): String? = cachedAccessToken

    suspend fun getRefreshToken(): String? =
        context.dataStore.data.first()[REFRESH_TOKEN]

    suspend fun isTokenExpired(): Boolean {
        val expiresAt = context.dataStore.data.first()[EXPIRES_AT] ?: return true
        return System.currentTimeMillis() >= expiresAt
    }

    suspend fun clear() {
        cachedAccessToken = null
        context.dataStore.edit { it.clear() }
    }
}
