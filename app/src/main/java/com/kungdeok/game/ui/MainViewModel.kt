package com.kungdeok.game.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kungdeok.game.KungdeokApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as KungdeokApp).repository
    val controller: GameController = (app as KungdeokApp).controller

    init {
        // 화면이 떠 있는 동안 30초마다 시간 경과를 반영해 저장한다.
        viewModelScope.launch {
            while (true) {
                repo.tick()
                delay(30_000)
            }
        }
    }
}

fun formatExp(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)

fun formatDuration(ms: Long): String {
    val s = (ms + 999) / 1000
    return when {
        s >= 3600 -> "${s / 3600}시간 ${(s % 3600) / 60}분"
        s >= 60 -> "${s / 60}분"
        else -> "${s}초"
    }
}
