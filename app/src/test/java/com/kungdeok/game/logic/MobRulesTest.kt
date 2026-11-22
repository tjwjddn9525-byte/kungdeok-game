package com.kungdeok.game.logic

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MobRulesTest {
    @Test fun pickFollowsWeights() {
        val r = Random(1)
        val counts = (0 until 10_000).groupingBy { MobRules.pick(r) }.eachCount()
        assertTrue(counts.keys.containsAll(MobType.entries))
        // 생쥐(30)가 나비(5)보다 훨씬 자주
        assertTrue(counts.getValue(MobType.MOUSE) > counts.getValue(MobType.BUTTERFLY) * 4)
    }

    @Test fun goldInRange() {
        val r = Random(2)
        for (m in MobType.entries) repeat(100) {
            val g = MobRules.gold(m, r)
            assertTrue(g in m.goldMin..m.goldMax)
        }
    }

    @Test fun spawnDelays() {
        val r = Random(3)
        repeat(100) {
            assertTrue(MobRules.nextSpawnDelaySec(r) in 120f..300f)
            assertTrue(MobRules.firstSpawnDelaySec(r) in 30f..90f)
        }
    }
}
