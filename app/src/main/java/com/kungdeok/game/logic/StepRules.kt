package com.kungdeok.game.logic

import com.kungdeok.game.data.GameEntity

/** 걸음 수 (SPEC 5-1). TYPE_STEP_COUNTER 는 재부팅 후 누적 걸음이라 직전 값과의 차이를 더한다. */
object StepRules {
    const val STEPS_PER_REWARD = 100
    const val EXP_PER_REWARD = 2
    const val DAILY_EXP_CAP = 200
    const val GOAL_STEPS = 10_000
    const val GOAL_GOLD = 100

    data class Update(val game: GameEntity, val expForEach: Int, val goldBonus: Int)

    fun todaySteps(game: GameEntity, today: Long): Int = if (game.stepDay == today) game.todaySteps else 0

    fun onSensor(game: GameEntity, sensorValue: Long, today: Long): Update {
        var g = game
        if (g.stepDay != today) {
            g = g.copy(stepDay = today, todaySteps = 0, stepExpToday = 0, stepGoldGiven = false)
        }
        val delta = when {
            g.lastStepSensor < 0 -> 0L // 처음 읽은 값은 기준점으로만 쓴다
            sensorValue >= g.lastStepSensor -> sensorValue - g.lastStepSensor
            else -> sensorValue // 재부팅으로 센서가 0부터 다시 셈
        }
        g = g.copy(lastStepSensor = sensorValue, todaySteps = (g.todaySteps + delta).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

        val earned = minOf(DAILY_EXP_CAP, g.todaySteps / STEPS_PER_REWARD * EXP_PER_REWARD)
        val exp = (earned - g.stepExpToday).coerceAtLeast(0)
        g = g.copy(stepExpToday = g.stepExpToday + exp)

        var gold = 0
        if (g.todaySteps >= GOAL_STEPS && !g.stepGoldGiven) {
            gold = GOAL_GOLD
            g = g.copy(stepGoldGiven = true, gold = g.gold + gold)
        }
        return Update(g, exp, gold)
    }
}
