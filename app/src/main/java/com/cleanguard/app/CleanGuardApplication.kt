package com.cleanguard.app

import android.app.Application
import com.cleanguard.app.di.AppContainer

class CleanGuardApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
