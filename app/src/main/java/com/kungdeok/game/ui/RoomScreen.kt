package com.kungdeok.game.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.data.GameState
import com.kungdeok.game.logic.CareAction
import com.kungdeok.game.logic.CareRules
import com.kungdeok.game.logic.StatusRules
import com.kungdeok.game.model.CatId
import com.kungdeok.game.model.Form
import com.kungdeok.game.model.Stage

/** 돌봄 대상 선택: 쿵, 노덕, 둘 다 */
enum class Target(val label: String, val cats: List<CatId>) {
    KUNG("쿵", listOf(CatId.KUNG)),
    NODEOK("노덕", listOf(CatId.NODEOK)),
    BOTH("둘 다", listOf(CatId.KUNG, CatId.NODEOK)),
}

@Composable
fun RoomScreen(
    state: GameState,
    now: Long,
    target: Target,
    onTarget: (Target) -> Unit,
    onCare: (CareAction) -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        TopInfo(state)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Wall, 0.42f to Wall, 0.42f to Floor, 1f to Floor)),
        ) {
            RoomDecor()
            Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                for (id in CatId.entries) {
                    RoomCat(
                        cat = state.cat(id),
                        id = id,
                        now = now,
                        selected = id in target.cats,
                        onClick = { onTarget(if (id == CatId.KUNG) Target.KUNG else Target.NODEOK) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) { extra() }
        }
        CarePanel(state, now, target, onTarget, onCare)
    }
}

/** 벽의 창문과 바닥 러그 */
@Composable
private fun RoomDecor() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val winW = w * 0.34f
        val winH = h * 0.24f
        val winX = w * 0.5f - winW / 2
        val winY = h * 0.07f
        drawRoundRect(Color(0xFF9C7A5B), Offset(winX - 8, winY - 8), Size(winW + 16, winH + 16), CornerRadius(10f))
        drawRoundRect(Color(0xFFBFE3F5), Offset(winX, winY), Size(winW, winH), CornerRadius(6f))
        drawLine(Color(0xFF9C7A5B), Offset(winX + winW / 2, winY), Offset(winX + winW / 2, winY + winH), 6f)
        drawLine(Color(0xFF9C7A5B), Offset(winX, winY + winH / 2), Offset(winX + winW, winY + winH / 2), 6f)
        drawOval(Color(0x33C0583A), Offset(w * 0.08f, h * 0.70f), Size(w * 0.84f, h * 0.24f))
        drawOval(Color(0x22FFFFFF), Offset(w * 0.18f, h * 0.74f), Size(w * 0.64f, h * 0.16f))
    }
}

@Composable
private fun TopInfo(state: GameState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🪙 ${state.game.gold}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.weight(1f))
        Text("사이좋음 ", fontSize = 14.sp)
        Text("❤", color = HeartPink, fontSize = 18.sp)
        Text(" ${state.game.friendship}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

/** 상태가 낮을 때 머리 위 말풍선 문구 (SPEC 8). */
fun bubbleText(cat: CatEntity, now: Long): String? = when {
    cat.isAsleep(now) -> "Zzz…"
    cat.hunger < StatusRules.BUBBLE -> "배고파"
    cat.mood < StatusRules.BUBBLE -> "놀아줘"
    cat.sleepiness < StatusRules.BUBBLE -> "졸려"
    else -> null
}

@Composable
private fun RoomCat(
    cat: CatEntity,
    id: CatId,
    now: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = Form.of(id, cat.stage)
    val sprite = rememberSprite(form.largeAsset)
    val asleep = cat.isAsleep(now)
    val sulking = cat.mood < StatusRules.LOW && !asleep
    val messy = cat.cleanliness < StatusRules.LOW

    val transition = rememberInfiniteTransition(label = "bob")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (asleep) 3f else 8f,
        animationSpec = infiniteRepeatable(tween(if (asleep) 1600 else 600), RepeatMode.Reverse),
        label = "bob",
    )
    // 진화할수록 방 안에서도 덩치가 커진다 (유년기 60% → 궁극체 100% 폭).
    val widthFraction = 0.6f + 0.4f * (Stage.of(cat.stage).overlayDp - 40) / 48f

    Column(
        modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bubble = bubbleText(cat, now)
        Box(Modifier.height(36.dp), contentAlignment = Alignment.BottomCenter) {
            if (bubble != null) {
                Text(
                    bubble,
                    Modifier
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, Choco, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 14.sp,
                )
            }
        }
        Box(contentAlignment = Alignment.Center) {
            Image(
                bitmap = sprite,
                contentDescription = form.monName,
                modifier = Modifier
                    .fillMaxWidth(widthFraction.coerceAtMost(1f))
                    .aspectRatio(1f)
                    .graphicsLayer {
                        translationY = -bob
                        // 기분이 바닥이면 등을 돌리고 앉는다: 좌우 반전 + 살짝 작게
                        scaleX = if (sulking) -0.92f else 1f
                        scaleY = if (sulking) 0.92f else 1f
                        rotationZ = if (asleep) -8f else 0f
                    }
                    .alpha(if (asleep) 0.85f else 1f),
            )
            if (messy) Text("💨", Modifier.align(Alignment.TopEnd), fontSize = 22.sp)
        }
        Text(
            "${form.monName}  Lv.${cat.level}",
            Modifier
                .padding(top = 4.dp)
                .background(if (selected) Choco else Color(0x66FFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
            color = if (selected) Color.White else Choco,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        if (sulking) Text("(삐져서 등 돌림)", fontSize = 11.sp, color = Choco)
    }
}

@Composable
private fun CarePanel(
    state: GameState,
    now: Long,
    target: Target,
    onTarget: (Target) -> Unit,
    onCare: (CareAction) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Target.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = target == t,
                    onClick = { onTarget(t) },
                    shape = SegmentedButtonDefaults.itemShape(i, Target.entries.size),
                ) { Text(t.label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        CareAction.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { action ->
                    // 대상 중 가장 오래 기다려야 하는 쿨다운을 보여 준다.
                    val left = target.cats.maxOf { CareRules.cooldownLeft(state.cat(it), action, now) }
                    val sleeping = CareRules.blockedBySleep(action) &&
                        target.cats.all { state.cat(it).isAsleep(now) }
                    OutlinedButton(
                        onClick = { onCare(action) },
                        modifier = Modifier.weight(1f).height(64.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${action.icon} ${action.label}", fontSize = 14.sp, textAlign = TextAlign.Center)
                            val sub = when {
                                sleeping -> "자는 중"
                                left > 0 && action != CareAction.PET -> formatDuration(left)
                                else -> null
                            }
                            if (sub != null) Text(sub, fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
