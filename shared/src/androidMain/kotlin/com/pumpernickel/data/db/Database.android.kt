package com.pumpernickel.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val dbFile = context.getDatabasePath("pumpernickel.db")
    return Room.databaseBuilder<AppDatabase>(context, dbFile.absolutePath)
        .fallbackToDestructiveMigration(dropAllTables = true)
}
