package com.kungdeok.game.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.data.GameState
import com.kungdeok.game.logic.EvolutionRules
import com.kungdeok.game.logic.ExpRules
import com.kungdeok.game.model.CatId
import com.kungdeok.game.model.Form

/** 상태 화면 (SPEC 9): 레벨, 경험치 바, 4가지 상태 바, 오늘 걸음 수 */
@Composable
fun StatusScreen(state: GameState, now: Long) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        for (id in CatId.entries) CatCard(id, state.cat(id), now)
        Text("오늘 걸음 수: 2단계에서 연동 예정", color = Color.Gray, fontSize = 13.sp)
    }
}

@Composable
private fun CatCard(id: CatId, cat: CatEntity, now: Long) {
    val form = Form.of(id, cat.stage)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F1))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(rememberSprite(form.largeAsset), form.monName, Modifier.size(88.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("${id.catName} · ${form.monName}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${form.stage.label} · 기술: ${form.skill}", fontSize = 13.sp, color = Color.Gray)
                    if (cat.isAsleep(now)) Text("자는 중 (${formatDuration(cat.sleepUntil - now)} 남음)", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            val need = ExpRules.expToNext(cat.level)
            Bar("Lv.${cat.level}  경험치 ${formatExp(cat.exp)} / $need", (cat.exp / need).toFloat(), Color(0xFF7C5CC4))
            Bar("배고픔", cat.hunger.toFloat() / 100f, Color(0xFFE39B4B))
            Bar("기분", cat.mood.toFloat() / 100f, HeartPink)
            Bar("청결", cat.cleanliness.toFloat() / 100f, Color(0xFF4BA3C7))
            Bar("졸림", cat.sleepiness.toFloat() / 100f, Color(0xFF6C7FB8))
            Spacer(Modifier.height(8.dp))
            val req = EvolutionRules.requirement(cat.stage)
            Text(
                when {
                    cat.evolutionReady -> "✨ 진화할 수 있어요! 방 화면에서 진화하기를 눌러 주세요."
                    req != null -> "다음 진화 조건: ${req.describe()}"
                    else -> "최종 단계예요."
                },
                fontSize = 13.sp,
                color = Color(0xFF7C5CC4),
            )
        }
    }
}

@Composable
private fun Bar(label: String, fraction: Float, color: Color) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            if (!label.startsWith("Lv")) Text("${(fraction * 100).toInt()}", fontSize = 13.sp)
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color,
            trackColor = Color(0xFFE8DED2),
            drawStopIndicator = {},
        )
    }
}
