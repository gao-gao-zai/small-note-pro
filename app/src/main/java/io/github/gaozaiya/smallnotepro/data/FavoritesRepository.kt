package io.github.gaozaiya.smallnotepro.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 收藏管理仓库。
 *
 * 使用 DataStore 持久化收藏的文件 URI 集合。
 */
class FavoritesRepository(
    private val appContext: Context,
) {
    private val favoritesKey = stringSetPreferencesKey("favorites")

    val favorites: Flow<Set<String>> = appContext.appDataStore.data.map { preferences ->
        preferences[favoritesKey] ?: emptySet()
    }

    suspend fun add(uriString: String) {
        appContext.appDataStore.edit { preferences ->
            val current = preferences[favoritesKey] ?: emptySet()
            preferences[favoritesKey] = current + uriString
        }
    }

    suspend fun remove(uriString: String) {
        appContext.appDataStore.edit { preferences ->
            val current = preferences[favoritesKey] ?: emptySet()
            preferences[favoritesKey] = current - uriString
        }
    }

    suspend fun toggle(uriString: String, isFavorite: Boolean) {
        if (isFavorite) {
            add(uriString)
        } else {
            remove(uriString)
        }
    }
}
