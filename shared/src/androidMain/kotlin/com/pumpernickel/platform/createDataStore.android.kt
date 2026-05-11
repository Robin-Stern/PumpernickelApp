package com.pumpernickel.platform

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.pumpernickel.data.preferences.DATA_STORE_FILE_NAME
import com.pumpernickel.data.preferences.createDataStore

fun createDataStoreAndroid(context: Context): DataStore<Preferences> = createDataStore(
    producePath = {
        context.filesDir.resolve(DATA_STORE_FILE_NAME).absolutePath
    }
)
