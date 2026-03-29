package com.pumpernickel.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pumpernickel.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {
    private val weightUnitKey = stringPreferencesKey("weight_unit")

    val weightUnit: Flow<WeightUnit> = dataStore.data.map { preferences ->
        when (preferences[weightUnitKey]) {
            "LBS" -> WeightUnit.LBS
            else -> WeightUnit.KG
        }
    }

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { preferences ->
            preferences[weightUnitKey] = unit.name
        }
    }
}
