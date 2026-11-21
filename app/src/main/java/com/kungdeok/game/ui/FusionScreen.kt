package com.kungdeok.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kungdeok.game.model.Form
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class FusionEvent(val firstTime: Boolean)

/** 합체 연출: 은하쿵몬과 덕황몬이 양쪽에서 달려와 빙글 돌며 겹치고, 번쩍 → 쿵덕몬 */
@Composable
fun FusionScreen(event: FusionEvent, onDone: () -> Unit) {
    val kung = rememberSprite(Form.KUNG_5.largeAsset)
    val nodeok = rememberSprite(Form.NODEOK_5.largeAsset)
    val fusion = rememberSprite(Form.FUSION.largeAsset)

    val approach = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    var fused by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(event) {
        delay(500)
        approach.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        launch { spin.animateTo(720f, tween(1200)) }
        delay(900)
        flash.animateTo(1f, tween(300))
        fused = true
        flash.animateTo(0f, tween(1200))
        finished = true
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF120E26)).clickable(enabled = finished, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        val half = maxWidth / 2
        val full = maxWidth
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (finished) "합체 성공!" else "쿵과 노덕의 마음이 하나로…!",
                color = Color.White,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                if (!fused) {
                    val d = half * (1f - approach.value) * 0.75f
                    val side = Modifier.size(full * 0.5f).graphicsLayer { rotationY = spin.value }
                    Image(kung, null, side.offset(x = -d))
                    Image(nodeok, null, side.offset(x = d))
                } else {
                    Image(fusion, Form.FUSION.monName, Modifier.fillMaxWidth(0.9f).aspectRatio(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
            if (finished) {
                Text(Form.FUSION.monName, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("${Form.FUSION.stage.label} · 기술: ${Form.FUSION.skill}", color = Color(0xFFFFE08A), fontSize = 16.sp)
                Text("30분 동안 함께 화면 위를 돌아다녀요", color = Color(0xCCFFFFFF), fontSize = 14.sp)
                if (event.firstTime) {
                    Text("📖 도감에 쿵덕몬이 등록됐어요!", color = Color(0xFFB9F6CA), fontSize = 15.sp)
                }
                Spacer(Modifier.height(24.dp))
                Text("화면을 눌러 닫기", color = Color(0x99FFFFFF), fontSize = 13.sp)
            } else {
                Spacer(Modifier.height(120.dp))
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))
    }
}
