package com.pumpernickel.di

import androidx.room.RoomDatabase
import com.pumpernickel.data.db.AppDatabase
import com.pumpernickel.data.db.getDatabaseBuilder
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<RoomDatabase.Builder<AppDatabase>> { getDatabaseBuilder() }
}
