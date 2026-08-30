package com.niklauncher.app

import android.app.Application
import com.niklauncher.app.data.AppContainer

class NikLauncherApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
