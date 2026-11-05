package com.tvtools.app

import android.app.Application

class TvToolsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        com.tvtools.app.data.Diagnostics.install(this)
        AppServices.init(this)
    }

    companion object {
        lateinit var instance: TvToolsApp
            private set
    }
}
