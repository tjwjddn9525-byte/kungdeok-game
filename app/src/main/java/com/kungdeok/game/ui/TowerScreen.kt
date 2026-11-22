package com.kungdeok.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kungdeok.game.data.GameState
import com.kungdeok.game.logic.Battle
import com.kungdeok.game.logic.BattleAction
import com.kungdeok.game.logic.BattleResult
import com.kungdeok.game.logic.BattleRules
import com.kungdeok.game.logic.Fighter
import com.kungdeok.game.logic.Reward

/** 캣타워 (SPEC 9, 3단계): 층을 고르고 전투 */
@Composable
fun TowerScreen(
    state: GameState,
    now: Long,
    battle: Battle?,
    reward: Reward?,
    onStart: (Int) -> Unit,
    onAction: (BattleAction) -> Unit,
    onLeave: () -> Unit,
    alreadyPlayed: Boolean = false,
    onPlayed: () -> Unit = {},
) {
    if (battle != null) {
        BattleView(battle, reward, state.game.snack, alreadyPlayed, onPlayed, onAction, onLeave)
    } else {
        TowerLobby(state, now, onStart)
    }
}

@Composable
private fun TowerLobby(state: GameState, now: Long, onStart: (Int) -> Unit) {
    val best = state.game.towerFloor
    val block = BattleRules.entryBlock(state.kung, state.nodeok, now, state.game.isFused(now))
    val floors = (best + 1 downTo 1).toList()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("🗼 캣타워", fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(4.dp))
            Text(
                "지금까지 ${best}층까지 올라왔어요. 층마다 적이 있고, ${BattleRules.BOSS_EVERY}층마다 보스가 나와요.\n" +
                    "도전하면 두 마리 모두 배고픔·졸림이 조금 줄어요. 컨디션이 좋을수록 세져요.",
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (block != null) {
                Text("⚠ $block", color = Color(0xFFB3261E), fontSize = 14.sp, modifier = Modifier.padding(4.dp))
            }
        }
        items(floors) { floor ->
            val e = BattleRules.enemy(floor)
            val r = BattleRules.reward(floor, best)
            Card(colors = CardDefaults.cardColors(containerColor = if (floor == best + 1) Color(0xFFFFF1D6) else Color(0xFFFFF9F1))) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArtIcon(e.icon, 52.dp, asset = e.asset)
                    Spacer(Modifier.padding(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${floor}층  ${e.name}${if (e.boss) " 👑보스" else ""}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        Text("체력 ${e.maxHp} · 공격 ${e.atk}", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            (if (floor == best + 1) "처음 도전! " else "다시 도전 (보상 절반) ") + "🪙${r.gold} · 경험치 ${r.exp}",
                            fontSize = 12.sp,
                        )
                    }
                    Button(onClick = { onStart(floor) }, enabled = block == null) { Text("도전") }
                }
            }
        }
    }
}

@Composable
private fun BattleView(
    b: Battle,
    reward: Reward?,
    snacks: Int,
    alreadyPlayed: Boolean,
    onPlayed: () -> Unit,
    onAction: (BattleAction) -> Unit,
    onLeave: () -> Unit,
) {
    var playing by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF2B2140))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🗼 ${b.floor}층  ${b.enemy.name}${if (b.enemy.boss) " 👑" else ""}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            if (b.result == null && !playing) TextButton(onClick = onLeave) { Text("도망가기", color = Color(0xFFFFB4AB)) }
        }

        // 아래쪽 마루에서 실제로 싸우는 무대
        BattleStage(
            b,
            alreadyPlayed = alreadyPlayed,
            onPlaying = {
                playing = it
                if (!it) onPlayed()
            },
            modifier = Modifier.weight(1f),
        )

        // 기록
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(Color(0x33000000), RoundedCornerShape(8.dp))
                .padding(8.dp),
        ) {
            val recent = b.log.takeLast(3)
            recent.forEachIndexed { i, line ->
                Text(line, color = Color.White.copy(alpha = if (i == recent.lastIndex) 1f else 0.65f), fontSize = 13.sp, maxLines = 1)
            }
        }

        if (b.result != null && !playing) {
            ResultCard(b.result, reward, onLeave)
        } else {
            val actions = listOf(
                BattleAction.ATTACK to "🐾 냥냥펀치",
                BattleAction.KUNG_SKILL to "⭐ ${b.kung.form.skill}" + cd(b.kung),
                BattleAction.NODEOK_SKILL to "💥 ${b.nodeok.form.skill}" + cd(b.nodeok),
                BattleAction.SNACK to "🍤 간식 ($snacks)",
            )
            actions.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (a, label) ->
                        Button(
                            onClick = { onAction(a) },
                            enabled = !playing && b.result == null && BattleRules.canUse(b, a, snacks),
                            modifier = Modifier.weight(1f).height(52.dp),
                            contentPadding = PaddingValues(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = Color(0x33FFFFFF),
                                disabledContentColor = Color(0x99FFFFFF),
                            ),
                        ) { Text(label, fontSize = 13.sp, maxLines = 2) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

private fun cd(f: Fighter) = if (f.skillCooldown > 0) " (${f.skillCooldown})" else ""

@Composable
private fun ResultCard(result: BattleResult, reward: Reward?, onLeave: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F1)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (result == BattleResult.WIN) {
                Text("🎉 승리!", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                if (reward != null) {
                    Text("🪙 ${reward.gold} · 두 마리 경험치 +${reward.exp}", fontSize = 15.sp)
                    if (reward.firstClear) Text("새로운 층을 깼어요! 다음 층이 열렸어요.", fontSize = 13.sp, color = Color(0xFF5BAA6A))
                }
            } else {
                Text("😿 졌어요", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("두 마리 기분 -5. 더 키워서 다시 도전해요.", fontSize = 14.sp)
            }
            OutlinedButton(onClick = onLeave, modifier = Modifier.padding(top = 8.dp)) { Text("돌아가기") }
        }
    }
}
