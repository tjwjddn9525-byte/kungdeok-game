package com.kungdeok.game.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kungdeok.game.ui.Choco
import com.kungdeok.game.ui.Cream
import com.kungdeok.game.ui.GameApp
import com.kungdeok.game.ui.GameController
import com.kungdeok.game.ui.KungdeokTheme
import com.kungdeok.game.ui.Target

/**
 * 화면 위 고양이를 톡 하면 아래에서 올라오는 창.
 * 앱 화면(방·상태·도감·캣타워·상점·설정)을 그대로 담아서, 앱에 들어가지 않고도 다 할 수 있다.
 * 바깥(어두운 곳)을 누르거나, ✕, 뒤로가기로 닫는다.
 */
class QuickPanel(
    private val context: Context,
    private val wm: WindowManager,
    private val owner: Any, // LifecycleOwner + SavedStateRegistryOwner (OverlayService)
    private val controller: GameController,
    private val onOpenApp: () -> Unit,
) {
    private var root: FrameLayout? = null
    val isOpen: Boolean get() = root != null

    /**
     * [target] 을 돌봄 대상으로 골라 둔 채 연다.
     * 시스템 바 피하기: 안드로이드 11+ 에서 오버레이 창은 기본으로 상태바·내비게이션 바를 피해서 놓이고,
     * 그래도 겹치면 아래 탭 바(NavigationBar)가 내비게이션 바 높이만큼 스스로 띄운다.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun open(target: Target) {
        if (root != null) close()
        val frame = object : FrameLayout(context) {
            // 뒤로가기로 닫기
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) close()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        frame.setViewTreeLifecycleOwner(owner as LifecycleOwner)
        frame.setViewTreeSavedStateRegistryOwner(owner as SavedStateRegistryOwner)

        val compose = ComposeView(context).apply {
            setContent {
                KungdeokTheme {
                    val slide = remember { Animatable(1f) }
                    LaunchedEffect(Unit) { slide.animateTo(0f, tween(220)) }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x66000000))
                            .clickable(remember { MutableInteractionSource() }, indication = null) { close() },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.86f)
                                .graphicsLayer { translationY = size.height * slide.value }
                                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                .background(Cream)
                                // 창 안쪽을 눌러도 닫히지 않게
                                .clickable(remember { MutableInteractionSource() }, indication = null) {},
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("🐾 쿵덕", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Choco)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { close(); onOpenApp() }) { Text("앱에서 열기") }
                                TextButton(onClick = { close() }) { Text("✕", fontSize = 20.sp) }
                                Spacer(Modifier.width(4.dp))
                            }
                            GameApp(controller, initialTarget = target, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        frame.addView(compose, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            },
            // 뒤로가기를 받으려면 포커스를 가져야 한다 (FLAG_NOT_FOCUSABLE 없음)
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "kungdeok-panel"
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        try {
            wm.addView(frame, params)
            root = frame
        } catch (_: RuntimeException) {
            root = null
        }
    }

    fun close() {
        val r = root ?: return
        root = null
        runCatching { wm.removeView(r) }
    }
}
