package com.kungdeok.game.data

import com.kungdeok.game.logic.CareAction
import com.kungdeok.game.logic.CareResult
import com.kungdeok.game.logic.CareRules
import com.kungdeok.game.logic.DAY
import com.kungdeok.game.logic.DailyRules
import com.kungdeok.game.logic.Gift
import com.kungdeok.game.logic.RelationshipRules
import com.kungdeok.game.logic.StepRules
import com.kungdeok.game.logic.epochDay
import com.kungdeok.game.logic.EvolutionRules
import com.kungdeok.game.logic.ExpRules
import com.kungdeok.game.logic.HOUR
import com.kungdeok.game.logic.StatusRules
import com.kungdeok.game.model.CatId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 돌봄 결과. [jealous] 는 질투해서 "흥" 한 고양이. */
data class CareOutcome(
    val results: Map<CatId, CareResult>,
    val jealous: CatId? = null,
)

data class GameState(
    val kung: CatEntity,
    val nodeok: CatEntity,
    val game: GameEntity,
) {
    fun cat(id: CatId): CatEntity = if (id == CatId.KUNG) kung else nodeok
    fun withCat(id: CatId, cat: CatEntity): GameState =
        if (id == CatId.KUNG) copy(kung = cat) else copy(nodeok = cat)
}

/**
 * 게임 상태의 유일한 창구. 모든 변경은 mutex 안에서 "시간 경과 반영 → 변경 → 저장" 순서로 한다.
 * 저장은 변경할 때마다 Room 에 바로 쓰므로 따로 저장 버튼이 없다 (자동 저장).
 */
class GameRepository(
    private val dao: GameDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** 진화 조건을 새로 채웠을 때 불린다 (알림 띄우기용). */
    var onEvolutionReady: (CatId, CatEntity) -> Unit = { _, _ -> }

    private val mutex = Mutex()

    val state: Flow<GameState> = combine(dao.observeCats(), dao.observeGame().filterNotNull()) { cats, game ->
        val byId = cats.associateBy { it.id }
        val kung = byId[CatId.KUNG.id]
        val nodeok = byId[CatId.NODEOK.id]
        if (kung == null || nodeok == null) null else GameState(kung, nodeok, game)
    }.filterNotNull()

    /** 처음 실행이면 기본 데이터를 만들고, 꺼 둔 동안의 시간을 반영한다. */
    suspend fun tick(): GameState = mutex.withLock {
        val s = checkEvolution(loadAndAdvance())
        save(s)
        s
    }

    /** mutex 안에서 호출. 현재 시각까지 시간 경과를 반영한 상태를 돌려준다 (저장은 안 함). */
    internal suspend fun loadAndAdvance(): GameState {
        val now = clock()
        var game = dao.game() ?: GameEntity(lastTickAt = now, lastSampleAt = now, createdAt = now)
        val cats = dao.cats().associateBy { it.id }
        var kung = cats[CatId.KUNG.id] ?: CatEntity(CatId.KUNG.id)
        var nodeok = cats[CatId.NODEOK.id] ?: CatEntity(CatId.NODEOK.id)

        var from = maxOf(game.lastTickAt, now - StatusRules.MAX_CATCH_UP)
        if (now <= from) return GameState(kung, nodeok, game.copy(lastTickAt = maxOf(game.lastTickAt, now)))

        // 1시간 경계마다 끊어서 계산하고, 경계마다 평균 상태를 기록한다.
        val samples = mutableListOf<ConditionSample>()
        var nextSample = maxOf(game.lastSampleAt + HOUR, from)
        while (from < now) {
            val until = minOf(now, nextSample)
            kung = StatusRules.advance(kung, from, until)
            nodeok = StatusRules.advance(nodeok, from, until)
            from = until
            if (until == nextSample) {
                samples += ConditionSample(catId = kung.id, at = until, value = kung.average)
                samples += ConditionSample(catId = nodeok.id, at = until, value = nodeok.average)
                game = game.copy(lastSampleAt = until)
                nextSample = until + HOUR
            }
        }
        if (samples.isNotEmpty()) {
            dao.insertSamples(samples)
            dao.pruneSamples(now - 8 * DAY)
        }
        game = RelationshipRules.decay(game.copy(lastTickAt = now), epochDay(now))
        return GameState(kung, nodeok, game)
    }

    /**
     * [targets] 에게 [action] 을 한다. 둘 다에게 같은 행동을 성공하면 사이좋음 +1 (SPEC 5).
     */
    suspend fun care(targets: List<CatId>, action: CareAction): CareOutcome = mutex.withLock {
        var s = loadAndAdvance()
        val now = clock()
        val results = linkedMapOf<CatId, CareResult>()
        var jealous: CatId? = null
        for (id in targets) {
            var (cat, result) = CareRules.apply(s.cat(id), action, now)
            if (result is CareResult.Done) {
                val exp = ExpRules.gain(s.cat(id), action)
                cat = ExpRules.addExp(cat, exp)
                result = CareResult.Done(exp)
                if (action == CareAction.PET && targets.size == 1) {
                    val (game, who) = RelationshipRules.onPet(s.game, id)
                    s = s.copy(game = game)
                    if (who != null) {
                        jealous = who
                        val other = s.cat(who)
                        s = s.withCat(who, other.copy(mood = StatusRules.clamp(other.mood - RelationshipRules.JEALOUS_MOOD)))
                    }
                }
            }
            s = s.withCat(id, cat)
            results[id] = result
        }
        if (targets.size == 2 && results.values.all { it is CareResult.Done }) {
            s = s.copy(game = s.game.copy(friendship = (s.game.friendship + 1).coerceAtMost(100)))
        }
        s = checkEvolution(s)
        save(s)
        CareOutcome(results, jealous)
    }

    /** 진화 조건을 확인해 evolutionReady 를 갱신하고, 새로 채웠으면 한 번만 알린다. */
    private suspend fun checkEvolution(state: GameState): GameState {
        var s = state
        for (id in CatId.entries) {
            val cat = s.cat(id)
            val ready = EvolutionRules.canEvolve(cat, s.game.friendship) { days -> averageCondition(cat, days) }
            var next = cat.copy(evolutionReady = ready)
            if (ready && !cat.evolutionNotified) {
                onEvolutionReady(id, next)
                next = next.copy(evolutionNotified = true)
            }
            s = s.withCat(id, next)
        }
        return s
    }

    /** 진화 버튼. 조건을 채운 상태면 한 단계 올리고 (이전 단계, 새 단계)를 돌려준다. */
    suspend fun evolve(id: CatId): Pair<Int, Int>? = mutex.withLock {
        var s = checkEvolution(loadAndAdvance())
        val cat = s.cat(id)
        if (!cat.evolutionReady || cat.stage >= EvolutionRules.MAX_STAGE) {
            save(s)
            return@withLock null
        }
        val evolved = cat.copy(stage = cat.stage + 1, evolutionReady = false, evolutionNotified = false)
        s = checkEvolution(s.withCat(id, evolved))
        save(s)
        cat.stage to evolved.stage
    }

    fun today(): Long = epochDay(clock())

    /** 걸음 센서 값을 반영한다. 100걸음마다 두 마리 모두 경험치 +2 (하루 최대 200). */
    suspend fun recordSteps(sensorValue: Long) = mutex.withLock {
        var s = loadAndAdvance()
        val u = StepRules.onSensor(s.game, sensorValue, today())
        s = s.copy(game = u.game)
        if (u.expForEach > 0) {
            for (id in CatId.entries) s = s.withCat(id, ExpRules.addExp(s.cat(id), u.expForEach.toDouble()))
        }
        s = checkEvolution(s)
        save(s)
    }

    /** 매일 선물 열기. 오늘 이미 받았으면 null. */
    suspend fun openDailyGift(): Gift? = mutex.withLock {
        var s = loadAndAdvance()
        val today = today()
        if (!DailyRules.giftAvailable(s.game, today)) return@withLock null
        val gift = DailyRules.rollGift()
        s = s.copy(game = DailyRules.apply(s.game, gift, today))
        save(s)
        gift
    }

    internal suspend fun save(s: GameState) {
        dao.upsertCats(listOf(s.kung, s.nodeok))
        dao.upsertGame(s.game)
    }

    /** 최근 [days]일 평균 상태. 기록이 없으면 현재 평균. */
    internal suspend fun averageCondition(cat: CatEntity, days: Int): Double =
        dao.averageSince(cat.id, clock() - days * DAY) ?: cat.average

    internal fun now(): Long = clock()

    internal suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock { block() }
}
