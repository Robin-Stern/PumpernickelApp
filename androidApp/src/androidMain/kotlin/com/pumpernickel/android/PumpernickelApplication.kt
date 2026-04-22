package com.pumpernickel.android

import android.app.Application
import com.pumpernickel.di.GamificationStartup
import com.pumpernickel.di.initKoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext

class PumpernickelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@PumpernickelApplication)
        }
        // Gamification: seed achievement_state + run retroactive XP replay.
        // Idempotent — safe to call every launch.
        CoroutineScope(Dispatchers.IO).launch {
            val startup: GamificationStartup = GlobalContext.get().get()
            startup.run()
        }
    }
}
