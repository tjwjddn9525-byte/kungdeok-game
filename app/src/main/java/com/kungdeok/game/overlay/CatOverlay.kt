package com.kungdeok.game.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.logic.StatusRules
import com.kungdeok.game.logic.ShopItem
import com.kungdeok.game.model.CatId
import com.kungdeok.game.model.Form
import com.kungdeok.game.model.Stage
import com.kungdeok.game.ui.Sprites
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** 화면 위를 돌아다니는 행동 패턴 (SPEC 8): 걷기 → 앉기 → 두리번 → 잠자기 랜덤 반복 */
enum class Behavior { WALK, SIT, LOOK, NAP }

/** 관계 연출용 특수 효과 */
enum class Effect { NONE, HEARTS, DUST }

/**
 * 감독(OverlayService)이 시키는 짧은 연출 한 토막.
 * [targetX] 로 걸어간 뒤 [hold] 행동을 [seconds] 초 동안, [faceRight] 쪽을 보며 [effect] 와 함께 한다.
 */
data class Script(
    val targetX: Float?,
    val hold: Behavior,
    val seconds: Float,
    val faceRight: Boolean? = null,
    val effect: Effect = Effect.NONE,
)

interface CatOverlayCallbacks {
    fun onTap(cat: CatId)
    fun onLongPress(cat: CatId)
}

/**
 * 고양이 한 마리 = 작은 오버레이 창 하나. 창 위치를 매 프레임 옮겨서 화면 아래쪽을 걸어 다니게 한다.
 * 1단계에서는 정지 그림 한 장을 좌우 반전과 통통 튀는 움직임으로 대신한다.
 */
@SuppressLint("ViewConstructor")
class CatOverlay(
    context: Context,
    private val wm: WindowManager,
    val cat: CatId,
    private val callbacks: CatOverlayCallbacks,
    /** true 면 합체한 쿵덕몬 (상태는 [cat] 쪽 것을 받지만 그림과 크기는 합체 궁극체). */
    val fusion: Boolean = false,
) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    // ---- 게임 상태 ----
    private var stage = 1
    private var sprite: Bitmap? = null
    /** 걷기 2프레임, 잠자기 1프레임 (없으면 기본 그림) */
    private var walkFrames: List<Bitmap> = emptyList()
    private var sleepFrame: Bitmap? = null
    private var bubble: String? = null
    private var asleep = false
    private var sulking = false
    private var messy = false
    private var sizeScale = 1f
    private var accessoryIcon: String? = null

    // ---- 화면 위 움직임 ----
    private val params = WindowManager.LayoutParams(
        0, 0,
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
        title = "kungdeok-${cat.id}"
    }
    private var attached = false

    private var screenW = 0
    private var groundY = 0f
    private var x = 0f
    private var y = 0f
    private var vy = 0f
    private var falling = false
    private var dragging = false

    private var behavior = Behavior.SIT
    private var behaviorLeft = 1f
    private var direction = if (cat == CatId.KUNG) 1 else -1
    private var facingRight = false
    private var lookTimer = 0f
    private var time = Random.nextFloat() * 10f
    private var heartTime = 0f
    private var tempBubble: String? = null
    private var tempBubbleTime = 0f

    private val scripts = ArrayDeque<Script>()
    private var scriptHoldLeft = -1f
    private var effect = Effect.NONE
    private var effectTime = 0f

    private val spritePaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5A3A28"); style = Paint.Style.STROKE; strokeWidth = dp(1.2f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5A3A28"); textSize = dp(11f); typeface = Typeface.DEFAULT_BOLD
    }
    private val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = dp(12f); typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(dp(1.5f), 0f, 0f, Color.parseColor("#5A3A28"))
    }
    private val accessoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(13f) }
    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E86A8A"); textSize = dp(15f); typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(dp(1.5f), 0f, 0f, Color.WHITE)
    }
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 190, 180, 165) }
    private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(16f) }
    private val srcRect = Rect()
    private val dstRect = RectF()

    private val spriteSizePx: Float get() = dp(Stage.of(stage).overlayDp * sizeScale)
    private val bubbleSpace: Float get() = dp(30f)
    // 잠자기 프레임은 6% 넓고 걷기 프레임은 다리가 삐져나오므로 여유를 둔다.
    private val viewW: Int get() = maxOf(spriteSizePx * 1.12f, dp(84f)).toInt()
    private val viewH: Int get() = (spriteSizePx + bubbleSpace + dp(8f)).toInt()

    fun attach() {
        if (attached) return
        params.width = viewW
        params.height = viewH
        params.x = x.toInt()
        params.y = y.toInt()
        wm.addView(this, params)
        attached = true
    }

    fun detach() {
        if (!attached) return
        wm.removeView(this)
        attached = false
    }

    /** 합체/해제할 때 [other] 가 있던 자리에서 나타나게 한다. */
    fun moveCenterTo(cx: Float) {
        x = windowXFor(cx)
        y = groundY
    }

    /** 화면 크기가 바뀌면 (처음, 회전) 바닥 위치를 다시 잡는다. */
    fun setScreen(width: Int, bottom: Int, firstTime: Boolean) {
        screenW = width
        groundY = (bottom - viewH).toFloat()
        if (firstTime) {
            x = if (cat == CatId.KUNG) width * 0.25f else width * 0.6f
            y = groundY
        } else if (!dragging) {
            x = x.coerceIn(0f, (screenW - viewW).toFloat().coerceAtLeast(0f))
            falling = true
        }
    }

    fun setSizeScale(scale: Float) {
        if (scale == sizeScale) return
        val oldBottom = groundY + viewH
        sizeScale = scale
        groundY = oldBottom - viewH
        relayout()
    }

    fun setOpacity(a: Float) {
        alpha = a
    }

    /** 게임 상태가 바뀔 때마다 불린다. */
    fun update(state: CatEntity, now: Long) {
        val newStage = if (fusion) Stage.FUSION.level else state.stage
        if (newStage != stage || sprite == null) {
            val oldBottom = groundY + viewH
            stage = newStage
            val form = if (fusion) Form.FUSION else Form.of(cat, stage)
            sprite = Sprites.bitmap(context, form.smallAsset)
            walkFrames = listOfNotNull(
                Sprites.bitmapOrNull(context, form.frameAsset("walk1")),
                Sprites.bitmapOrNull(context, form.frameAsset("walk2")),
            )
            sleepFrame = Sprites.bitmapOrNull(context, form.frameAsset("sleep"))
            groundY = oldBottom - viewH
            relayout()
        }
        if (fusion) {
            bubble = null
            asleep = false
            sulking = false
            messy = false
            invalidate()
            return
        }
        accessoryIcon = ShopItem.byId(state.accessory)?.icon
        bubble = StatusRules.bubble(state, now)
        asleep = state.isAsleep(now)
        sulking = state.mood < StatusRules.LOW && !asleep
        messy = state.cleanliness < StatusRules.LOW
        if (asleep) behavior = Behavior.NAP
        invalidate()
    }

    private fun relayout() {
        if (!attached) return
        params.width = viewW
        params.height = viewH
        if (!dragging && !falling) y = groundY
        params.x = x.toInt()
        params.y = y.toInt()
        wm.updateViewLayout(this, params)
    }

    // ---- 감독(OverlayService)용 ----
    val centerX: Float get() = x + viewW / 2f
    val widthPx: Int get() = viewW
    val behaviorNow: Behavior get() = behavior
    val isAsleep: Boolean get() = asleep

    /** 연출 중이거나 사람이 만지는 중이면 바쁨 */
    val isBusy: Boolean get() = scripts.isNotEmpty() || dragging || falling

    /** 가운데 x 좌표가 [cx] 가 되는 창 x 위치 (화면 안으로 제한) */
    fun windowXFor(cx: Float): Float = (cx - viewW / 2f).coerceIn(0f, (screenW - viewW).toFloat().coerceAtLeast(0f))

    fun play(vararg list: Script) {
        scripts.clear()
        scripts.addAll(list)
        scriptHoldLeft = -1f
    }

    fun cancelScripts() {
        scripts.clear()
        effect = Effect.NONE
    }

    fun showHeart() {
        heartTime = 1.2f
    }

    fun showTempBubble(text: String) {
        tempBubble = text
        tempBubbleTime = 1.6f
    }

    /** 매 프레임 호출. dt 는 초. */
    fun step(dt: Float) {
        time += dt
        if (heartTime > 0) heartTime -= dt
        if (tempBubbleTime > 0) tempBubbleTime -= dt else tempBubble = null

        if (!dragging) {
            if (falling) {
                vy += dp(2400f) * dt
                y += vy * dt
                if (y >= groundY) {
                    y = groundY
                    vy = 0f
                    falling = false
                }
            } else {
                y = groundY
                think(dt)
            }
        }
        if (attached) {
            val nx = x.toInt()
            val ny = y.toInt()
            if (nx != params.x || ny != params.y) {
                params.x = nx
                params.y = ny
                wm.updateViewLayout(this, params)
            }
        }
        invalidate()
    }

    private fun think(dt: Float) {
        if (scripts.isNotEmpty() && runScript(dt)) return
        if (asleep) {
            behavior = Behavior.NAP
            return
        }
        behaviorLeft -= dt
        if (behaviorLeft <= 0) pickBehavior()
        when (behavior) {
            Behavior.WALK -> {
                val speed = dp(28f) * (if (sulking) 0.6f else 1f)
                x += direction * speed * dt
                val maxX = (screenW - viewW).toFloat().coerceAtLeast(0f)
                if (x <= 0f) { x = 0f; direction = 1 }
                if (x >= maxX) { x = maxX; direction = -1 }
                facingRight = direction > 0
            }
            Behavior.LOOK -> {
                lookTimer -= dt
                if (lookTimer <= 0) {
                    facingRight = !facingRight
                    lookTimer = 0.6f + Random.nextFloat() * 0.8f
                }
            }
            Behavior.SIT, Behavior.NAP -> Unit
        }
    }

    /** 연출을 한 프레임 진행한다. 처리했으면 true. */
    private fun runScript(dt: Float): Boolean {
        val sc = scripts.first()
        val tx = sc.targetX
        if (scriptHoldLeft < 0f) {
            // 1) 목표 지점까지 걷기 (잠든 고양이는 그 자리에서)
            if (tx != null && !asleep && abs(x - tx) > dp(3f)) {
                behavior = Behavior.WALK
                val dir = if (tx > x) 1 else -1
                x += dir * dp(40f) * dt
                if ((dir > 0 && x > tx) || (dir < 0 && x < tx)) x = tx
                facingRight = dir > 0
                return true
            }
            if (tx != null && !asleep) x = tx
            scriptHoldLeft = sc.seconds
            effect = sc.effect
        }
        // 2) 자리에서 행동 유지
        behavior = if (asleep) Behavior.NAP else sc.hold
        sc.faceRight?.let { facingRight = it }
        effectTime += dt
        scriptHoldLeft -= dt
        if (scriptHoldLeft <= 0f) {
            scripts.removeFirst()
            scriptHoldLeft = -1f
            effect = Effect.NONE
            if (scripts.isEmpty()) {
                behaviorLeft = 2f + Random.nextFloat() * 2f
                if (behavior == Behavior.WALK) behavior = Behavior.SIT
            }
        }
        return true
    }

    private fun pickBehavior() {
        val r = Random.nextFloat()
        behavior = when {
            sulking -> if (r < 0.3f) Behavior.WALK else Behavior.SIT
            r < 0.45f -> Behavior.WALK
            r < 0.70f -> Behavior.SIT
            r < 0.90f -> Behavior.LOOK
            else -> Behavior.NAP
        }
        behaviorLeft = when (behavior) {
            Behavior.WALK -> 3f + Random.nextFloat() * 5f
            Behavior.SIT -> 3f + Random.nextFloat() * 4f
            Behavior.LOOK -> 2f + Random.nextFloat() * 2f
            Behavior.NAP -> 6f + Random.nextFloat() * 8f
        }
        if (behavior == Behavior.WALK) direction = if (Random.nextBoolean()) 1 else -1
    }

    override fun onDraw(canvas: Canvas) {
        val base = sprite ?: return
        val size = spriteSizePx
        // 크기는 항상 기본 그림 기준으로 맞춰서 프레임이 바뀌어도 덩치가 튀지 않게 한다.
        val s = size / maxOf(base.width, base.height).toFloat()
        val bmp = when {
            dragging || falling -> base
            behavior == Behavior.WALK && walkFrames.size == 2 -> walkFrames[(time * 6f).toInt() % 2]
            behavior == Behavior.NAP -> sleepFrame ?: base
            else -> base
        }
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val dw = w * s
        val dh = h * s
        val cx = width / 2f
        val bottom = height.toFloat()

        // 통통 튀기: 걸을 때 깡총, 잘 때 숨쉬기
        var hop = 0f
        var squash = 1f
        when {
            dragging -> Unit
            behavior == Behavior.WALK && !falling -> hop = abs(sin(time * 9f)) * dp(4f)
            behavior == Behavior.NAP -> squash = 1f - 0.03f * (1 + sin(time * 2.5f))
            effect == Effect.DUST -> hop = abs(sin(time * 23f)) * dp(5f)
        }
        val dh2 = dh * squash
        dstRect.set(cx - dw / 2, bottom - dh2 - hop, cx + dw / 2, bottom - hop)
        srcRect.set(0, 0, bmp.width, bmp.height)

        // 등 돌림(삐짐)은 반대쪽을 보게 한다.
        val flip = if (sulking) !facingRight else facingRight
        canvas.save()
        if (flip) canvas.scale(-1f, 1f, cx, 0f)
        canvas.drawBitmap(bmp, srcRect, dstRect, spritePaint)
        canvas.restore()

        val top = dstRect.top
        accessoryIcon?.let {
            val tw = accessoryPaint.measureText(it)
            canvas.drawText(it, cx - tw / 2, top + dp(10f), accessoryPaint)
        }
        if (messy) canvas.drawText("💨", dstRect.right - dp(14f), top + dp(14f), effectPaint)
        if (behavior == Behavior.NAP && !dragging) {
            val zx = cx + dw * 0.25f
            val phase = (time % 2f) / 2f
            canvas.drawText("z", zx, top + dp(4f) - phase * dp(10f), zPaint)
        }
        when (effect) {
            Effect.HEARTS -> {
                // 그루밍: 작은 하트가 번갈아 떠오른다
                val phase = (effectTime % 1.4f) / 1.4f
                heartPaint.alpha = (255 * (1 - phase * 0.7f)).toInt()
                val side = if (facingRight) 1 else -1
                canvas.drawText("♥", cx + side * dw * 0.3f, top + dp(12f) - phase * dp(16f), heartPaint)
            }
            Effect.DUST -> {
                // 투닥거림: 발치에 먼지구름이 들썩인다
                for (i in 0 until 5) {
                    val a = time * 14f + i * 1.3f
                    val px = cx + sin(a) * dw * 0.45f
                    val py = bottom - dp(3f) - abs(sin(a * 1.7f)) * dh * 0.18f
                    canvas.drawCircle(px, py, dp(4f + (i % 3) * 1.5f), dustPaint)
                }
            }
            Effect.NONE -> Unit
        }
        if (heartTime > 0) {
            val rise = (1.2f - heartTime) * dp(18f)
            canvas.drawText("❤", cx - dp(8f), top - rise + dp(6f), effectPaint)
        }
        val text = tempBubble ?: bubble?.takeIf { it != "Zzz…" }
        if (text != null) drawBubble(canvas, text, cx, top - dp(4f))
    }

    private fun drawBubble(canvas: Canvas, text: String, cx: Float, bottomY: Float) {
        val tw = textPaint.measureText(text)
        val padH = dp(7f)
        val h = dp(18f)
        val b = maxOf(bottomY, h + dp(2f))
        val r = RectF(cx - tw / 2 - padH, b - h, cx + tw / 2 + padH, b)
        canvas.drawRoundRect(r, dp(9f), dp(9f), bubblePaint)
        canvas.drawRoundRect(r, dp(9f), dp(9f), bubbleStroke)
        canvas.drawText(text, cx - tw / 2, b - dp(5f), textPaint)
    }

    // ---- 터치: 톡(쓰다듬기), 꾹(메인 화면), 드래그(옮기기) ----
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false
    private val longPressRunnable = Runnable {
        if (!dragging) {
            longPressed = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            callbacks.onLongPress(cat)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downX = x
                downY = y
                longPressed = false
                dragging = false
                postDelayed(longPressRunnable, longPressMs)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && !longPressed && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragging = true
                    falling = false
                    cancelScripts()
                    removeCallbacks(longPressRunnable)
                }
                if (dragging) {
                    x = (downX + dx).coerceIn(0f, (screenW - viewW).toFloat().coerceAtLeast(0f))
                    y = (downY + dy).coerceIn(0f, groundY)
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (dragging) {
                    dragging = false
                    vy = 0f
                    falling = y < groundY
                    behavior = Behavior.SIT
                    behaviorLeft = 1.5f
                } else if (!longPressed) {
                    callbacks.onTap(cat)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (dragging) {
                    dragging = false
                    falling = true
                }
            }
        }
        return true
    }
}
