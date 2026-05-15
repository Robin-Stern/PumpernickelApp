package com.pumpernickel.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.data.db.getDatabaseBuilder
import com.pumpernickel.data.geofence.IosGeofenceProvider
import com.pumpernickel.data.location.IosLocationProvider
import com.pumpernickel.data.permissions.IosPermissionController
import com.pumpernickel.data.preferences.createDataStoreIos
import com.pumpernickel.domain.ai.SecureKeyStore
import com.pumpernickel.domain.geofence.EarlyExitTracker
import com.pumpernickel.domain.geofence.GeofenceProvider
import com.pumpernickel.domain.location.LocationProvider
import com.pumpernickel.domain.permissions.PermissionController
import com.pumpernickel.domain.progresspic.BiometricGate
import com.pumpernickel.domain.progresspic.PhotoCaptureLauncher
import com.pumpernickel.domain.progresspic.PhotoVault
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<RoomDatabase.Builder<AppDatabase>> { getDatabaseBuilder() }
    single<DataStore<Preferences>> { createDataStoreIos() }
    single<LocationProvider> { IosLocationProvider() }
    // Phase 17 — actuals from 17-04 use no-arg ctors (D-17-19).
    single<PhotoVault> { PhotoVault() }
    single<PhotoCaptureLauncher> { PhotoCaptureLauncher() }
    single<BiometricGate> { BiometricGate() }
    // Phase 18 — BYOK key store, no-arg ctor (REQ-AI-06).
    single<SecureKeyStore> { SecureKeyStore() }
    // Phase 19 — geofence + permission stack (D-19-01, D-19-02, D-19-04, D-19-12)
    // PendingGeofenceExitStore is bound in SharedModule via SettingsRepository bind;
    // here we wire the iOS actuals that consume it.
    single<GeofenceProvider> { IosGeofenceProvider(get()) }    // takes PendingGeofenceExitStore
    single<PermissionController> { IosPermissionController() }
    single { EarlyExitTracker(get()) }   // takes SettingsRepository (already in graph)
}
