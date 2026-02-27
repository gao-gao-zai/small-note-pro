package io.github.gaozaiya.smallnotepro.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val STORE_NAME = "small_note_pro"

/**
 * 应用级 DataStore 扩展属性。
 *
 * 提供全局唯一的 Preferences DataStore 实例，用于持久化应用设置。
 */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)
