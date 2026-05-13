package com.pumpernickel.platform

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pumpernickel.data.db.AppDatabase

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val dbFile = context.getDatabasePath("pumpernickel.db")
    return Room.databaseBuilder<AppDatabase>(context, dbFile.absolutePath)
        .fallbackToDestructiveMigration(dropAllTables = true)
}
