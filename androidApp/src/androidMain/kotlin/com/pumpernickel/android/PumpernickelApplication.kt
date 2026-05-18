package com.pumpernickel.android

import android.app.Application
import com.pumpernickel.data.geofence.DebugGeofenceProvider
import com.pumpernickel.di.GamificationStartup
import com.pumpernickel.di.initKoin
import com.pumpernickel.domain.geofence.GeofenceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

class PumpernickelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@PumpernickelApplication)
        }

        // DEBUG-only: override GeofenceProvider with a manual-trigger mock so Phase 19
        // flows can be exercised on the Emulator without real GPS. NEVER active in release.
        if (BuildConfig.DEBUG) {
            loadKoinModules(
                module {
                    single<GeofenceProvider> { DebugGeofenceProvider() }
                }
            )
        }

        // Gamification: seed achievement_state + run retroactive XP replay.
        // Idempotent — safe to call every launch.
        CoroutineScope(Dispatchers.IO).launch {
            val startup: GamificationStartup = GlobalContext.get().get()
            startup.run()
        }
    }
}
