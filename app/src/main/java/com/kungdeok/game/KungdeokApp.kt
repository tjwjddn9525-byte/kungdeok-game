package com.kungdeok.game

import android.app.Application
import com.kungdeok.game.data.AppDatabase
import com.kungdeok.game.data.GameRepository
import com.kungdeok.game.ui.GameController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KungdeokApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy {
        GameRepository(database.dao()).apply {
            onEvolutionReady = { id, cat -> Notifications.evolutionReady(this@KungdeokApp, id, cat.stage) }
        }
    }

    /** 앱이 살아 있는 동안 쓰는 코루틴 범위 (앱 화면과 오버레이 메뉴가 함께 씀) */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val controller by lazy { GameController(repository, appScope) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
