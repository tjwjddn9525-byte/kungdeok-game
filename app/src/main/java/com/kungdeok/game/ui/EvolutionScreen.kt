package com.kungdeok.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kungdeok.game.model.CatId
import com.kungdeok.game.model.Form
import kotlinx.coroutines.delay

data class EvolutionEvent(val cat: CatId, val fromStage: Int, val toStage: Int)

/**
 * 진화 연출 (SPEC 6-2): 화면이 하얗게 번쩍이고, 이전 형태와 새 형태가 빠르게 번갈아 깜빡인 뒤
 * 새 형태로 고정, 이름과 단계 표시. 끝나면 화면을 눌러 닫는다.
 */
@Composable
fun EvolutionScreen(event: EvolutionEvent, onDone: () -> Unit) {
    val from = Form.of(event.cat, event.fromStage)
    val to = Form.of(event.cat, event.toStage)
    val fromSprite = rememberSprite(from.largeAsset)
    val toSprite = rememberSprite(to.largeAsset)

    val flash = remember { Animatable(0f) }
    var showNew by remember { mutableStateOf(false) }
    var silhouette by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(event) {
        delay(600)
        flash.animateTo(1f, tween(250))
        flash.animateTo(0f, tween(500))
        silhouette = true
        // 점점 빨라지는 깜빡임
        var interval = 320L
        while (interval > 40) {
            showNew = !showNew
            delay(interval)
            interval = (interval * 0.85).toLong()
        }
        flash.animateTo(1f, tween(150))
        showNew = true
        silhouette = false
        flash.animateTo(0f, tween(900))
        finished = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1B1530))
            .clickable(enabled = finished, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (finished) "진화 완료!" else "어라…? ${event.cat.catName}의 모습이…!",
                color = Color.White,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(24.dp))
            Image(
                bitmap = if (showNew) toSprite else fromSprite,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        val s = if (finished) 1f else 0.96f
                        scaleX = s
                        scaleY = s
                    },
                colorFilter = if (silhouette) ColorFilter.tint(Color.White) else null,
            )
            Spacer(Modifier.height(24.dp))
            if (finished) {
                Text(to.monName, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${to.stage.label} · 기술: ${to.skill}",
                    color = Color(0xFFFFE08A),
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(32.dp))
                Text("화면을 눌러 닫기", color = Color(0x99FFFFFF), fontSize = 13.sp)
            } else {
                Spacer(Modifier.height(90.dp))
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))
    }
}
