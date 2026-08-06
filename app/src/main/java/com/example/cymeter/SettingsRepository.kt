package com.example.cymeter

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val SPEED_THRESHOLD_KEY = floatPreferencesKey("speed_threshold_kmh")
        const val DEFAULT_SPEED_THRESHOLD_KMH = 5.0f
    }

    val speedThresholdFlow: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[SPEED_THRESHOLD_KEY] ?: DEFAULT_SPEED_THRESHOLD_KMH
        }

    suspend fun updateSpeedThreshold(thresholdKmh: Float) {
        context.dataStore.edit { preferences ->
            preferences[SPEED_THRESHOLD_KEY] = thresholdKmh
        }
    }
}
