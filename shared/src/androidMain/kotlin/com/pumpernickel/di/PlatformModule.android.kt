package com.pumpernickel.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.platform.createDataStoreAndroid
import com.pumpernickel.platform.getDatabaseBuilder
import com.pumpernickel.domain.ai.SecureKeyStore
import com.pumpernickel.domain.location.LocationProvider
import com.pumpernickel.domain.progresspic.BiometricGate
import com.pumpernickel.domain.progresspic.PhotoCaptureLauncher
import com.pumpernickel.domain.progresspic.PhotoVault
import com.pumpernickel.feature.location.AndroidLocationProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<RoomDatabase.Builder<AppDatabase>> { getDatabaseBuilder(androidContext()) }
    single<DataStore<Preferences>> { createDataStoreAndroid(get()) }
    single<LocationProvider> { AndroidLocationProvider(androidContext()) }
    // Phase 17 — actuals from 17-03 take Context (D-17-19).
    single<PhotoVault> { PhotoVault(androidContext()) }
    single<PhotoCaptureLauncher> { PhotoCaptureLauncher(androidContext()) }
    single<BiometricGate> { BiometricGate(androidContext()) }
    // Phase 18 — BYOK key store (REQ-AI-06).
    single<SecureKeyStore> { SecureKeyStore(androidContext()) }
}
