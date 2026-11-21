package com.kungdeok.game.logic

import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.model.CatId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthRulesTest {
    private val kung = CatEntity(CatId.KUNG.id)
    private val nodeok = CatEntity(CatId.NODEOK.id)

    @Test fun levelUpUses20TimesLevel() {
        val c = ExpRules.addExp(kung, 20.0 + 40.0 + 5.0)
        assertEquals(3, c.level)
        assertEquals(5.0, c.exp, 1e-6)
    }

    @Test fun personalityBonus() {
        assertEquals(12.0, ExpRules.gain(kung, CareAction.PLAY), 1e-6)
        assertEquals(8.0, ExpRules.gain(nodeok, CareAction.PLAY), 1e-6)
        assertEquals(4.5, ExpRules.gain(nodeok, CareAction.SNACK), 1e-6)
    }

    @Test fun hungryCatGetsHalfExp() {
        assertEquals(2.5, ExpRules.gain(kung.copy(hunger = 10.0), CareAction.FEED), 1e-6)
    }

    @Test fun cooldownBlocksRepeat() {
        val (fed, _) = CareRules.apply(kung, CareAction.FEED, 1_000_000)
        val (_, r) = CareRules.apply(fed, CareAction.FEED, 1_000_000 + 10 * MINUTE)
        assertTrue(r is CareResult.Cooldown)
    }

    @Test fun cannotPetSleepingCat() {
        val (_, r) = CareRules.apply(kung.copy(sleepUntil = 10_000), CareAction.PET, 5_000)
        assertEquals(CareResult.Asleep, r)
    }

    @Test fun evolutionConditions() = runBlocking {
        assertTrue(EvolutionRules.canEvolve(kung.copy(level = 5), 0) { 0.0 })
        assertFalse(EvolutionRules.canEvolve(kung.copy(level = 4), 0) { 100.0 })
        assertFalse(EvolutionRules.canEvolve(kung.copy(stage = 2, level = 15), 0) { 59.0 })
        assertTrue(EvolutionRules.canEvolve(kung.copy(stage = 2, level = 15), 0) { 60.0 })
        assertFalse(EvolutionRules.canEvolve(kung.copy(stage = 4, level = 50), 49) { 80.0 })
        assertTrue(EvolutionRules.canEvolve(kung.copy(stage = 4, level = 50), 50) { 80.0 })
        assertFalse(EvolutionRules.canEvolve(kung.copy(stage = 5, level = 99), 100) { 100.0 })
    }
}
