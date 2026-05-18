package com.pumpernickel.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.data.db.getDatabaseBuilder
import com.pumpernickel.data.preferences.createDataStoreAndroid
import com.pumpernickel.domain.geofence.EarlyExitTracker
import com.pumpernickel.infrastructure.ai.SecureKeyStore
import com.pumpernickel.infrastructure.geofence.AndroidGeofenceProvider
import com.pumpernickel.infrastructure.geofence.GeofenceProvider
import com.pumpernickel.infrastructure.location.AndroidLocationProvider
import com.pumpernickel.infrastructure.location.LocationProvider
import com.pumpernickel.infrastructure.permissions.AndroidPermissionController
import com.pumpernickel.infrastructure.permissions.PermissionController
import com.pumpernickel.infrastructure.progresspic.BiometricGate
import com.pumpernickel.infrastructure.progresspic.PhotoCaptureLauncher
import com.pumpernickel.infrastructure.progresspic.PhotoVault
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<RoomDatabase.Builder<AppDatabase>> { getDatabaseBuilder(androidContext()) }
    single<DataStore<Preferences>> { createDataStoreAndroid(get()) }
    single<LocationProvider> { AndroidLocationProvider(androidContext()) }
    // Phase 19 — geofence + permission stack
    // PendingGeofenceExitStore is bound in SharedModule via SettingsRepositoryImpl's
    // multi-bind (Plan 20-05 / D-20-05); no Android-side override needed.
    single<GeofenceProvider> { AndroidGeofenceProvider(androidContext()) }
    single<PermissionController> { AndroidPermissionController(androidContext()) }
    single { EarlyExitTracker(get()) }   // takes EarlyExitBudgetStore (narrow port, D-20-05)
    // Phase 17 — actuals from 17-03 take Context (D-17-19).
    single<PhotoVault> { PhotoVault(androidContext()) }
    single<PhotoCaptureLauncher> { PhotoCaptureLauncher(androidContext()) }
    single<BiometricGate> { BiometricGate(androidContext()) }
    // Phase 18 — BYOK key store (REQ-AI-06).
    single<SecureKeyStore> { SecureKeyStore(androidContext()) }

    // Async AI generation: notifications + foreground-service-backed background work.
    single { com.pumpernickel.infrastructure.notification.NotificationService(androidContext()) }
    single { com.pumpernickel.infrastructure.notification.BackgroundTaskManager(androidContext()) }
}
