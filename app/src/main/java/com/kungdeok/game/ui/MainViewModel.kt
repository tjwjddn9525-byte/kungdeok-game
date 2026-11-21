package com.kungdeok.game.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kungdeok.game.KungdeokApp
import com.kungdeok.game.data.GameState
import com.kungdeok.game.logic.CareAction
import com.kungdeok.game.logic.Gift
import com.kungdeok.game.logic.BuyResult
import com.kungdeok.game.logic.ShopItem
import com.kungdeok.game.logic.CareResult
import com.kungdeok.game.model.CatId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as KungdeokApp).repository

    val state: StateFlow<GameState?> =
        repo.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 쿨다운 표시용 1초 시계 */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    init {
        // 화면이 떠 있는 동안 30초마다 시간 경과를 반영해 저장한다.
        viewModelScope.launch {
            while (true) {
                repo.tick()
                delay(30_000)
            }
        }
    }

    private val _evolution = MutableStateFlow<EvolutionEvent?>(null)
    val evolution: StateFlow<EvolutionEvent?> = _evolution

    fun evolve(id: CatId) {
        viewModelScope.launch {
            val (from, to) = repo.evolve(id) ?: return@launch
            _evolution.value = EvolutionEvent(id, from, to)
        }
    }

    fun evolutionShown() {
        _evolution.value = null
    }

    private val _fusion = MutableStateFlow<FusionEvent?>(null)
    val fusion: StateFlow<FusionEvent?> = _fusion

    fun fuse() {
        viewModelScope.launch {
            val first = repo.fuse() ?: return@launch
            _fusion.value = FusionEvent(firstTime = first)
        }
    }

    fun fusionShown() {
        _fusion.value = null
    }

    /** 열어 본 오늘의 선물 (닫으면 null) */
    private val _gift = MutableStateFlow<Gift?>(null)
    val gift: StateFlow<Gift?> = _gift

    fun today(): Long = repo.today()

    fun openGift() {
        viewModelScope.launch { _gift.value = repo.openDailyGift() }
    }

    fun closeGift() {
        _gift.value = null
    }

    // ---- 상점 (3단계) ----
    fun buy(item: ShopItem) {
        viewModelScope.launch {
            val msg = when (repo.buy(item)) {
                BuyResult.Ok -> "${item.icon} ${item.label}을(를) 샀어요!"
                BuyResult.NotEnoughGold -> "골드가 모자라요"
                BuyResult.AlreadyOwned -> "이미 가지고 있어요"
            }
            _messages.emit(msg)
        }
    }

    fun togglePlace(item: ShopItem) {
        viewModelScope.launch { repo.togglePlaced(item) }
    }

    fun toggleWear(cat: CatId, item: ShopItem) {
        viewModelScope.launch { repo.toggleAccessory(cat, item) }
    }

    fun useBag(cat: CatId, item: ShopItem) {
        viewModelScope.launch {
            val exp = repo.useBagItem(cat, item)
            _messages.emit(
                if (exp == null) "${cat.catName}에게 ${item.label}을(를) 줄 수 없어요 (없거나 자는 중)"
                else "${cat.catName}: ${item.icon} ${item.label}! (경험치 +${formatExp(exp)})",
            )
        }
    }

    fun care(targets: List<CatId>, action: CareAction) {
        viewModelScope.launch {
            val outcome = repo.care(targets, action)
            var text = outcome.results.entries.joinToString("\n") { (id, r) -> describe(id, action, r) }
            outcome.jealous?.let { text += "\n${it.catName}: 흥! (질투해서 기분 -5)" }
            _messages.emit(text)
        }
    }

    private fun describe(id: CatId, action: CareAction, r: CareResult): String = when (r) {
        is CareResult.Done -> buildString {
            append("${id.catName}: ${action.label}!")
            if (r.exp > 0) append(" (경험치 +${formatExp(r.exp)})")
        }
        is CareResult.Cooldown -> "${id.catName}: ${formatDuration(r.remainingMs)} 뒤에 할 수 있어요"
        CareResult.Asleep -> "${id.catName}은 자는 중이에요"
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
