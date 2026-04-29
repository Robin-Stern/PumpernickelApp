package com.pumpernickel.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.data.db.getDatabaseBuilder
import com.pumpernickel.data.location.AndroidLocationProvider
import com.pumpernickel.data.preferences.createDataStoreAndroid
import com.pumpernickel.domain.location.LocationProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<RoomDatabase.Builder<AppDatabase>> { getDatabaseBuilder(androidContext()) }
    single<DataStore<Preferences>> { createDataStoreAndroid(get()) }
    single<LocationProvider> { AndroidLocationProvider(androidContext()) }
}
