package com.kungdeok.game.logic

import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.model.CatId

const val MINUTE = 60_000L
const val HOUR = 60 * MINUTE
const val DAY = 24 * HOUR

/** 상태 시스템과 시간 경과 계산 (SPEC 4-1). 순수 함수만 둔다. */
object StatusRules {
    /** 시간당 감소량 */
    const val HUNGER_PER_HOUR = 5.0
    const val MOOD_PER_HOUR = 3.0
    const val CLEAN_PER_HOUR = 2.0
    const val SLEEPY_PER_HOUR = 4.0
    /** 노덕은 졸림이 천천히 줄어든다 (SPEC 4-3). */
    const val NODEOK_SLEEPY_FACTOR = 0.5

    /** 시간이 지나도 이 아래로는 내려가지 않는다. 죽거나 도망가는 일은 없다. */
    const val FLOOR = 10.0
    /** "0에 가까움" 기준. 배고픔이면 경험치 절반, 졸림이면 잠들어 버림 등. */
    const val LOW = 20.0
    /** 오버레이 말풍선 기준 (SPEC 8). */
    const val BUBBLE = 30.0

    /** 재우기 한 번에 자는 시간과 잠든 동안 분당 졸림 회복량. */
    const val SLEEP_DURATION = 10 * MINUTE
    const val SLEEP_RECOVER_PER_MINUTE = 9.0

    /** 앱을 아주 오래 꺼 뒀으면 마지막 이만큼만 계산한다 (그 전에 이미 모든 수치가 바닥). */
    const val MAX_CATCH_UP = 8 * DAY

    fun sleepyPerHour(cat: CatId): Double =
        if (cat == CatId.NODEOK) SLEEPY_PER_HOUR * NODEOK_SLEEPY_FACTOR else SLEEPY_PER_HOUR

    private fun decay(value: Double, amount: Double): Double =
        if (value <= FLOOR) value else maxOf(FLOOR, value - amount)

    /**
     * [cat] 의 상태를 [from] 부터 [to] 까지 [step] 단위로 흘려 보낸다.
     * 잠든 동안에는 졸림이 회복되고, 졸림이 바닥나면 스스로 잠든다.
     */
    fun advance(cat: CatEntity, from: Long, to: Long): CatEntity {
        if (to <= from) return cat
        val id = CatId.of(cat.id)
        var c = cat
        var t = from
        while (t < to) {
            val next = minOf(to, t + MINUTE)
            val hours = (next - t).toDouble() / HOUR
            val asleep = c.isAsleep(t)
            c = c.copy(
                hunger = decay(c.hunger, HUNGER_PER_HOUR * hours),
                mood = decay(c.mood, MOOD_PER_HOUR * hours),
                cleanliness = decay(c.cleanliness, CLEAN_PER_HOUR * hours),
                sleepiness = if (asleep) {
                    minOf(100.0, c.sleepiness + SLEEP_RECOVER_PER_MINUTE * (next - t) / MINUTE)
                } else {
                    decay(c.sleepiness, sleepyPerHour(id) * hours)
                },
            )
            if (!c.isAsleep(next) && c.sleepiness < LOW) {
                c = c.copy(sleepUntil = next + SLEEP_DURATION)
            }
            t = next
        }
        return c
    }

    /** 상태가 낮을 때 머리 위 말풍선 문구 (SPEC 8). */
    fun bubble(cat: CatEntity, now: Long): String? = when {
        cat.isAsleep(now) -> "Zzz…"
        cat.hunger < BUBBLE -> "배고파"
        cat.mood < BUBBLE -> "놀아줘"
        cat.sleepiness < BUBBLE -> "졸려"
        else -> null
    }

    fun clamp(v: Double): Double = v.coerceIn(0.0, 100.0)
}
