package com.pumpernickel.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.data.db.getDatabaseBuilder
import com.pumpernickel.data.location.IosLocationProvider
import com.pumpernickel.data.preferences.createDataStoreIos
import com.pumpernickel.domain.ai.SecureKeyStore
import com.pumpernickel.domain.location.LocationProvider
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
}
