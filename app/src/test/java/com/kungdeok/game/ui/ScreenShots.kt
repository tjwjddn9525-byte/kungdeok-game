package com.kungdeok.game.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.data.GameEntity
import com.kungdeok.game.data.GameState
import org.junit.Rule
import org.junit.Test

class ScreenShots {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        environment = detectEnvironment().run {
            copy(compileSdkVersion = 34)
        },
    )

    private val state = GameState(
        CatEntity("kung", stage = 1, level = 3, exp = 12.0, hunger = 25.0),
        CatEntity("nodeok", stage = 2, level = 6, exp = 40.0, mood = 15.0, cleanliness = 10.0),
        GameEntity(gold = 120, friendship = 42),
    )

    @Test fun room() = paparazzi.snapshot {
        KungdeokTheme { RoomScreen(state, 1000L, Target.BOTH, {}, {}) }
    }

    @Test fun status() = paparazzi.snapshot {
        KungdeokTheme { StatusScreen(state, 1000L) }
    }

    @Test fun evolution() = paparazzi.snapshot {
        KungdeokTheme { EvolutionScreen(EvolutionEvent(com.kungdeok.game.model.CatId.NODEOK, 1, 2)) {} }
    }
}
