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
import com.kungdeok.game.logic.StepRules
import com.kungdeok.game.logic.ShopItem
import com.kungdeok.game.logic.ShopKind
import com.kungdeok.game.logic.ShopRules
import androidx.compose.material3.TextButton
import com.kungdeok.game.logic.epochDay
import com.kungdeok.game.StepTracker
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.kungdeok.game.logic.ExpRules
import com.kungdeok.game.model.CatId
import com.kungdeok.game.model.Form

/** 상태 화면 (SPEC 9): 레벨, 경험치 바, 4가지 상태 바, 오늘 걸음 수 */
@Composable
fun StatusScreen(state: GameState, now: Long, onUseBag: (CatId, ShopItem) -> Unit = { _, _ -> }) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        for (id in CatId.entries) CatCard(id, state.cat(id), now)
        BagCard(state, onUseBag)
        StepsCard(state)
    }
}

/** 가방: 상점에서 산 소모품을 쿨다운 없이 쓴다. */
@Composable
private fun BagCard(state: GameState, onUse: (CatId, ShopItem) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F1))) {
        Column(Modifier.padding(16.dp)) {
            Text("🎒 가방", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            for (item in ShopItem.entries.filter { it.kind == ShopKind.CONSUMABLE }) {
                val n = ShopRules.bagCount(state.game, item)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${item.icon} ${item.label} ${n}개", Modifier.weight(1f), fontSize = 14.sp)
                    for (id in CatId.entries) {
                        TextButton(onClick = { onUse(id, item) }, enabled = n > 0) { Text("${id.catName}에게") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepsCard(state: GameState) {
    val context = LocalContext.current
    val today = remember(state.game.stepDay) { epochDay(System.currentTimeMillis()) }
    val steps = StepRules.todaySteps(state.game, today)
    val exp = if (state.game.stepDay == today) state.game.stepExpToday else 0
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F1))) {
        Column(Modifier.padding(16.dp)) {
            Text("👣 오늘 걸음 수  ${"%,d".format(steps)}보", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (steps / StepRules.GOAL_STEPS.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Color(0xFF5BAA6A),
                trackColor = Color(0xFFE8DED2),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "걸음 경험치 오늘 $exp / ${StepRules.DAILY_EXP_CAP} (100보마다 두 마리 +2) · 1만 보 달성 시 골드 ${StepRules.GOAL_GOLD}",
                fontSize = 12.sp,
                color = Color.Gray,
            )
            when {
                !StepTracker.available(context) -> Text("이 폰에는 걸음 센서가 없어요.", fontSize = 12.sp, color = Color(0xFFB3261E))
                !StepTracker.hasPermission(context) -> Text(
                    "걸음 수를 세려면 '신체 활동' 권한을 허용해 주세요 (설정 → 애플리케이션 → 쿵덕 → 권한).",
                    fontSize = 12.sp,
                    color = Color(0xFFB3261E),
                )
                else -> Text("걸음은 앱을 열거나 화면 위 고양이가 떠 있을 때 반영돼요.", fontSize = 12.sp, color = Color.Gray)
            }
        }
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
