package com.kungdeok.game.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.kungdeok.game.logic.MobMove
import com.kungdeok.game.logic.MobType
import com.kungdeok.game.ui.Art
import com.kungdeok.game.ui.Sprites
import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * 바탕화면에 나타나는 잡몹 하나 = 작은 오버레이 창 하나.
 * 생쥐는 바닥을 걷고, 파리·나비는 날고, 귀뚜라미는 뛰고, 거미는 위에서 실을 타고 내려온다.
 * 톡 하면 [onTap] → 고양이가 달려와 덮친다.
 */
@SuppressLint("ViewConstructor")
class MobOverlay(
    context: Context,
    private val wm: WindowManager,
    val type: MobType,
    private val screenW: Int,
    /** 고양이들이 서 있는 바닥 (화면 y) */
    private val groundBottom: Int,
    private val onTap: (MobOverlay) -> Unit,
) : View(context) {

    enum class State { ROAM, LANDING, FROZEN, DEFEATED, ESCAPING, GONE }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val winW = dp(110f).toInt()
    private val winH = dp(96f).toInt()
    private val mobSize = dp(28f)
    /** 창 안에서 몹(이모지) 가운데의 위치 */
    private val bodyCx get() = winW / 2f
    private val bodyCy get() = winH - dp(20f)

    private val params = WindowManager.LayoutParams(
        winW, winH,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "kungdeok-mob"
    }
    private var attached = false

    var state = State.ROAM
        private set
    var hp = type.hp
        private set

    /** 창 왼쪽 위 (화면 좌표) */
    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var time = Random.nextFloat() * 10f
    var age = 0f
        private set
    private var pauseLeft = 0f
    private var flyTarget = 0f to 0f
    private var dropTargetY = 0f
    private var flashLeft = 0f
    /** 히트스톱 (맞는 순간 찰나 멈춤) */
    private var freezeLeft = 0f
    /** 할퀸 자국 남은 시간 */
    private var slashLeft = 0f

    /** 맞았을 때 튀는 픽셀 파편 (창 안 좌표) */
    private class Bit(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Int, var life: Float)
    private val bits = mutableListOf<Bit>()
    private val bitPaint = Paint()
    private val slashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF8E1"); strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }
    private val slashInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF7043"); strokeWidth = 2.5f; strokeCap = Paint.Cap.ROUND
    }
    private val whiteTint = android.graphics.PorterDuffColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_ATOP)

    private fun burst(n: Int, power: Float) {
        val colors = intArrayOf(Color.parseColor("#FFE082"), Color.WHITE, Color.parseColor("#FFB74D"))
        repeat(n) { i ->
            val ang = (i.toFloat() / n) * 2 * Math.PI.toFloat() + Random.nextFloat() * 0.6f
            val sp = power * (0.6f + Random.nextFloat() * 0.6f)
            bits += Bit(bodyCx, bodyCy, kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp - power * 0.4f, colors[i % colors.size], 0.5f)
        }
    }
    private var text: String? = null
    private var textTime = 0f
    private var startled = 0f
    private var fleeBoost = 1f

    /** 몹 몸통 가운데 (화면 좌표) */
    val centerX: Float get() = x + bodyCx
    val centerY: Float get() = y + bodyCy

    private val groundY: Float get() = groundBottom - winH + dp(6f)
    private val flyTop: Float get() = groundBottom - dp(320f)
    private val flyBottom: Float get() = groundBottom - dp(110f)

    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = mobSize }
    /** 픽셀 아트 그림 (없으면 이모지). 그림은 왼쪽을 보고 있다. */
    private val art: Bitmap? = Sprites.bitmapOrNull(context, Art.mob(type))
    private val hitArt: Bitmap? = Sprites.bitmapOrNull(context, "ui/fx_hit.png")
    private val sparkleArt: Bitmap? = Sprites.bitmapOrNull(context, "ui/fx_sparkle.png")
    private val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val artRect = RectF()

    private fun drawArt(canvas: Canvas, bmp: Bitmap, cx: Float, cy: Float, size: Float, alpha: Int = 255) {
        val s = size / maxOf(bmp.width, bmp.height)
        val w = bmp.width * s
        val h = bmp.height * s
        artRect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        artPaint.alpha = alpha
        canvas.drawBitmap(bmp, null, artRect, artPaint)
        artPaint.alpha = 255
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE082"); textSize = dp(14f); typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(dp(2f), 0f, 0f, Color.parseColor("#5A3A28"))
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = dp(16f); typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(dp(2f), 0f, 0f, Color.BLACK)
    }
    private val threadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 230, 230, 230); strokeWidth = dp(1f)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) }

    init {
        // 등장 위치: 걷는 놈은 화면 가장자리에서, 나는 놈은 공중, 거미는 위에서
        val fromLeft = Random.nextBoolean()
        val speed = dp(type.speedDp)
        when (type.move) {
            MobMove.WALK, MobMove.HOP -> {
                x = if (fromLeft) -bodyCx + dp(4f) else screenW - bodyCx - dp(4f)
                y = groundY
                vx = if (fromLeft) speed else -speed
            }
            MobMove.FLY -> {
                x = if (fromLeft) -bodyCx else screenW - bodyCx
                y = flyTop + Random.nextFloat() * (flyBottom - flyTop)
                pickFlyTarget()
            }
            MobMove.DROP -> {
                x = dp(40f) + Random.nextFloat() * (screenW - dp(80f)) - bodyCx
                y = groundBottom - dp(560f)
                dropTargetY = groundBottom - dp(200f) - Random.nextFloat() * dp(120f)
            }
        }
    }

    fun attach() {
        if (attached) return
        params.x = x.toInt()
        params.y = y.toInt()
        runCatching { wm.addView(this, params) }.onSuccess { attached = true }
    }

    fun detach() {
        if (!attached) return
        runCatching { wm.removeView(this) }
        attached = false
    }

    private fun pickFlyTarget() {
        flyTarget = (dp(30f) + Random.nextFloat() * (screenW - dp(60f))) to (flyTop + Random.nextFloat() * (flyBottom - flyTop))
    }

    /** 고양이가 덮치러 온다: 놀라서 바닥으로 내려와 얼어붙는다 */
    fun startle() {
        if (state != State.ROAM) return
        state = if (abs(y - groundY) > dp(2f)) State.LANDING else State.FROZEN
        vx = 0f
        vy = 0f
        startled = 0.6f
    }

    /** 사냥이 빗나가면 다시 돌아다닌다 */
    fun release() {
        if (state == State.FROZEN || state == State.LANDING) {
            state = State.ROAM
            fleeBoost = 1.8f
            if (vx == 0f) vx = dp(type.speedDp) * (if (Random.nextBoolean()) 1 else -1)
        }
    }

    /** 한 대 맞음. 쓰러뜨렸으면 true */
    fun hit(): Boolean {
        if (state == State.DEFEATED || state == State.GONE) return false
        hp--
        flashLeft = 0.3f
        freezeLeft = 0.09f
        slashLeft = 0.28f
        burst(if (hp <= 0) 12 else 7, dp(170f))
        if (hp <= 0) {
            state = State.DEFEATED
            textTime = 0f
            return true
        }
        // 아직 살아 있으면 도망치듯 튀어 오른다
        state = State.ROAM
        fleeBoost = 2.2f
        vx = dp(type.speedDp) * fleeBoost * (if (Random.nextBoolean()) 1 else -1)
        if (type.move == MobMove.HOP || type.move == MobMove.WALK) vy = -dp(260f)
        return false
    }

    /** 잡혔을 때 떠오를 글자 (+경험치) */
    fun showReward(t: String) {
        text = t
        textTime = 0f
    }

    fun escape() {
        if (state == State.ROAM || state == State.FROZEN || state == State.LANDING) {
            state = State.ESCAPING
            showText("도망!")
        }
    }

    fun showText(t: String) {
        text = t
        textTime = 0f
    }

    fun step(dt: Float) {
        // 파편은 히트스톱 중에도 움직인다
        val it = bits.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.life -= dt
            if (b.life <= 0) { it.remove(); continue }
            b.vy += dp(700f) * dt
            b.x += b.vx * dt
            b.y += b.vy * dt
        }
        if (slashLeft > 0) slashLeft -= dt
        if (freezeLeft > 0f) {
            freezeLeft -= dt
            invalidate()
            return
        }
        time += dt
        age += dt
        if (flashLeft > 0) flashLeft -= dt
        if (startled > 0) startled -= dt
        textTime += dt
        val speed = dp(type.speedDp) * fleeBoost
        when (state) {
            State.ROAM -> roam(dt, speed)
            State.LANDING -> {
                vy += dp(1800f) * dt
                y += vy * dt
                if (y >= groundY) {
                    y = groundY
                    vy = 0f
                    state = State.FROZEN
                }
            }
            State.FROZEN -> Unit
            State.DEFEATED -> if (textTime > 1.4f) state = State.GONE
            State.ESCAPING -> {
                // 가까운 화면 끝으로 달아남
                val dir = if (centerX < screenW / 2f) -1 else 1
                x += dir * speed * 2.2f * dt
                if (type.move == MobMove.FLY || type.move == MobMove.DROP) y -= dp(90f) * dt
                if (x < -winW || x > screenW || y < -winH) state = State.GONE
            }
            State.GONE -> Unit
        }
        if (attached && state != State.GONE) {
            val nx = x.toInt()
            val ny = y.toInt()
            if (nx != params.x || ny != params.y) {
                params.x = nx
                params.y = ny
                runCatching { wm.updateViewLayout(this, params) }
            }
        }
        invalidate()
    }

    private fun roam(dt: Float, speed: Float) {
        if (fleeBoost > 1f) fleeBoost = maxOf(1f, fleeBoost - dt * 0.5f)
        when (type.move) {
            MobMove.WALK, MobMove.HOP -> {
                // 맞고 튀어 오른 뒤에는 떨어진다
                if (y < groundY || vy < 0) {
                    vy += dp(1600f) * dt
                    y += vy * dt
                    if (y >= groundY) { y = groundY; vy = 0f }
                }
                if (pauseLeft > 0) {
                    pauseLeft -= dt
                } else {
                    x += (if (vx >= 0) 1 else -1) * speed * dt
                    if (Random.nextFloat() < dt * 0.25f) pauseLeft = 0.6f + Random.nextFloat()
                    if (Random.nextFloat() < dt * 0.15f) vx = -vx
                    if (type.move == MobMove.HOP && y >= groundY && Random.nextFloat() < dt * 1.2f) vy = -dp(220f)
                }
                bounceAtEdges()
            }
            MobMove.FLY -> {
                val (tx, ty) = flyTarget
                val dx = tx - centerX
                val dy = ty - y
                val dist = maxOf(1f, kotlin.math.sqrt(dx * dx + dy * dy))
                vx = dx / dist * speed
                x += vx * dt
                y += dy / dist * speed * dt + sin(time * 9f) * dp(30f) * dt
                if (dist < dp(12f) || Random.nextFloat() < dt * 0.3f) pickFlyTarget()
            }
            MobMove.DROP -> {
                // 천천히 내려와서 대롱대롱
                if (y < dropTargetY) y = minOf(dropTargetY, y + speed * dt)
                x += sin(time * 1.5f) * dp(6f) * dt
            }
        }
    }

    private fun bounceAtEdges() {
        val minX = -bodyCx + dp(4f)
        val maxX = screenW - bodyCx - dp(4f)
        if (x < minX) { x = minX; vx = abs(vx).coerceAtLeast(1f) }
        if (x > maxX) { x = maxX; vx = -abs(vx).coerceAtLeast(1f) }
    }

    override fun onDraw(canvas: Canvas) {
        if (state == State.GONE) return
        val cx = bodyCx
        val cy = bodyCy

        if (state != State.DEFEATED) {
            // 거미줄
            if (type.move == MobMove.DROP && state == State.ROAM) {
                canvas.drawLine(cx, 0f, cx, cy - mobSize * 0.5f, threadPaint)
            }
            // 바닥 그림자
            if (abs(y - groundY) < dp(4f)) canvas.drawOval(cx - mobSize * 0.45f, cy + mobSize * 0.25f, cx + mobSize * 0.45f, cy + mobSize * 0.4f, shadowPaint)

            canvas.save()
            // 날갯짓, 걸음 통통, 맞으면 깜빡
            val flap = if (type.move == MobMove.FLY) 1f - 0.25f * abs(sin(time * 22f)) else 1f
            val bob = if (type.move == MobMove.WALK && state == State.ROAM && pauseLeft <= 0) abs(sin(time * 14f)) * dp(2f) else 0f
            val shake = if (state == State.FROZEN || startled > 0) sin(time * 60f) * dp(1.5f) else 0f
            canvas.translate(shake, -bob)
            if (vx > 0 && type.move != MobMove.DROP) canvas.scale(-1f, 1f, cx, cy)
            canvas.scale(1f, flap, cx, cy)
            val blink = if (flashLeft > 0 && flashLeft < 0.18f && (time * 30).toInt() % 2 == 0) 90 else 255
            val a = art
            if (a != null) {
                drawArt(canvas, a, cx, cy, mobSize * 1.35f, blink)
                // 맞는 순간 하얗게 번쩍
                if (flashLeft > 0.18f) {
                    artPaint.colorFilter = whiteTint
                    drawArt(canvas, a, cx, cy, mobSize * 1.35f, 230)
                    artPaint.colorFilter = null
                }
            } else {
                emojiPaint.alpha = blink
                val tw = emojiPaint.measureText(type.icon)
                canvas.drawText(type.icon, cx - tw / 2, cy + mobSize * 0.35f, emojiPaint)
            }
            canvas.restore()

            if (state == State.FROZEN || state == State.LANDING) {
                canvas.drawText("!", cx + mobSize * 0.45f, cy - mobSize * 0.5f, markPaint)
            }
        } else {
            // 쓰러뜨림: 💥 → 별
            val hit = hitArt
            val sparkle = sparkleArt
            if (hit != null && sparkle != null) {
                if (textTime < 0.35f) drawArt(canvas, hit, cx, cy, mobSize * (0.8f + textTime * 2.6f))
                else drawArt(canvas, sparkle, cx, cy, mobSize * 1.2f, (255 * (1 - (textTime - 0.35f) / 1.05f)).toInt().coerceIn(0, 255))
            } else if (textTime < 0.35f) {
                val s = 0.6f + textTime * 2f
                emojiPaint.textSize = mobSize * s
                val tw = emojiPaint.measureText("💥")
                canvas.drawText("💥", cx - tw / 2, cy + mobSize * 0.35f * s, emojiPaint)
                emojiPaint.textSize = mobSize
            } else {
                val tw = emojiPaint.measureText("✨")
                emojiPaint.alpha = (255 * (1 - (textTime - 0.35f) / 1.05f)).toInt().coerceIn(0, 255)
                canvas.drawText("✨", cx - tw / 2, cy + mobSize * 0.3f, emojiPaint)
                emojiPaint.alpha = 255
            }
        }

        // 할퀸 자국 세 줄
        if (slashLeft > 0f) {
            val p = 1f - slashLeft / 0.28f
            val draw = (p / 0.4f).coerceAtMost(1f)
            val alpha = if (p < 0.4f) 255 else (255 * (1 - (p - 0.4f) / 0.6f)).toInt().coerceIn(0, 255)
            slashPaint.alpha = alpha
            slashInner.alpha = alpha
            val s = mobSize * 1.1f
            for (k in -1..1) {
                val sx = cx + k * s * 0.22f + s * 0.3f
                val sy = cy - s * 0.45f
                val ex = sx - s * 0.6f * draw
                val ey = sy + s * 0.9f * draw
                canvas.drawLine(sx, sy, ex, ey, slashPaint)
                canvas.drawLine(sx, sy, ex, ey, slashInner)
            }
        }
        // 파편
        for (b in bits) {
            bitPaint.color = b.color
            bitPaint.alpha = (255 * (b.life / 0.5f)).toInt().coerceIn(0, 255)
            val r = dp(2.5f)
            canvas.drawRect(b.x - r, b.y - r, b.x + r, b.y + r, bitPaint)
        }

        text?.let {
            if (textTime < 1.4f) {
                textPaint.alpha = (255 * (1 - textTime / 1.4f)).toInt().coerceIn(0, 255)
                val tw = textPaint.measureText(it)
                val ty = cy - mobSize * 0.8f - textTime * dp(26f)
                canvas.drawText(it, (cx - tw / 2).coerceIn(0f, winW - tw), ty.coerceAtLeast(textPaint.textSize), textPaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state != State.ROAM) return false
        // 몸통 근처를 눌렀을 때만 (창의 빈 곳은 아래 앱으로 넘기지 못하지만, 너무 넓게 잡히지 않게)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val near = abs(event.x - bodyCx) < mobSize * 1.3f && abs(event.y - bodyCy) < mobSize * 1.3f
            if (near) onTap(this)
        }
        return true
    }
}
