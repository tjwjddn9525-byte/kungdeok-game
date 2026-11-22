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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.BiasAlignment
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.kungdeok.game.logic.ShopItem
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
import com.kungdeok.game.logic.RelationshipRules
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
    anim: CareAnimEvent? = null,
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
            RoomFurniture(state.game.placed, inBox = boxSitter(state, now))
            Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                if (state.game.isFused(now)) {
                    FusedCat(state.game.fusionUntil - now)
                } else for (id in CatId.entries) {
                    val sitter = boxSitter(state, now)
                    RoomCat(
                        anim = anim?.takeIf { id in it.cats },
                        inBox = sitter?.id == id.id,
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

/** 상점에서 산 가구 (3단계). [inBox] 가 있으면 박스 안에 그 고양이가 들어가 있다. */
@Composable
private fun RoomFurniture(placed: Set<String>, inBox: CatEntity?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        @Composable
        fun item(id: String, x: Float, y: Float, size: Int) {
            val shop = ShopItem.byId(id) ?: return
            if (id !in placed) return
            ArtIcon(shop.icon, size.dp, Modifier.offset(w * x, h * y))
        }
        item("picture", 0.05f, 0.08f, 56)
        item("plant", 0.80f, 0.36f, 60)
        item("scratcher", 0.03f, 0.40f, 76)
        item("cushion", 0.38f, 0.50f, 64)
        if ("box" in placed) {
            Box(Modifier.offset(w * 0.70f, h * 0.53f)) {
                if (inBox != null) {
                    val form = Form.of(CatId.of(inBox.id), inBox.stage)
                    Image(
                        rememberSprite(form.smallAsset),
                        form.monName,
                        Modifier.size(44.dp).offset(y = (-22).dp).align(Alignment.TopCenter),
                        filterQuality = FilterQuality.None,
                    )
                }
                ArtIcon("📦", 72.dp)
            }
        }
    }
}

/** 박스가 있으면 기분 좋고 깨어 있는 고양이가 30분 중 10분쯤 박스에 들어가 있다 (실제 버릇). */
fun boxSitter(state: GameState, now: Long): CatEntity? {
    if ("box" !in state.game.placed) return null
    val slot = (now / (10 * 60_000L)) % 3
    val cat = when (slot) {
        0L -> state.kung
        1L -> state.nodeok
        else -> return null
    }
    return cat.takeIf { !it.isAsleep(now) && it.mood >= StatusRules.BUBBLE }
}

/** 방 배경 그림 (벽·창문·바닥·러그). 방 칸 비율에 맞게 잘라서 바닥이 아래쪽에 오게 한다. */
@Composable
private fun RoomDecor() {
    Image(
        rememberSprite(Art.BG_ROOM),
        null,
        Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(0f, 0.3f),
    )
}

@Composable
private fun TopInfo(state: GameState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtIcon("🪙", 22.dp)
        Text(" ${state.game.gold}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.weight(1f))
        Text("사이좋음 ", fontSize = 14.sp)
        ArtIcon("❤", 20.dp)
        Text(" ${state.game.friendship}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            " (${RelationshipRules.closeness(state.game.friendship).label})",
            fontSize = 13.sp,
            color = Choco,
        )
    }
}

/** 합체 중에는 쿵덕몬 한 마리가 방에 있다. */
@Composable
private fun FusedCat(leftMs: Long) {
    val sprite = rememberSprite(Form.FUSION.largeAsset)
    val bob by rememberInfiniteTransition(label = "fusion").animateFloat(
        0f, 10f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            sprite,
            Form.FUSION.monName,
            Modifier.fillMaxWidth(0.7f).aspectRatio(1f).graphicsLayer { translationY = -bob },
        )
        Text(
            "${Form.FUSION.monName}  (${formatDuration(leftMs)} 남음)",
            Modifier.padding(top = 4.dp).background(Choco, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun RoomCat(
    anim: CareAnimEvent?,
    inBox: Boolean,
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
    // 돌봄 장면 (밥 먹기, 털실 놀이 등). 탭을 옮겼다 와도 남은 부분만 이어서 튼다.
    val progress = remember { Animatable(1f) }
    LaunchedEffect(anim?.id) {
        val a = anim ?: return@LaunchedEffect
        val dur = CareAnimation.durationMs(a.action)
        val elapsed = System.currentTimeMillis() - a.startedAt
        if (elapsed >= dur) return@LaunchedEffect
        progress.snapTo(elapsed.toFloat() / dur)
        progress.animateTo(1f, tween((dur - elapsed).toInt(), easing = LinearEasing))
    }
    val playing = anim != null && progress.value < 1f
    val pose = if (playing) CareAnimation.pose(anim!!.action, progress.value) else CarePose()
    val props = if (playing) CareAnimation.props(anim!!.action, progress.value) else emptyList()
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // 진화할수록 방 안에서도 덩치가 커진다 (유년기 60% → 궁극체 100% 폭).
    val widthFraction = 0.6f + 0.4f * (Stage.of(cat.stage).overlayDp - 40) / 48f

    Column(
        modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bubble = StatusRules.bubble(cat, now)
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
                    .onSizeChanged { sizePx = it }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationX = pose.dx * size.width
                        translationY = (if (playing) 0f else -bob) + pose.dy * size.height
                        // 기분이 바닥이면 등을 돌리고 앉는다: 좌우 반전 + 살짝 작게
                        val base = if (sulking && !playing) 0.92f else 1f
                        scaleX = base * pose.sx * (if (sulking && !playing) -1f else 1f) * (if (pose.flip) -1f else 1f)
                        scaleY = base * pose.sy
                        rotationZ = (if (asleep && !playing) -8f else 0f) + pose.rot
                    }
                    .alpha(if (inBox && !playing) 0f else if (asleep && !playing) 0.85f else 1f),
            )
            // 장면 소품: 밥그릇, 털실, 빗, "냠" 같은 글자
            for (prop in props) {
                val propAsset = Art.asset(prop.text)
                if (propAsset != null) {
                    Image(
                        rememberSprite(propAsset),
                        null,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .size(with(density) { (prop.size * sizePx.width * 1.25f).coerceAtLeast(1f).toDp() })
                            .graphicsLayer {
                                translationX = prop.x * sizePx.width
                                translationY = prop.y * sizePx.height
                                rotationZ = prop.rot
                                alpha = prop.alpha.coerceIn(0f, 1f)
                            },
                    )
                    continue
                }
                Text(
                    prop.text,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            translationX = prop.x * sizePx.width
                            translationY = prop.y * sizePx.height
                            rotationZ = prop.rot
                            alpha = prop.alpha.coerceIn(0f, 1f)
                        },
                    fontSize = with(density) { (prop.size * sizePx.width).coerceAtLeast(1f).toSp() },
                    color = Choco,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (messy && !inBox && !playing) ArtIcon("💨", 30.dp, Modifier.align(Alignment.TopEnd))
            ShopItem.byId(cat.accessory)?.takeIf { !inBox }?.let {
                ArtIcon(
                    it.icon,
                    36.dp,
                    Modifier.align(Alignment.TopCenter).graphicsLayer {
                        translationX = pose.dx * sizePx.width
                        translationY = (if (playing) 0f else -bob) + pose.dy * sizePx.height
                    },
                )
            }
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
        if (inBox) Text("(박스에 쏙 들어감)", fontSize = 11.sp, color = Choco)
        else if (sulking) Text("(삐져서 등 돌림)", fontSize = 11.sp, color = Choco)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ArtIcon(action.icon, 22.dp)
                                Text(" ${action.label}", fontSize = 14.sp, textAlign = TextAlign.Center)
                            }
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
