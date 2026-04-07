package com.pumpernickel

import android.content.Context
import org.koin.core.context.GlobalContext

actual fun readResourceFile(fileName: String): String {
    val context = GlobalContext.get().get<Context>()
    return context.assets.open(fileName).bufferedReader().readText()
}
