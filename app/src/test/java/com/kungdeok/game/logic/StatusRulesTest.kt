package com.kungdeok.game.logic

import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.model.CatId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusRulesTest {
    private val kung = CatEntity(CatId.KUNG.id, hunger = 100.0, mood = 100.0, cleanliness = 100.0, sleepiness = 100.0)

    @Test fun decaysPerHour() {
        val c = StatusRules.advance(kung, 0, 2 * HOUR)
        assertEquals(90.0, c.hunger, 1e-6)
        assertEquals(94.0, c.mood, 1e-6)
        assertEquals(96.0, c.cleanliness, 1e-6)
        assertEquals(92.0, c.sleepiness, 1e-6)
    }

    @Test fun nodeokGetsSleepySlower() {
        val n = kung.copy(id = CatId.NODEOK.id)
        val c = StatusRules.advance(n, 0, 2 * HOUR)
        assertEquals(96.0, c.sleepiness, 1e-6)
    }

    @Test fun neverBelowFloorAfterLongAbsence() {
        val c = StatusRules.advance(kung, 0, 5 * DAY)
        assertEquals(10.0, c.hunger, 1e-6)
        assertEquals(10.0, c.mood, 1e-6)
        assertEquals(10.0, c.cleanliness, 1e-6)
        assertTrue(c.sleepiness >= 10.0)
    }

    @Test fun sleepRecoversSleepiness() {
        val c = StatusRules.advance(kung.copy(sleepiness = 30.0, sleepUntil = 10 * MINUTE), 0, 10 * MINUTE)
        assertEquals(60.0, c.sleepiness, 1e-6)
    }

    @Test fun fallsAsleepWhenTooSleepy() {
        val c = StatusRules.advance(kung.copy(sleepiness = 20.5), 0, 15 * MINUTE)
        assertTrue(c.sleepUntil > 0)
    }
}
