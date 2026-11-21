package com.kungdeok.game.logic

import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.model.CatId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BattleRulesTest {
    private fun cats(level: Int, stage: Int) =
        CatEntity("kung", level = level, stage = stage) to CatEntity("nodeok", level = level, stage = stage)

    /** 정해진 전략(기술 우선, 위험하면 간식)으로 끝까지 싸운다 */
    private fun fight(floor: Int, level: Int, stage: Int, seed: Int, snacks0: Int = 0): BattleResult {
        val (k, n) = cats(level, stage)
        var b = BattleRules.start(floor, k, n)
        var snacks = snacks0
        val r = Random(seed)
        while (b.result == null && b.turn < 200) {
            val low = listOf(b.kung, b.nodeok).any { it.alive && it.hp < it.maxHp * 0.3 }
            val a = when {
                low && BattleRules.canUse(b, BattleAction.SNACK, snacks) -> BattleAction.SNACK.also { snacks-- }
                BattleRules.canUse(b, BattleAction.KUNG_SKILL, snacks) -> BattleAction.KUNG_SKILL
                BattleRules.canUse(b, BattleAction.NODEOK_SKILL, snacks) -> BattleAction.NODEOK_SKILL
                else -> BattleAction.ATTACK
            }
            b = BattleRules.act(b, a, r)
        }
        return b.result!!
    }

    private fun winRate(floor: Int, level: Int, stage: Int) =
        (0 until 200).count { fight(floor, level, stage, it) == BattleResult.WIN } / 200.0

    @Test fun firstFloorIsEasyForBabies() {
        assertTrue(winRate(1, 1, 1) > 0.95)
    }

    @Test fun earlyBossNeedsSomeGrowth() {
        assertTrue("아기 때 5층 보스는 어려워야 함", winRate(5, 1, 1) < 0.3)
        assertTrue("성장기 레벨 8이면 5층 보스 가능", winRate(5, 8, 2) > 0.7)
    }

    @Test fun towerScalesWithEvolution() {
        assertTrue(winRate(10, 15, 3) > 0.6)
        assertTrue(winRate(30, 50, 5) > 0.6)
        assertTrue("궁극체도 60층은 힘들어야 함", winRate(60, 50, 5) < 0.3)
    }

    @Test fun nodeokSkillStunsEnemy() {
        val (k, n) = cats(10, 2)
        val b = BattleRules.act(BattleRules.start(20, k, n), BattleAction.NODEOK_SKILL, Random(1))
        assertTrue(b.enemyStunned)
        assertEquals(BattleRules.SKILL_COOLDOWN, b.nodeok.skillCooldown)
        val hpBefore = b.kung.hp to b.nodeok.hp
        val b2 = BattleRules.act(b, BattleAction.ATTACK, Random(2))
        assertEquals(hpBefore, b2.kung.hp to b2.nodeok.hp)
    }

    @Test fun replayRewardsHalf() {
        assertEquals(BattleRules.reward(3, 2).gold, 25)
        assertEquals(BattleRules.reward(3, 5).gold, 12)
        assertTrue(BattleRules.reward(5, 4).gold > BattleRules.reward(6, 5).gold)
    }
}
