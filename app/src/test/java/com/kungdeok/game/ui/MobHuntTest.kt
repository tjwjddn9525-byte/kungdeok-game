package com.kungdeok.game.ui

import android.widget.LinearLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.logic.MobType
import com.kungdeok.game.model.CatId
import com.kungdeok.game.overlay.CatOverlay
import com.kungdeok.game.overlay.CatOverlayCallbacks
import com.kungdeok.game.overlay.MobManager
import com.kungdeok.game.overlay.MobOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 바탕화면 잡몹: 나타나기 → 톡 → 고양이가 달려가 덮침 → 보상 */
class MobHuntTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        environment = detectEnvironment().run { copy(compileSdkVersion = 34) },
    )

    private val noop = object : CatOverlayCallbacks {
        override fun onTap(cat: CatId) {}
        override fun onLongPress(cat: CatId) {}
    }

    private fun cats(): Map<CatId, CatOverlay> {
        val ctx = paparazzi.context
        val wm = ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        return CatId.entries.associateWith { id ->
            CatOverlay(ctx, wm, id, noop).apply {
                update(CatEntity(id.id, stage = 2), System.currentTimeMillis())
                setScreen(1080, 2200, true)
            }
        }
    }

    @Test fun tapMobAndCatCatchesIt() {
        val ctx = paparazzi.context
        val wm = ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val views = cats()
        var caught: Triple<CatId?, Int, Int>? = null
        val mgr = MobManager(ctx, wm, { views.map { (k, v) -> k to v } }) { c, e, g -> caught = Triple(c, e, g) }
        mgr.setScreen(1080, 2200)
        // 여러 번 해 봐서 모든 종류(나는 놈, 거미 포함)가 잡히는지 본다
        repeat(30) { round ->
            caught = null
            mgr.clear()
            mgr.debugSpawn()
            val mob = mgr.current
            assertNotNull(mob)
            repeat(40) { mgr.step(0.05f, true); views.values.forEach { it.step(0.05f) } } // 2초 돌아다님
            var taps = 0
            var t = 0f
            while (caught == null && t < 30f) {
                if (mgr.current?.state == MobOverlay.State.ROAM && taps < mob!!.type.hp + 2) {
                    mgr.debugTap(); taps++
                }
                mgr.step(0.05f, true)
                views.values.forEach { it.step(0.05f) }
                t += 0.05f
            }
            assertNotNull("round $round ${mob!!.type} 가 잡혀야 함", caught)
            assertEquals(mob.type.exp, caught!!.second)
            assertTrue(caught!!.third in mob.type.goldMin..mob.type.goldMax)
        }
    }

    @Test fun mobsLook() {
        val ctx = paparazzi.context
        val wm = ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val row = LinearLayout(ctx).apply { setBackgroundColor(android.graphics.Color.parseColor("#3A4A66")) }
        val d = ctx.resources.displayMetrics.density
        for (t in MobType.entries) {
            val m = MobOverlay(ctx, wm, t, 1080, 2200) {}
            m.step(0.1f)
            row.addView(m, LinearLayout.LayoutParams((110 * d / 1.6).toInt(), (96 * d).toInt()))
        }
        val defeated = MobOverlay(ctx, wm, MobType.MOUSE, 1080, 2200) {}
        defeated.hit(); defeated.showReward("+6 EXP  🪙4"); defeated.step(0.5f)
        row.addView(defeated, LinearLayout.LayoutParams((110 * d).toInt(), (96 * d).toInt()))
        paparazzi.snapshot(row)
    }

    @Test fun huntPoses() {
        val ctx = paparazzi.context
        val wm = ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val d = ctx.resources.displayMetrics.density
        val row = LinearLayout(ctx).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#3A4A66"))
            gravity = android.view.Gravity.BOTTOM
        }
        fun cat(script: com.kungdeok.game.overlay.Script, t: Float): CatOverlay =
            CatOverlay(ctx, wm, CatId.KUNG, noop).apply {
                update(CatEntity("kung", stage = 2), System.currentTimeMillis())
                setScreen(1080, 2200, true)
                play(script)
                repeat((t / 0.02f).toInt()) { step(0.02f) }
            }
        val crouch = cat(com.kungdeok.game.overlay.Script(null, com.kungdeok.game.overlay.Behavior.CROUCH, 1f, faceRight = true), 0.1f)
        val pounce = cat(com.kungdeok.game.overlay.Script(null, com.kungdeok.game.overlay.Behavior.POUNCE, 0.55f, faceRight = true), 0.33f)
        val cheer = CatOverlay(ctx, wm, CatId.KUNG, noop).apply {
            update(CatEntity("kung", stage = 2), System.currentTimeMillis()); setScreen(1080, 2200, true)
            celebrate(); repeat(6) { step(0.02f) }
        }
        val mob = MobOverlay(ctx, wm, MobType.MOUSE, 1080, 2200) {}.apply { step(0.1f); hit(); step(0.03f); step(0.03f) }
        val mob2 = MobOverlay(ctx, wm, MobType.CRICKET, 1080, 2200) {}.apply { step(0.1f); hit(); repeat(4) { step(0.03f) } }
        for (v in listOf(mob, mob2)) row.addView(v, LinearLayout.LayoutParams((80 * d).toInt(), (96 * d).toInt()))
        for (v in listOf(crouch, pounce, cheer)) row.addView(v, LinearLayout.LayoutParams((100 * d).toInt(), (130 * d).toInt()))
        paparazzi.snapshot(row)
    }
}
