package com.example.snapgps

import android.app.Application
import com.example.snapgps.data.media.TempFiles
import com.example.snapgps.di.dataModule
import com.example.snapgps.di.presentationModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SnapGpsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SnapGpsApp)
            modules(dataModule, presentationModule)
        }
        // Remove leftovers from a capture interrupted by process death (TDD §29).
        get<TempFiles>().clearAll()
    }
}
