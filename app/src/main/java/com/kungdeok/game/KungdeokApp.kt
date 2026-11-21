package com.kungdeok.game

import android.app.Application
import com.kungdeok.game.data.AppDatabase
import com.kungdeok.game.data.GameRepository

class KungdeokApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy {
        GameRepository(database.dao()).apply {
            onEvolutionReady = { id, cat -> Notifications.evolutionReady(this@KungdeokApp, id, cat.stage) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
