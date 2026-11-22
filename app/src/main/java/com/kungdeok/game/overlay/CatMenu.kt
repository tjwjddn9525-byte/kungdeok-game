package com.kungdeok.game.overlay

import com.kungdeok.game.ui.Art
import com.kungdeok.game.ui.ArtIcon
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.data.GameState
import com.kungdeok.game.logic.CareAction
import com.kungdeok.game.logic.CareRules
import com.kungdeok.game.model.CatId
import com.kungdeok.game.model.Form
import com.kungdeok.game.ui.Choco
import com.kungdeok.game.ui.Cream
import com.kungdeok.game.ui.HeartPink
import com.kungdeok.game.ui.KungdeokTheme
import com.kungdeok.game.ui.formatDuration
import kotlinx.coroutines.delay

/**
 * 화면 위 고양이를 톡 하면 그 고양이 머리 위에 뜨는 작은 메뉴.
 * 고양이를 가리지 않도록 머리 위(말풍선 자리)에 놓고, 꼬리 표시가 고양이를 가리킨다.
 * 바깥을 누르거나 같은 고양이를 다시 누르면 닫히고, 가만히 두면 저절로 닫힌다.
 */
class CatMenu(
    private val context: Context,
    private val wm: WindowManager,
    private val owner: Any, // LifecycleOwner + SavedStateRegistryOwner (OverlayService)
    private val state: MutableState<GameState?>,
    private val onAction: (CatId?, CareAction) -> Unit,
    private val onOpenApp: () -> Unit,
    private val onClosed: () -> Unit,
) {
    private var root: FrameLayout? = null

    /** 열려 있는 메뉴의 대상 (null = 합체한 쿵덕몬). 닫혀 있으면 [isOpen] false. */
    var target: CatId? = null
        private set
    val isOpen: Boolean get() = root != null

    /** 바깥을 눌러 닫힌 시각: 그 바깥 터치가 같은 고양이 톡이었다면 다시 열지 않으려고 */
    var closedByOutsideAt = 0L
        private set

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    /**
     * [cat] 의 메뉴를 연다. 메뉴 아래쪽 끝을 [anchorBottomY] 에 맞추고, 꼬리를 [anchorX] 에 둔다.
     * 위쪽 자리가 모자라면 고양이 아래로 뒤집어 놓는다.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun open(cat: CatId?, anchorX: Float, anchorBottomY: Float, anchorTopIfBelow: Float, screenW: Int) {
        close(notify = false)
        target = cat
        val w = minOf(screenW - dp(12f), dp(340f))
        val h = dp(124f)
        val x = (anchorX - w / 2f).coerceIn(dp(6f).toFloat(), (screenW - w - dp(6f)).toFloat().coerceAtLeast(0f))
        val below = anchorBottomY - h < 0
        val y = if (below) anchorTopIfBelow else anchorBottomY - h
        val pointerX = anchorX - x

        val frame = object : FrameLayout(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    closedByOutsideAt = System.currentTimeMillis()
                    close()
                    return true
                }
                return super.onTouchEvent(event)
            }
        }
        frame.setViewTreeLifecycleOwner(owner as LifecycleOwner)
        frame.setViewTreeSavedStateRegistryOwner(owner as SavedStateRegistryOwner)
        frame.addView(
            ComposeView(context).apply {
                setContent {
                    KungdeokTheme {
                        MenuContent(cat, pointerX, below)
                    }
                }
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        val params = WindowManager.LayoutParams(
            w, h,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x.toInt()
            this.y = y.toInt()
            title = "kungdeok-menu"
        }
        try {
            wm.addView(frame, params)
            root = frame
        } catch (_: RuntimeException) {
            root = null
            target = null
        }
    }

    fun close(notify: Boolean = true) {
        val r = root ?: return
        root = null
        runCatching { wm.removeView(r) }
        if (notify) onClosed()
    }

    @Composable
    internal fun MenuContent(cat: CatId?, pointerX: Float, below: Boolean) {
        val s = state.value ?: return
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            // 1초마다 쿨다운 표시 갱신, 가만히 두면 8초 뒤 저절로 닫힘
            var idle = 0
            while (true) {
                delay(1_000)
                now = System.currentTimeMillis()
                if (++idle >= 8) close()
            }
        }
        val pop = remember { Animatable(0.6f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, tween(140)) }

        val cats: List<CatEntity> = if (cat == null) listOf(s.kung, s.nodeok) else listOf(s.cat(cat))
        val name = if (cat == null) Form.FUSION.monName else {
            val c = s.cat(cat)
            "${Form.of(cat, c.stage).monName} Lv.${c.level}"
        }
        val tail = with(LocalDensity.current) { 10.dp.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        (pointerX / size.width).coerceIn(0f, 1f), if (below) 0f else 1f,
                    )
                },
        ) {
            // 고양이를 가리키는 꼬리
            Canvas(Modifier.fillMaxSize()) {
                val px = pointerX.coerceIn(tail * 2, size.width - tail * 2)
                val path = Path().apply {
                    if (below) {
                        moveTo(px - tail, tail + 6); lineTo(px, 0f); lineTo(px + tail, tail + 6)
                    } else {
                        moveTo(px - tail, size.height - tail - 6); lineTo(px, size.height); lineTo(px + tail, size.height - tail - 6)
                    }
                    close()
                }
                drawPath(path, Cream)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (below) 10.dp else 0.dp, bottom = if (below) 0.dp else 10.dp)
                    .shadow(6.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Cream)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                // 이름과 작은 상태 막대
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Choco, maxLines = 1)
                    Row(Modifier.weight(1f).padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MiniBar(Art.STATUS_HUNGER, cats.minOf { it.hunger }, Color(0xFFE39B4B))
                        MiniBar(Art.STATUS_MOOD, cats.minOf { it.mood }, HeartPink)
                        MiniBar(Art.STATUS_CLEAN, cats.minOf { it.cleanliness }, Color(0xFF4BA3C7))
                        MiniBar(Art.STATUS_SLEEP, cats.minOf { it.sleepiness }, Color(0xFF6C7FB8))
                    }
                    Text(
                        "✕",
                        Modifier.clickable { close() }.padding(horizontal = 6.dp),
                        fontSize = 14.sp,
                        color = Color.Gray,
                    )
                }
                // 돌봄 버튼 한 줄 + 앱 열기
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (action in CareAction.entries) {
                        val left = cats.maxOf { CareRules.cooldownLeft(it, action, now) }
                        val sleeping = CareRules.blockedBySleep(action) && cats.all { it.isAsleep(now) }
                        val ready = left <= 0 && !sleeping
                        MenuButton(
                            icon = action.icon,
                            asset = Art.care(action),
                            label = shortLabel(action),
                            sub = when {
                                sleeping -> "자는 중"
                                left > 0 && action != CareAction.PET -> formatDuration(left)
                                else -> null
                            },
                            enabled = ready,
                            modifier = Modifier.weight(1f),
                        ) {
                            close()
                            onAction(cat, action)
                        }
                    }
                    MenuButton("🏠", "앱 열기", null, true, Modifier.weight(1f), asset = Art.TAB_ROOM) {
                        close()
                        onOpenApp()
                    }
                }
            }
        }
    }

    private fun shortLabel(a: CareAction) = when (a) {
        CareAction.PET -> "쓰담"
        CareAction.FEED -> "밥"
        CareAction.SNACK -> "간식"
        CareAction.PLAY -> "놀기"
        CareAction.BRUSH -> "빗질"
        CareAction.SLEEP -> "재우기"
    }
}

@Composable
private fun MiniBar(asset: String, value: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ArtIcon("", 12.dp, asset = asset)
        Box(Modifier.width(18.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFE8DED2))) {
            Box(Modifier.fillMaxWidth((value / 100.0).toFloat().coerceIn(0f, 1f)).height(5.dp).background(color))
        }
    }
}

@Composable
private fun MenuButton(
    icon: String,
    label: String,
    sub: String?,
    enabled: Boolean,
    modifier: Modifier,
    asset: String? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 쿨다운 중이면 아이콘만 흐리게, 남은 시간은 또렷하게
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(Color.White).graphicsLayer { alpha = if (enabled) 1f else 0.35f },
            contentAlignment = Alignment.Center,
        ) {
            ArtIcon(icon, 26.dp, asset = asset ?: Art.asset(icon))
        }
        if (sub != null) {
            Text(sub, fontSize = 10.sp, color = Color(0xFFB0662E), fontWeight = FontWeight.Bold, maxLines = 1)
        } else {
            Text(label, fontSize = 10.sp, color = Choco, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
