package com.pumpernickel.android

import android.app.Application
import com.pumpernickel.di.initKoin
import org.koin.android.ext.koin.androidContext

class PumpernickelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@PumpernickelApplication)
        }
    }
}
