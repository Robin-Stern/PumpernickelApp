package com.pumpernickel.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.data.repository.SettingsRepository
import com.pumpernickel.platform.createDataStoreAndroid
import com.pumpernickel.platform.getDatabaseBuilder
import com.pumpernickel.domain.ai.SecureKeyStore
import com.pumpernickel.domain.geofence.EarlyExitTracker
import com.pumpernickel.domain.geofence.GeofenceProvider
import com.pumpernickel.domain.geofence.PendingGeofenceExitStore
import com.pumpernickel.domain.location.LocationProvider
import com.pumpernickel.domain.permissions.PermissionController
import com.pumpernickel.domain.progresspic.BiometricGate
import com.pumpernickel.domain.progresspic.PhotoCaptureLauncher
import com.pumpernickel.domain.progresspic.PhotoVault
import com.pumpernickel.feature.geofence.AndroidGeofenceProvider
import com.pumpernickel.feature.location.AndroidLocationProvider
import com.pumpernickel.feature.permissions.AndroidPermissionController
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<RoomDatabase.Builder<AppDatabase>> { getDatabaseBuilder(androidContext()) }
    single<DataStore<Preferences>> { createDataStoreAndroid(get()) }
    single<LocationProvider> { AndroidLocationProvider(androidContext()) }
    // Phase 19 — geofence + permission stack
    // PendingGeofenceExitStore is implemented by SettingsRepository (Plan 01 design decision);
    // resolve from Koin graph here since SharedModule binds SettingsRepository as a concrete type.
    single<PendingGeofenceExitStore> { get<SettingsRepository>() }
    single<GeofenceProvider> { AndroidGeofenceProvider(androidContext()) }
    single<PermissionController> { AndroidPermissionController(androidContext()) }
    single { EarlyExitTracker(get()) }   // takes SettingsRepository (already in graph)
    // Phase 17 — actuals from 17-03 take Context (D-17-19).
    single<PhotoVault> { PhotoVault(androidContext()) }
    single<PhotoCaptureLauncher> { PhotoCaptureLauncher(androidContext()) }
    single<BiometricGate> { BiometricGate(androidContext()) }
    // Phase 18 — BYOK key store (REQ-AI-06).
    single<SecureKeyStore> { SecureKeyStore(androidContext()) }
}
