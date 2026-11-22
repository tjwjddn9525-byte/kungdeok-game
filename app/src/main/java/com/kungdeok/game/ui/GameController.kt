package com.kungdeok.game.ui

import com.kungdeok.game.data.GameRepository
import com.kungdeok.game.data.GameState
import com.kungdeok.game.logic.CareAction
import com.kungdeok.game.logic.Gift
import com.kungdeok.game.logic.BuyResult
import com.kungdeok.game.logic.Battle
import com.kungdeok.game.logic.BattleAction
import com.kungdeok.game.logic.BattleResult
import com.kungdeok.game.logic.BattleRules
import com.kungdeok.game.logic.Reward
import com.kungdeok.game.logic.ShopItem
import com.kungdeok.game.logic.ShopRules
import com.kungdeok.game.logic.CareResult
import com.kungdeok.game.model.CatId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 게임 화면이 쓰는 상태와 행동을 한곳에 모은 것.
 * 앱 화면(MainActivity)과 화면 위 고양이를 눌러 여는 창(오버레이 메뉴)이 같은 것을 함께 써서,
 * 한쪽에서 하던 전투나 선물 상자가 다른 쪽에서도 그대로 이어진다. 앱 전체에 하나만 있다.
 */
class GameController(
    private val repo: GameRepository,
    private val scope: CoroutineScope,
) {
    val state: StateFlow<GameState?> =
        repo.state.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** 쿨다운 표시용 1초 시계 */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private val _evolution = MutableStateFlow<EvolutionEvent?>(null)
    val evolution: StateFlow<EvolutionEvent?> = _evolution

    fun evolve(id: CatId) {
        scope.launch {
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
        scope.launch {
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
        scope.launch { _gift.value = repo.openDailyGift() }
    }

    fun closeGift() {
        _gift.value = null
    }

    // ---- 캣타워 (3단계) ----
    private val _battle = MutableStateFlow<Battle?>(null)
    val battle: StateFlow<Battle?> = _battle
    private val _reward = MutableStateFlow<Reward?>(null)
    val reward: StateFlow<Reward?> = _reward

    fun startBattle(floor: Int) {
        scope.launch {
            val (b, block) = repo.startBattle(floor)
            if (b != null) {
                _reward.value = null
                _battle.value = b.copy(id = System.nanoTime())
            } else if (block != null) {
                _messages.emit(block)
            }
        }
    }

    /** 이미 장면을 다 튼 턴 (탭을 옮겼다 와도 다시 틀지 않게) */
    private var playedKey = ""
    fun battlePlayed(b: Battle): Boolean = playedKey == "${b.id}/${b.turn}"
    fun markBattlePlayed() {
        _battle.value?.let { playedKey = "${it.id}/${it.turn}" }
    }

    fun battleAction(action: BattleAction) {
        scope.launch {
            val b = _battle.value ?: return@launch
            if (b.result != null) return@launch
            if (action == BattleAction.SNACK && !repo.consumeSnack()) return@launch
            val next = BattleRules.act(b, action)
            _battle.value = next
            if (next.result != null) _reward.value = repo.finishBattle(next)
        }
    }

    /** 끝난 전투를 닫거나, 진행 중인 전투에서 도망친다 (진 것으로 침). */
    fun leaveBattle() {
        scope.launch {
            val b = _battle.value ?: return@launch
            if (b.result == null) repo.finishBattle(b.copy(result = BattleResult.LOSE))
            _battle.value = null
            _reward.value = null
        }
    }

    // ---- 상점 (3단계) ----
    fun buy(item: ShopItem) {
        scope.launch {
            val msg = when (repo.buy(item)) {
                BuyResult.Ok -> "${item.icon} ${item.label}을(를) 샀어요!"
                BuyResult.NotEnoughGold -> "골드가 모자라요"
                BuyResult.AlreadyOwned -> "이미 가지고 있어요"
            }
            _messages.emit(msg)
        }
    }

    fun togglePlace(item: ShopItem) {
        scope.launch { repo.togglePlaced(item) }
    }

    fun toggleWear(cat: CatId, item: ShopItem) {
        scope.launch { repo.toggleAccessory(cat, item) }
    }

    fun useBag(cat: CatId, item: ShopItem) {
        scope.launch {
            val exp = repo.useBagItem(cat, item)
            if (exp != null) ShopRules.careActionOf(item)?.let { playCare(setOf(cat), it) }
            _messages.emit(
                if (exp == null) "${cat.catName}에게 ${item.label}을(를) 줄 수 없어요 (없거나 자는 중)"
                else "${cat.catName}: ${item.icon} ${item.label}! (경험치 +${formatExp(exp)})",
            )
        }
    }

    /** 방에서 틀어 줄 돌봄 장면 */
    private val _careAnim = MutableStateFlow<CareAnimEvent?>(null)
    val careAnim: StateFlow<CareAnimEvent?> = _careAnim

    private fun playCare(cats: Set<CatId>, action: CareAction) {
        if (cats.isEmpty()) return
        val now = System.currentTimeMillis()
        _careAnim.value = CareAnimEvent(System.nanoTime(), cats, action, now)
    }

    fun care(targets: List<CatId>, action: CareAction) {
        scope.launch {
            val outcome = repo.care(targets, action)
            playCare(outcome.results.filterValues { it is CareResult.Done }.keys, action)
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
