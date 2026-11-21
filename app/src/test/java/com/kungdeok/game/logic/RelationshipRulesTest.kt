package com.kungdeok.game.logic

import com.kungdeok.game.data.GameEntity
import com.kungdeok.game.model.CatId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelationshipRulesTest {
    @Test fun decaysTwoPerDay() {
        val g = RelationshipRules.decay(GameEntity(friendship = 50, friendshipDay = 100), 103)
        assertEquals(44, g.friendship)
        assertEquals(103, g.friendshipDay)
    }

    @Test fun firstDayDoesNotDecay() {
        val g = RelationshipRules.decay(GameEntity(friendship = 50), 103)
        assertEquals(50, g.friendship)
    }

    @Test fun tenPetsInARowMakesOtherJealous() {
        var g = GameEntity()
        repeat(9) {
            val (ng, who) = RelationshipRules.onPet(g, CatId.KUNG)
            assertNull(who)
            g = ng
        }
        val (_, who) = RelationshipRules.onPet(g, CatId.KUNG)
        assertEquals(CatId.NODEOK, who)
    }

    @Test fun alternatingResetsStreak() {
        var g = GameEntity()
        repeat(9) { g = RelationshipRules.onPet(g, CatId.KUNG).first }
        g = RelationshipRules.onPet(g, CatId.NODEOK).first
        assertEquals(1, g.petStreak)
    }
}
