package com.kungdeok.game.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.logic.BattleAction
import com.kungdeok.game.logic.BattleRules
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

/** 전투 무대 장면을 동영상으로 녹화해서 눈으로 확인 (snapshots/videos) */
class BattleMovie {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        environment = detectEnvironment().run { copy(compileSdkVersion = 34) },
    )

    private fun record(action: BattleAction, name: String, floor: Int = 3) {
        val k = CatEntity("kung", stage = 2, level = 8)
        val n = CatEntity("nodeok", stage = 3, level = 15)
        val start = BattleRules.start(floor, k, n)
        val b = BattleRules.act(start, action, Random(7))
        val view = ComposeView(paparazzi.context)
        view.setContent {
            KungdeokTheme {
                TowerScreen(state = com.kungdeok.game.data.GameState(k, n, com.kungdeok.game.data.GameEntity(snack = 2)),
                    now = 1000L, battle = b, reward = null, onStart = {}, onAction = {}, onLeave = {})
            }
        }
        paparazzi.gif(view, name = name, start = 0, end = 4000, fps = 10)
    }

    @Test fun attack() = record(BattleAction.ATTACK, "attack")
    @Test fun kungSkill() = record(BattleAction.KUNG_SKILL, "kungSkill")
    @Test fun nodeokSkill() = record(BattleAction.NODEOK_SKILL, "nodeokSkill")
}
