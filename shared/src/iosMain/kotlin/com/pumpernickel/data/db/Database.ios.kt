@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.pumpernickel.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSHomeDirectory

fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = NSHomeDirectory() + "/Documents/pumpernickel.db"
    return Room.databaseBuilder<AppDatabase>(dbFilePath)
}
