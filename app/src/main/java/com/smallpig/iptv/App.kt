package com.smallpig.iptv

import android.app.Application
import com.smallpig.iptv.data.AppDatabase

class App : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }

    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
