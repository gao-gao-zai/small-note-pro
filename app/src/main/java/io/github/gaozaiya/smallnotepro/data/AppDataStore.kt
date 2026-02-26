package io.github.gaozaiya.smallnotepro.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val STORE_NAME = "small_note_pro"

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)
