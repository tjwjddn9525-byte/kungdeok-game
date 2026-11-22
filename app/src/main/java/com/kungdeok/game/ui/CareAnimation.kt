package com.kungdeok.game.ui

import com.kungdeok.game.logic.CareAction
import com.kungdeok.game.model.CatId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** 돌봄이 성공했을 때 방에서 틀어 줄 장면 하나. */
data class CareAnimEvent(
    val id: Long,
    val cats: Set<CatId>,
    val action: CareAction,
    val startedAt: Long,
)

/**
 * 고양이 그림의 자세. 값은 모두 그림 크기에 대한 비율 (dx 0.1 = 그림 폭의 10%).
 * dy 는 아래쪽이 +.
 */
data class CarePose(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val rot: Float = 0f,
    val sx: Float = 1f,
    val sy: Float = 1f,
    /** 반대쪽을 보게 좌우 반전 */
    val flip: Boolean = false,
)

/**
 * 장면에 같이 나오는 소품이나 글자 (밥그릇, 털실, "냠" 등).
 * x, y 는 그림 아래 가운데를 (0, 0) 으로 한 비율 좌표, y 는 위쪽이 -.
 */
data class CareProp(
    val text: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val rot: Float = 0f,
    val alpha: Float = 1f,
)

/**
 * 돌봄 장면 (밥 먹기, 간식 받아먹기, 털실 놀이, 빗질, 쓰다듬기, 잠들기).
 * 새 그림 없이, 지금 그림을 움직이고 이모지 소품을 곁들여서 만든다.
 * [p] 는 0 → 1 진행도. 모두 순수 함수라 테스트하기 쉽다.
 */
object CareAnimation {
    fun durationMs(action: CareAction): Int = when (action) {
        CareAction.PET -> 1300
        CareAction.FEED -> 3000
        CareAction.SNACK -> 2300
        CareAction.PLAY -> 3200
        CareAction.BRUSH -> 2700
        CareAction.SLEEP -> 2000
    }

    private fun seg(p: Float, a: Float, b: Float): Float = ((p - a) / (b - a)).coerceIn(0f, 1f)
    private fun ease(t: Float): Float = t * t * (3 - 2 * t)
    private fun fadeInOut(p: Float, inEnd: Float, outStart: Float): Float =
        minOf(seg(p, 0f, inEnd), 1f - seg(p, outStart, 1f))

    fun pose(action: CareAction, p: Float): CarePose = when (action) {
        CareAction.FEED -> {
            // 발 앞에 놓인 그릇에 고개를 파묻고 오물오물 (그림이 정면을 보므로 몸을 웅크리며 숙인다)
            val lean = ease(seg(p, 0.12f, 0.25f)) * (1 - ease(seg(p, 0.85f, 0.97f)))
            val munch = if (p in 0.25f..0.85f) abs(sin(p * 70f)) else 0f
            CarePose(
                dy = 0.07f * lean + 0.03f * munch,
                rot = 3f * sin(p * 23f) * lean,
                sy = 1f - 0.14f * lean - 0.04f * munch,
                sx = 1f + 0.05f * lean,
            )
        }
        CareAction.SNACK -> {
            // 날아오는 간식을 점프해서 덥석
            val jump = sin(seg(p, 0.28f, 0.48f) * PI.toFloat())
            val munch = if (p in 0.5f..0.85f) abs(sin(p * 60f)) else 0f
            val land = if (p in 0.48f..0.55f) 1f - seg(p, 0.48f, 0.55f) else 0f
            CarePose(
                dy = -0.28f * jump + 0.02f * munch,
                sy = 1f - 0.06f * munch - 0.12f * land,
                sx = 1f + 0.08f * land,
                rot = -6f * jump,
            )
        }
        CareAction.PLAY -> {
            // 털실을 쫓아 좌우로 뛰다가 마지막에 덮친다
            val chase = seg(p, 0.05f, 0.8f)
            val ballX = playBallX(p)
            val x = if (p < 0.8f) ballX * 0.55f * ease(seg(p, 0f, 0.15f)) else ballX * 0.55f + (ballX - ballX * 0.55f) * ease(seg(p, 0.8f, 0.9f))
            val v = playBallX(p + 0.01f) - ballX
            val hop = if (chase > 0f && p < 0.8f) abs(sin(p * 34f)) * 0.07f else 0f
            val pounce = sin(seg(p, 0.8f, 0.92f) * PI.toFloat())
            val squash = if (p in 0.92f..0.98f) 1f - seg(p, 0.92f, 0.98f) else 0f
            val back = ease(seg(p, 0.95f, 1f))
            CarePose(
                dx = x * (1 - back),
                dy = -hop - 0.25f * pounce,
                rot = if (v > 0) 6f else -6f,
                sy = 1f - 0.15f * squash,
                sx = 1f + 0.1f * squash,
                flip = v > 0,
            )
        }
        CareAction.BRUSH -> {
            // 눈을 가늘게 뜨고 빗질에 몸을 맡긴다
            val on = fadeInOut(p, 0.12f, 0.88f)
            val sway = sin(p * 2 * PI.toFloat() * 3f)
            CarePose(rot = 4f * sway * on, sy = 1f - 0.04f * on, sx = 1f + 0.02f * on, dx = 0.02f * sway * on)
        }
        CareAction.PET -> {
            // 토닥토닥 두 번 찌그러짐
            val pat = maxOf(bump(p, 0.25f), bump(p, 0.55f))
            CarePose(sy = 1f - 0.12f * pat, sx = 1f + 0.06f * pat, dy = 0.02f * pat)
        }
        CareAction.SLEEP -> {
            // 몸을 웅크리고 옆으로 기운다
            val curl = ease(seg(p, 0.1f, 0.6f))
            CarePose(rot = -12f * curl, sy = 1f - 0.16f * curl, sx = 1f + 0.06f * curl, dy = 0.04f * curl)
        }
    }

    /** [center] 근처에서 잠깐 1로 솟는 혹 모양 */
    private fun bump(p: Float, center: Float, width: Float = 0.09f): Float {
        val t = 1f - abs(p - center) / width
        return t.coerceIn(0f, 1f)
    }

    /** 털실 위치: 좌우로 굴러다니다 0.8 에 멈춘다 */
    private fun playBallX(p: Float): Float {
        val t = minOf(p, 0.8f)
        return 0.55f * sin(t / 0.8f * 2f * PI.toFloat() * 1.25f)
    }

    fun props(action: CareAction, p: Float): List<CareProp> = when (action) {
        CareAction.FEED -> buildList {
            val slide = ease(seg(p, 0f, 0.15f))
            val fade = 1f - seg(p, 0.88f, 1f)
            // 그릇이 오른쪽에서 미끄러져 들어와 발 앞에 놓인다
            add(CareProp("🍚", 1.1f - 1.1f * slide, 0.08f, 0.42f, alpha = fade))
            if (p in 0.25f..0.88f) {
                // "냠" 이 차례로 떠오른다
                for (i in 0 until 4) {
                    val t = seg(p, 0.25f + i * 0.15f, 0.45f + i * 0.15f)
                    if (t in 0.01f..0.99f) {
                        add(CareProp(if (i % 2 == 0) "냠" else "쩝", 0.42f - 0.84f * (i % 2), -0.45f - 0.4f * t, 0.2f, alpha = 1f - t))
                    }
                }
                // 밥알 부스러기가 튄다
                for (i in 0 until 3) {
                    val t = (p * 7f + i / 3f) % 1f
                    val dir = if (i % 2 == 0) 1f else -1f
                    add(CareProp("·", dir * (0.1f + 0.25f * t), -0.2f - 0.25f * sin(t * PI.toFloat()), 0.16f, alpha = 1f - t))
                }
            }
            if (p > 0.86f) add(CareProp("😋", 0.3f, -0.95f - 0.1f * seg(p, 0.86f, 1f), 0.2f, alpha = fade.coerceAtLeast(0.2f)))
        }
        CareAction.SNACK -> buildList {
            // 왼쪽 위에서 포물선으로 날아와 입으로 쏙
            if (p < 0.46f) {
                val t = seg(p, 0f, 0.46f)
                val x = -0.9f + 0.9f * t
                val y = -1.15f + 0.65f * t - 0.45f * sin(t * PI.toFloat())
                add(CareProp("🍤", x, y, 0.24f, rot = 360f * t))
            }
            if (p in 0.48f..0.9f) {
                val t = seg(p, 0.48f, 0.9f)
                add(CareProp("냠!", 0.4f, -0.75f - 0.3f * t, 0.18f, alpha = 1f - t))
                add(CareProp("✨", -0.35f, -0.6f - 0.2f * t, 0.16f, alpha = 1f - t))
            }
            if (p > 0.85f) add(CareProp("❤", 0f, -1.0f - 0.2f * seg(p, 0.85f, 1f), 0.18f, alpha = 1f - seg(p, 0.85f, 1f)))
        }
        CareAction.PLAY -> buildList {
            val fade = 1f - seg(p, 0.93f, 1f)
            val x = playBallX(p)
            // 털실은 굴러가며 돈다. 덮치면 납작
            val squashed = p > 0.9f
            add(CareProp("🧶", x, 0f, if (squashed) 0.2f else 0.24f, rot = x * 720f, alpha = fade))
            if (p in 0.8f..0.97f) {
                val t = seg(p, 0.8f, 0.97f)
                add(CareProp("냥!", x * 0.55f + 0.3f, -1.0f - 0.2f * t, 0.18f, alpha = 1f - t))
            }
            if (p in 0.1f..0.8f) {
                val t = (p * 5f) % 1f
                add(CareProp("💨", -0.4f * (if (playBallX(p + 0.01f) > x) 1 else -1) + x * 0.55f, -0.05f, 0.12f, alpha = 0.6f * (1 - t)))
            }
        }
        CareAction.BRUSH -> buildList {
            val on = fadeInOut(p, 0.1f, 0.9f)
            val stroke = sin(p * 2 * PI.toFloat() * 3f)
            add(CareProp("🪮", 0.35f * stroke, -0.62f + 0.05f * abs(stroke), 0.26f, rot = -30f + 25f * stroke, alpha = on))
            for (i in 0 until 3) {
                val t = (p * 3f + i / 3f) % 1f
                add(CareProp("✨", -0.3f + 0.3f * i, -0.75f - 0.25f * t, 0.12f, alpha = on * (1 - t)))
            }
            if (p in 0.4f..0.95f) {
                val t = seg(p, 0.4f, 0.95f)
                add(CareProp("골골…", 0.45f, -0.95f - 0.15f * t, 0.14f, alpha = 1f - t))
            }
        }
        CareAction.PET -> buildList {
            val down = ease(seg(p, 0f, 0.2f)) * (1 - ease(seg(p, 0.75f, 0.95f)))
            val pat = maxOf(bump(p, 0.25f), bump(p, 0.55f))
            add(CareProp("🤚", 0f, -1.35f + 0.28f * down + 0.08f * pat, 0.26f, rot = 180f, alpha = down))
            if (p > 0.6f) {
                val t = seg(p, 0.6f, 1f)
                add(CareProp("❤", 0.25f, -0.85f - 0.35f * t, 0.16f, alpha = 1f - t))
                add(CareProp("❤", -0.2f, -0.75f - 0.3f * t, 0.12f, alpha = 1f - t))
            }
        }
        CareAction.SLEEP -> buildList {
            val t = seg(p, 0.15f, 1f)
            add(CareProp("🌙", -0.5f, -1.05f, 0.18f, alpha = fadeInOut(p, 0.2f, 0.85f)))
            for (i in 0 until 3) {
                val z = seg(p, 0.35f + i * 0.15f, 0.8f + i * 0.07f)
                if (z > 0f) add(CareProp("z", 0.3f + 0.1f * i, -0.6f - 0.35f * z - 0.1f * i, 0.11f + 0.03f * i, alpha = 1f - z * 0.6f))
            }
            if (t > 0.9f) add(CareProp("💤", 0.35f, -0.95f, 0.16f, alpha = 1f - seg(p, 0.95f, 1f)))
        }
    }
}
