package com.smallpig.iptv

import android.app.Application
import com.smallpig.iptv.data.AppDatabase
import com.smallpig.iptv.data.Repository
import com.smallpig.iptv.net.Fetcher

class App : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val repo: Repository by lazy { Repository(db, Fetcher()) }

    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
