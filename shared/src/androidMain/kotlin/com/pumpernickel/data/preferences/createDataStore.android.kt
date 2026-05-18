package com.pumpernickel.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

fun createDataStoreAndroid(context: Context): DataStore<Preferences> = createDataStore(
    producePath = {
        context.filesDir.resolve(DATA_STORE_FILE_NAME).absolutePath
    }
)
