package com.kickstream.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore by preferencesDataStore(name = "kick_favorites")

/**
 * Local persistence for followed/favorited channel slugs.
 * Uses a separate DataStore file from tokens to keep concerns isolated.
 */
class FavoritesStore(private val context: Context) {

    companion object {
        private val FAVORITE_SLUGS = stringSetPreferencesKey("favorite_slugs")
    }

    val favoriteSlugs: Flow<Set<String>> = context.favoritesDataStore.data.map { prefs ->
        prefs[FAVORITE_SLUGS] ?: emptySet()
    }

    suspend fun addFavorite(slug: String) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[FAVORITE_SLUGS] ?: emptySet()
            prefs[FAVORITE_SLUGS] = current + slug
        }
    }

    suspend fun removeFavorite(slug: String) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[FAVORITE_SLUGS] ?: emptySet()
            prefs[FAVORITE_SLUGS] = current - slug
        }
    }

    suspend fun toggleFavorite(slug: String) {
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[FAVORITE_SLUGS] ?: emptySet()
            prefs[FAVORITE_SLUGS] = if (slug in current) current - slug else current + slug
        }
    }

    suspend fun isFavorite(slug: String): Boolean {
        val slugs = context.favoritesDataStore.data.first()[FAVORITE_SLUGS] ?: emptySet()
        return slug in slugs
    }
}
