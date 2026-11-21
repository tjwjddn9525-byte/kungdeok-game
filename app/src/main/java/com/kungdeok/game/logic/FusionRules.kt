package com.kungdeok.game.logic

import com.kungdeok.game.data.GameEntity
import com.kungdeok.game.data.GameState

enum class FusionBlock(val reason: String) {
    NOT_ULTIMATE("두 마리 모두 궁극체가 되어야 해요"),
    NOT_FRIENDS("사이좋음이 100이어야 해요"),
    ALREADY_TODAY("오늘은 이미 합체했어요. 내일 다시 할 수 있어요"),
    FUSED("지금 합체 중이에요"),
}

/** 합체 (SPEC 6-3) */
object FusionRules {
    const val DURATION = 30 * MINUTE

    fun block(state: GameState, now: Long, today: Long): FusionBlock? = when {
        state.game.isFused(now) -> FusionBlock.FUSED
        state.kung.stage < EvolutionRules.MAX_STAGE || state.nodeok.stage < EvolutionRules.MAX_STAGE ->
            FusionBlock.NOT_ULTIMATE
        state.game.friendship < 100 -> FusionBlock.NOT_FRIENDS
        state.game.lastFusionDay == today -> FusionBlock.ALREADY_TODAY
        else -> null
    }

    fun fuse(game: GameEntity, now: Long, today: Long): GameEntity =
        game.copy(fusionUntil = now + DURATION, lastFusionDay = today, fusionSeen = true)
}
