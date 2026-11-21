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

    @Test fun overlayCats() {
        val ctx = paparazzi.context
        val wm = ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val cb = object : com.kungdeok.game.overlay.CatOverlayCallbacks {
            override fun onTap(cat: com.kungdeok.game.model.CatId) {}
            override fun onLongPress(cat: com.kungdeok.game.model.CatId) {}
        }
        val d = ctx.resources.displayMetrics.density
        val row = android.widget.LinearLayout(ctx).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#3A4A66"))
            gravity = android.view.Gravity.BOTTOM
        }
        val samples = listOf(
            CatEntity("kung", stage = 1, hunger = 20.0),
            CatEntity("nodeok", stage = 1, mood = 10.0, cleanliness = 10.0),
            CatEntity("kung", stage = 3),
            CatEntity("nodeok", stage = 5, sleepUntil = Long.MAX_VALUE),
        )
        for (c in samples) {
            val v = com.kungdeok.game.overlay.CatOverlay(ctx, wm, com.kungdeok.game.model.CatId.of(c.id), cb)
            v.update(c, 1000L)
            v.step(0.3f)
            row.addView(v, android.widget.LinearLayout.LayoutParams((100 * d).toInt(), (130 * d).toInt()))
        }
        paparazzi.snapshot(row)
    }
}
