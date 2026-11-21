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

    @Test fun roomFused() = paparazzi.snapshot {
        val fusedState = state.copy(game = state.game.copy(fusionUntil = 1000L + 20 * 60_000L))
        KungdeokTheme { RoomScreen(fusedState, 1000L, Target.BOTH, {}, {}) }
    }

    @Test fun roomDecorated() = paparazzi.snapshot {
        val decorated = state.copy(
            kung = state.kung.copy(accessory = "ribbon", hunger = 80.0),
            game = state.game.copy(
                ownedDecor = "box,cushion,picture,plant,ribbon,scratcher",
                placedDecor = "box,cushion,picture,plant,scratcher",
            ),
        )
        // now = 10분 칸 1번 → 노덕이 박스에 (노덕 기분이 낮으면 안 들어감 → 기분 올림)
        val s2 = decorated.copy(nodeok = decorated.nodeok.copy(mood = 80.0))
        KungdeokTheme { RoomScreen(s2, 10 * 60_000L + 1000L, Target.BOTH, {}, {}) }
    }

    @Test fun shop() = paparazzi.snapshot {
        val rich = state.copy(game = state.game.copy(gold = 300, ownedDecor = "box,ribbon", placedDecor = "box"))
        KungdeokTheme { ShopScreen(rich, {}, {}, { _, _ -> }) }
    }

    @Test fun towerLobby() = paparazzi.snapshot {
        KungdeokTheme { TowerScreen(state.copy(game = state.game.copy(towerFloor = 4)), 1000L, null, null, {}, {}, {}) }
    }

    @Test fun towerBattle() = paparazzi.snapshot {
        var b = com.kungdeok.game.logic.BattleRules.start(5, state.kung, state.nodeok)
        b = com.kungdeok.game.logic.BattleRules.act(b, com.kungdeok.game.logic.BattleAction.KUNG_SKILL, kotlin.random.Random(3))
        b = com.kungdeok.game.logic.BattleRules.act(b, com.kungdeok.game.logic.BattleAction.ATTACK, kotlin.random.Random(4))
        KungdeokTheme { TowerScreen(state, 1000L, b, null, {}, {}, {}) }
    }

    @Test fun dex() = paparazzi.snapshot {
        KungdeokTheme { DexScreen(state) }
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

    @Test fun overlayRelationshipScripts() {
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
        val effects = listOf(com.kungdeok.game.overlay.Effect.HEARTS, com.kungdeok.game.overlay.Effect.DUST)
        for ((i, e) in effects.withIndex()) {
            val id = if (i == 0) com.kungdeok.game.model.CatId.KUNG else com.kungdeok.game.model.CatId.NODEOK
            val v = com.kungdeok.game.overlay.CatOverlay(ctx, wm, id, cb)
            v.update(CatEntity(id.id, stage = 2), 1000L)
            v.setScreen(1080, 2000, true)
            val target = 100f
            v.play(com.kungdeok.game.overlay.Script(target, com.kungdeok.game.overlay.Behavior.SIT, 20f, true, e))
            repeat(420) { v.step(1f / 60f) } // 7초: 걸어가서 도착, 아직 자리 유지 중
            org.junit.Assert.assertEquals(target, v.windowXFor(v.centerX), 1f)
            row.addView(v, android.widget.LinearLayout.LayoutParams((140 * d).toInt(), (140 * d).toInt()))
        }
        paparazzi.snapshot(row)
    }

    @Test fun overlayHabits() {
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
        val habits = listOf(
            com.kungdeok.game.overlay.Behavior.BOX,
            com.kungdeok.game.overlay.Behavior.ZOOM,
            com.kungdeok.game.overlay.Behavior.KNEAD,
        )
        for ((i, h) in habits.withIndex()) {
            val id = if (i % 2 == 0) com.kungdeok.game.model.CatId.NODEOK else com.kungdeok.game.model.CatId.KUNG
            val v = com.kungdeok.game.overlay.CatOverlay(ctx, wm, id, cb)
            v.update(CatEntity(id.id, stage = 3, accessory = if (i == 0) "crown" else ""), 1000L)
            v.setScreen(1080, 2000, true)
            v.forceBehavior(h, 100f)
            if (h == com.kungdeok.game.overlay.Behavior.KNEAD) v.showTempBubble("꾹꾹", 5f)
            v.step(0.05f)
            row.addView(v, android.widget.LinearLayout.LayoutParams((140 * d).toInt(), (140 * d).toInt()))
        }
        paparazzi.snapshot(row)
    }
}
