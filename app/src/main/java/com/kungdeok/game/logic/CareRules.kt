package com.kungdeok.game.logic

import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.logic.StatusRules.clamp

/** 돌봄 행동 (SPEC 5). */
enum class CareAction(val label: String, val icon: String, val cooldown: Long, val baseExp: Int) {
    PET("쓰다듬기", "🤚", 3_000L, 1),
    FEED("밥 주기", "🍚", HOUR, 5),
    SNACK("간식 주기", "🍤", 30 * MINUTE, 3),
    PLAY("놀아주기", "🧶", 30 * MINUTE, 8),
    BRUSH("빗질", "🪮", 2 * HOUR, 5),
    SLEEP("재우기", "🌙", 0L, 0);
}

sealed interface CareResult {
    data class Done(val exp: Double) : CareResult
    data class Cooldown(val remainingMs: Long) : CareResult
    /** 잠든 동안에는 쓰다듬기·놀아주기를 할 수 없다. */
    data object Asleep : CareResult
}

object CareRules {
    fun lastDone(cat: CatEntity, action: CareAction): Long = when (action) {
        CareAction.PET -> cat.lastPetAt
        CareAction.FEED -> cat.lastFeedAt
        CareAction.SNACK -> cat.lastSnackAt
        CareAction.PLAY -> cat.lastPlayAt
        CareAction.BRUSH -> cat.lastBrushAt
        CareAction.SLEEP -> 0L
    }

    fun cooldownLeft(cat: CatEntity, action: CareAction, now: Long): Long {
        if (action == CareAction.SLEEP) return if (cat.isAsleep(now)) cat.sleepUntil - now else 0L
        val last = lastDone(cat, action)
        if (last == 0L) return 0L
        return maxOf(0L, last + action.cooldown - now)
    }

    fun blockedBySleep(action: CareAction): Boolean =
        action == CareAction.PET || action == CareAction.PLAY || action == CareAction.SLEEP

    /** 행동을 적용한 새 상태와 결과. 경험치는 [ExpRules] 가 따로 더한다. */
    fun apply(cat: CatEntity, action: CareAction, now: Long): Pair<CatEntity, CareResult> {
        if (cat.isAsleep(now) && blockedBySleep(action)) return cat to CareResult.Asleep
        val left = cooldownLeft(cat, action, now)
        if (left > 0) return cat to CareResult.Cooldown(left)
        val next = when (action) {
            CareAction.PET -> cat.copy(mood = clamp(cat.mood + 2), lastPetAt = now)
            CareAction.FEED -> cat.copy(hunger = clamp(cat.hunger + 40), lastFeedAt = now)
            CareAction.SNACK -> cat.copy(
                hunger = clamp(cat.hunger + 10), mood = clamp(cat.mood + 10), lastSnackAt = now,
            )
            CareAction.PLAY -> cat.copy(
                mood = clamp(cat.mood + 25), sleepiness = clamp(cat.sleepiness - 10), lastPlayAt = now,
            )
            CareAction.BRUSH -> cat.copy(
                cleanliness = clamp(cat.cleanliness + 40), mood = clamp(cat.mood + 5), lastBrushAt = now,
            )
            CareAction.SLEEP -> cat.copy(sleepUntil = now + StatusRules.SLEEP_DURATION)
        }
        return next to CareResult.Done(0.0)
    }
}
