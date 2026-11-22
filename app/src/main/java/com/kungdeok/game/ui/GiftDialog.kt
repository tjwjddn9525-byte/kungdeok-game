package com.kungdeok.game.ui

import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.kungdeok.game.logic.Gift

/** 매일 선물 상자 (SPEC 5-2). [opened] 가 null 이면 아직 안 연 상자. */
@Composable
fun GiftDialog(opened: Gift?, onOpen: () -> Unit, onClose: () -> Unit) {
    val wiggle by rememberInfiniteTransition(label = "gift").animateFloat(
        -8f, 8f, infiniteRepeatable(tween(180), RepeatMode.Reverse), label = "wiggle",
    )
    InlineDialog(
        onDismissRequest = { if (opened != null) onClose() },
        title = { Text(if (opened == null) "오늘의 선물이 도착했어요!" else "선물을 열었어요", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ArtIcon(
                    when (opened) {
                        null -> "🎁"
                        is Gift.Gold -> "🪙"
                        is Gift.Items -> opened.item.icon
                    },
                    96.dp,
                    Modifier.graphicsLayer { rotationZ = if (opened == null) wiggle else 0f },
                )
                if (opened != null) {
                    Text(opened.describe(), fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("내일 또 받을 수 있어요.", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            if (opened == null) Button(onClick = onOpen) { Text("열기") } else Button(onClick = onClose) { Text("좋아!") }
        },
    )
}
