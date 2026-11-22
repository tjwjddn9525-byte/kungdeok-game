package com.kungdeok.game.overlay

import android.content.Context
import android.view.WindowManager
import com.kungdeok.game.logic.MobRules
import com.kungdeok.game.model.CatId
import kotlin.math.abs
import kotlin.random.Random

/**
 * 바탕화면 잡몹 시스템.
 * 2~5분마다 잡몹이 한 마리 나타난다. 톡 하면 가장 가까운 깨어 있는 고양이가 달려가 덮쳐서 잡고,
 * 잡은 고양이가 경험치(와 약간의 골드)를 얻는다. 가만히 두면 고양이가 알아서 사냥하기도 하고, 40초 뒤엔 도망간다.
 */
class MobManager(
    private val context: Context,
    private val wm: WindowManager,
    /** 지금 화면에 있는 고양이들 (합체 중이면 쿵덕몬 하나). 짝: (고양이 id 또는 null=쿵덕몬, 화면 위 고양이) */
    private val hunters: () -> List<Pair<CatId?, CatOverlay>>,
    /** 잡았을 때 보상 저장 */
    private val onCaught: (CatId?, exp: Int, gold: Int) -> Unit,
) {
    private var mob: MobOverlay? = null
    private var spawnLeft = MobRules.firstSpawnDelaySec()
    private var autoCheckLeft = 5f
    private var hunter: Pair<CatId?, CatOverlay>? = null
    private var screenW = 0
    private var groundBottom = 0

    private val density = context.resources.displayMetrics.density

    fun setScreen(width: Int, bottom: Int) {
        val changed = width != screenW || bottom != groundBottom
        screenW = width
        groundBottom = bottom
        // 화면이 돌아가면 지금 있는 잡몹은 치운다
        if (changed) clear()
    }

    /** 숨기기·화면 꺼짐·끄기 때: 잡몹을 치우고 타이머를 처음부터 */
    fun clear() {
        mob?.detach()
        mob = null
        hunter = null
    }

    fun step(dt: Float, enabled: Boolean) {
        if (!enabled || screenW <= 0) {
            if (mob != null) clear()
            return
        }
        val m = mob
        if (m == null) {
            spawnLeft -= dt
            if (spawnLeft <= 0f) spawn()
            return
        }

        m.step(dt)
        if (m.state == MobOverlay.State.GONE) {
            clear()
            spawnLeft = MobRules.nextSpawnDelaySec()
            return
        }

        val h = hunter
        if (h != null) {
            val cat = h.second
            // 덮치는 중 몹 위에 내려앉으면 잡힘
            val reach = maxOf(cat.widthPx * 0.45f, 40f * density)
            if (cat.pouncing && cat.pounceT > 0.55f && abs(cat.centerX - m.centerX) < reach) {
                hunter = null
                cat.hitStop(0.09f)
                if (m.hit()) {
                    val gold = MobRules.gold(m.type)
                    m.showReward("+${m.type.exp} EXP  🪙$gold")
                    cat.celebrate()
                    cat.showTempBubble("잡았다!", 1.8f)
                    onCaught(h.first, m.type.exp, gold)
                } else {
                    cat.showTempBubble("놓쳤다!", 1.2f)
                }
            } else if (!cat.isBusy) {
                // 덮치기가 끝났는데 못 잡음 (드래그 등으로 끊김)
                hunter = null
                m.release()
            }
            return
        }

        // 아무도 안 잡으면 도망
        if (m.state == MobOverlay.State.ROAM && m.age > MobRules.LIFETIME_SEC) {
            m.escape()
            return
        }

        // 고양이 가까이 오면 고양이가 알아서 사냥에 나서기도 한다
        autoCheckLeft -= dt
        if (autoCheckLeft <= 0f) {
            autoCheckLeft = 5f
            val near = availableHunters().minByOrNull { abs(it.second.centerX - m.centerX) }
            if (near != null && abs(near.second.centerX - m.centerX) < 120f * density &&
                m.centerY > groundBottom - 200f * density && Random.nextFloat() < MobRules.AUTO_HUNT_CHANCE
            ) {
                startHunt(m, near)
            }
        }
    }

    /** 테스트용: 지금 있는 잡몹 */
    internal val current: MobOverlay? get() = mob

    /** 테스트용: 바로 한 마리 내보내기 / 톡 하기 */
    internal fun debugSpawn() = spawn()
    internal fun debugTap() = mob?.let { onTap(it) }

    private fun spawn() {
        val m = MobOverlay(context, wm, MobRules.pick(), screenW, groundBottom) { tapped -> onTap(tapped) }
        m.attach()
        mob = m
        autoCheckLeft = 5f
    }

    private fun availableHunters() = hunters().filter { (_, c) -> c.awake && !c.isBusy }

    /** 잡몹을 톡: 가장 가까운 깨어 있는 고양이가 덮치러 간다 */
    private fun onTap(m: MobOverlay) {
        if (hunter != null) return
        val h = availableHunters().minByOrNull { abs(it.second.centerX - m.centerX) }
        if (h == null) {
            m.showText("다들 자는 중…")
            return
        }
        startHunt(m, h)
    }

    private fun startHunt(m: MobOverlay, h: Pair<CatId?, CatOverlay>) {
        val cat = h.second
        m.startle()
        hunter = h
        val fromLeft = cat.centerX < m.centerX
        // 몹 바로 앞까지 전력 질주 → 폴짝 덮치기
        val standCx = m.centerX + (if (fromLeft) -1 else 1) * cat.widthPx * 0.15f
        cat.play(
            // 예비 동작: 몹 쪽을 보고 납작 웅크림 → 잔상 끌며 전력 질주 → 폴짝 덮치기
            Script(null, Behavior.CROUCH, 0.2f, faceRight = fromLeft),
            Script(cat.windowXFor(standCx), Behavior.POUNCE, 0.55f, faceRight = fromLeft, speedDp = 260f),
        )
    }
}
