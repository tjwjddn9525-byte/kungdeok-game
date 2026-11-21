package com.kungdeok.game.overlay

import com.kungdeok.game.logic.Closeness
import com.kungdeok.game.logic.RelationshipRules
import com.kungdeok.game.model.CatId
import kotlin.math.abs
import kotlin.random.Random

/**
 * 사이좋음에 따른 화면 위 행동 (SPEC 7)을 지휘한다. 매 프레임 [step] 을 부른다.
 * - 80 이상: 서로 그루밍, 붙어서 같이 잠
 * - 40–79: 각자 돌아다니다 가끔 나란히 앉음
 * - 40 미만: 가끔 투닥거림(먼지구름), 떨어져서 잠
 */
class RelationshipDirector(
    private val kung: CatOverlay,
    private val nodeok: CatOverlay,
    private val screenWidth: () -> Int,
) {
    var friendship = 30
    private var timer = 12f

    fun step(dt: Float) {
        val w = screenWidth()
        if (w <= 0) return
        val closeness = RelationshipRules.closeness(friendship)

        // 서먹하면 떨어져서 잔다: 한 마리가 자는데 너무 가까우면 다른 쪽이 멀리 가서 잔다.
        if (closeness == Closeness.DISTANT) keepApartWhileSleeping(w)

        timer -= dt
        if (timer > 0) return
        timer = 20f + Random.nextFloat() * 25f
        if (kung.isBusy || nodeok.isBusy) return

        when (closeness) {
            Closeness.CLOSE -> if (Random.nextBoolean()) groom(w) else napTogether(w)
            Closeness.NORMAL -> if (Random.nextFloat() < 0.6f) sitSideBySide(w)
            Closeness.DISTANT -> if (Random.nextFloat() < 0.5f) scuffle(w)
        }
    }

    /** 만날 지점: 두 마리의 가운데. 자는 고양이가 있으면 그 옆. */
    private fun meetingPoint(): Float = when {
        kung.isAsleep -> kung.centerX
        nodeok.isAsleep -> nodeok.centerX
        else -> (kung.centerX + nodeok.centerX) / 2f
    }

    /** 가운데 [mid] 를 두고 왼쪽/오른쪽 자리. 원래 왼쪽에 있던 고양이가 왼쪽 자리로 간다. */
    private fun sides(mid: Float, gapFactor: Float): Map<CatId, Pair<Float, Boolean>> {
        val half = maxOf(kung.widthPx, nodeok.widthPx) * gapFactor
        val leftCat = if (kung.centerX <= nodeok.centerX) kung else nodeok
        val rightCat = if (leftCat === kung) nodeok else kung
        val sleeper = when {
            kung.isAsleep -> kung
            nodeok.isAsleep -> nodeok
            else -> null
        }
        // 자는 고양이는 제자리, 다른 쪽만 그 옆으로
        if (sleeper != null) {
            val awake = if (sleeper === kung) nodeok else kung
            val toRight = awake.centerX >= sleeper.centerX
            val ax = sleeper.centerX + (if (toRight) 1 else -1) * half * 2
            return mapOf(
                sleeper.cat to (sleeper.centerX to toRight),
                awake.cat to (ax to !toRight),
            )
        }
        return mapOf(
            leftCat.cat to (mid - half to true),
            rightCat.cat to (mid + half to false),
        )
    }

    private fun both(build: (CatOverlay, Float, Boolean) -> Array<Script>, mid: Float, gap: Float) {
        val pos = sides(mid, gap)
        for (c in listOf(kung, nodeok)) {
            val (cx, faceRight) = pos.getValue(c.cat)
            c.play(*build(c, c.windowXFor(cx), faceRight))
        }
    }

    private fun groom(w: Int) = both({ _, x, face ->
        arrayOf(Script(x, Behavior.SIT, 7f, faceRight = face, effect = Effect.HEARTS))
    }, meetingPoint(), 0.32f)

    private fun napTogether(w: Int) = both({ _, x, face ->
        arrayOf(Script(x, Behavior.NAP, 14f, faceRight = face))
    }, meetingPoint(), 0.30f)

    private fun sitSideBySide(w: Int) = both({ _, x, _ ->
        arrayOf(Script(x, Behavior.SIT, 6f, faceRight = Random.nextBoolean()))
    }, meetingPoint(), 0.42f)

    private fun scuffle(w: Int) {
        if (kung.isAsleep || nodeok.isAsleep) return
        both({ c, x, face ->
            // 다가가서 투닥투닥 → 서로 반대쪽 끝으로 흩어짐
            val away = if (face) w * 0.1f else w * 0.8f
            arrayOf(
                Script(x, Behavior.LOOK, 2.5f, faceRight = face, effect = Effect.DUST),
                Script(c.windowXFor(away), Behavior.SIT, 3f, faceRight = !face),
            )
        }, meetingPoint(), 0.28f)
    }

    private fun keepApartWhileSleeping(w: Int) {
        val sleeper = listOf(kung, nodeok).firstOrNull { it.behaviorNow == Behavior.NAP && !it.isBusy } ?: return
        val other = if (sleeper === kung) nodeok else kung
        if (other.isBusy || other.behaviorNow != Behavior.NAP) return
        if (abs(sleeper.centerX - other.centerX) > w * 0.35f) return
        val farX = if (sleeper.centerX < w / 2f) w * 0.85f else w * 0.15f
        other.play(Script(other.windowXFor(farX), Behavior.NAP, 10f))
    }
}
