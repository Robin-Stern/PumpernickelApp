package com.pumpernickel.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.data.db.getDatabaseBuilder
import com.pumpernickel.data.preferences.createDataStoreIos
import com.pumpernickel.domain.geofence.EarlyExitTracker
import com.pumpernickel.infrastructure.ai.SecureKeyStore
import com.pumpernickel.infrastructure.geofence.GeofenceProvider
import com.pumpernickel.infrastructure.geofence.IosGeofenceProvider
import com.pumpernickel.infrastructure.location.IosLocationProvider
import com.pumpernickel.infrastructure.location.LocationProvider
import com.pumpernickel.infrastructure.permissions.IosPermissionController
import com.pumpernickel.infrastructure.permissions.PermissionController
import com.pumpernickel.infrastructure.progresspic.BiometricGate
import com.pumpernickel.infrastructure.progresspic.PhotoCaptureLauncher
import com.pumpernickel.infrastructure.progresspic.PhotoVault
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
    // Phase 22 — OAuth browser bridge for Anthropic Pro/Max auth flow (D-22-01).
    single<com.pumpernickel.infrastructure.ai.OAuthBrowserLauncher> {
        com.pumpernickel.infrastructure.ai.OAuthBrowserLauncher()
    }
    // Phase 19 — geofence + permission stack (D-19-01, D-19-02, D-19-04, D-19-12)
    // PendingGeofenceExitStore is bound in SharedModule via SettingsRepository bind;
    // here we wire the iOS actuals that consume it.
    single<GeofenceProvider> { IosGeofenceProvider(get()) }    // takes PendingGeofenceExitStore
    single<PermissionController> { IosPermissionController() }
    single { EarlyExitTracker(get()) }   // takes EarlyExitBudgetStore (narrow port, D-20-05)

    // Async AI generation: notifications + BGTask-backed background work.
    single { com.pumpernickel.infrastructure.notification.NotificationService() }
    single { com.pumpernickel.infrastructure.notification.BackgroundTaskManager() }
}
