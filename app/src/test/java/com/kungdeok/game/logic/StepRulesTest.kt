package com.kungdeok.game.logic

import com.kungdeok.game.data.GameEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class StepRulesTest {
    @Test fun firstReadingIsBaseline() {
        val u = StepRules.onSensor(GameEntity(), 5000, 10)
        assertEquals(0, u.game.todaySteps)
        assertEquals(0, u.expForEach)
    }

    @Test fun every100StepsGives2Exp() {
        var g = StepRules.onSensor(GameEntity(), 1000, 10).game
        val u = StepRules.onSensor(g, 1350, 10)
        assertEquals(350, u.game.todaySteps)
        assertEquals(6, u.expForEach)
        g = u.game
        val u2 = StepRules.onSensor(g, 1420, 10)
        assertEquals(2, u2.expForEach)
    }

    @Test fun dailyCapAndGoldBonus() {
        var g = StepRules.onSensor(GameEntity(), 0, 10).game
        val u = StepRules.onSensor(g, 12_000, 10)
        assertEquals(200, u.expForEach)
        assertEquals(100, u.goldBonus)
        val u2 = StepRules.onSensor(u.game, 13_000, 10)
        assertEquals(0, u2.expForEach)
        assertEquals(0, u2.goldBonus)
    }

    @Test fun newDayResetsAndRebootHandled() {
        var g = StepRules.onSensor(GameEntity(), 100, 10).game
        g = StepRules.onSensor(g, 600, 10).game
        val u = StepRules.onSensor(g, 50, 11) // 다음 날, 재부팅
        assertEquals(50, u.game.todaySteps)
        assertEquals(0, u.game.stepExpToday)
    }
}
