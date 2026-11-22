package com.kungdeok.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kungdeok.game.logic.Battle
import com.kungdeok.game.logic.BattleEvent
import com.kungdeok.game.model.CatId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 무대 위 배우 한 명(고양이 또는 적)의 움직임 */
private class Actor {
    val x = Animatable(0f)
    val y = Animatable(0f)
    val rot = Animatable(0f)
    val sx = Animatable(1f)
    val sy = Animatable(1f)
    val flash = Animatable(0f)
    val alpha = Animatable(1f)

    suspend fun reset() {
        x.snapTo(0f); y.snapTo(0f); rot.snapTo(0f); sx.snapTo(1f); sy.snapTo(1f); flash.snapTo(0f); alpha.snapTo(1f)
    }
}

/** 떠오르는 글자/효과 (데미지 숫자, 💥, ⭐ …). x, y 는 무대 좌표(px) */
private data class Fx(val id: Int, val text: String, val x: Float, val y: Float, val sizeSp: Int, val color: Color, val rise: Boolean)

/**
 * 캣타워 전투 무대: 화면 아래쪽 마루에서 고양이 둘과 적이 실제로 부딪친다.
 * 한 턴의 [Battle.events] 를 순서대로 장면으로 튼다. 재생 중에는 [onPlaying] true.
 * 새 그림 없이 지금 그림을 달리고, 뛰고, 기울이고, 찌그러뜨려서 움직인다.
 */
@Composable
fun BattleStage(
    b: Battle,
    alreadyPlayed: Boolean,
    onPlaying: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kung = remember { Actor() }
    val nodeok = remember { Actor() }
    val enemy = remember { Actor() }
    val shake = remember { Animatable(0f) }
    val banner = remember { Animatable(0f) }
    var bannerText by remember { mutableStateOf("") }
    val fx = remember { mutableStateListOf<Fx>() }
    var fxId by remember { mutableIntStateOf(0) }
    var stunnedShown by remember { mutableStateOf(b.enemyStunned) }

    // 화면에 보이는 체력 (장면에 맞춰 줄어든다)
    var hpKung by remember { mutableIntStateOf(b.kung.hp) }
    var hpNodeok by remember { mutableIntStateOf(b.nodeok.hp) }
    var hpEnemy by remember { mutableIntStateOf(b.enemy.hp) }

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(0f to Color(0xFF3B2D5C), 0.7f to Color(0xFF5B4377), 0.7f to Color(0xFF8A5A3B), 1f to Color(0xFF6B4329)),
                RoundedCornerShape(12.dp),
            ),
    ) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val ground = h * 0.88f
        // 무대 높이에 맞춰 덩치를 키운다 (너무 납작한 화면에서는 폭 기준)
        val catSize = minOf(w * 0.32f, h * 0.42f)
        val enemySize = minOf(w * if (b.enemy.boss) 0.4f else 0.32f, h * if (b.enemy.boss) 0.5f else 0.42f)
        val baseX = mapOf(CatId.KUNG to w * 0.17f, CatId.NODEOK to w * 0.43f)
        val enemyX = w * 0.8f
        fun actor(id: CatId) = if (id == CatId.KUNG) kung else nodeok

        fun CoroutineScope.pop(text: String, x: Float, y: Float, sizeSp: Int, color: Color, rise: Boolean = true) {
            val f = Fx(fxId++, text, x, y, sizeSp, color, rise)
            fx += f
            launch {
                delay(900)
                fx.remove(f)
            }
        }

        // ---- 장면 재생 ----
        LaunchedEffect(b.turn, b.result) {
            if (alreadyPlayed || b.events.isEmpty()) {
                hpKung = b.kung.hp; hpNodeok = b.nodeok.hp; hpEnemy = b.enemy.hp
                stunnedShown = b.enemyStunned
                if (b.enemy.hp <= 0) { enemy.alpha.snapTo(0f) }
                return@LaunchedEffect
            }
            onPlaying(true)
            coroutineScope {
                for (e in b.events) {
                    when (e) {
                        is BattleEvent.CatHit -> {
                            val a = actor(e.cat)
                            val reach = enemyX - enemySize * 0.55f - catSize * 0.35f - baseX.getValue(e.cat)
                            if (!e.skill) {
                                // 달려가서 냥냥펀치
                                launch { a.y.animateTo(-catSize * 0.18f, tween(130)); a.y.animateTo(0f, tween(130)) }
                                a.x.animateTo(reach, tween(260, easing = FastOutSlowInEasing))
                                a.rot.animateTo(12f, tween(70))
                                impact(enemy, shake, 6f)
                                pop("💥", enemyX - enemySize * 0.35f, ground - enemySize * 0.65f, 38, Color.White, rise = false)
                                pop("-${e.dmg}", enemyX, ground - enemySize * 1.05f, 22, Color(0xFFFFE082))
                                hpEnemy = e.enemyHp
                                launch { a.rot.animateTo(0f, tween(150)) }
                                a.x.animateTo(0f, tween(300, easing = LinearOutSlowInEasing))
                            } else if (e.cat == CatId.KUNG) {
                                // 쿵 기술: 높이 솟구쳐 회전하며 내리찍기 (메테오)
                                launch { showBanner(banner, b.kung.form.skill) { bannerText = it } }
                                launch { a.rot.animateTo(360f, tween(520)); a.rot.snapTo(0f) }
                                launch { a.x.animateTo(reach, tween(520, easing = FastOutSlowInEasing)) }
                                a.y.animateTo(-h * 0.55f, tween(320, easing = LinearOutSlowInEasing))
                                pop("⭐", baseX.getValue(e.cat) + reach * 0.5f, ground - h * 0.6f, 22, Color.White, rise = false)
                                pop("⭐", baseX.getValue(e.cat) + reach * 0.8f, ground - h * 0.5f, 18, Color.White, rise = false)
                                a.y.animateTo(0f, tween(200, easing = FastOutLinearInEasing))
                                launch { a.sy.animateTo(0.75f, tween(60)); a.sy.animateTo(1f, tween(200)) }
                                impact(enemy, shake, 18f)
                                pop("💥", enemyX - enemySize * 0.2f, ground - enemySize * 0.75f, 56, Color.White, rise = false)
                                pop("-${e.dmg}", enemyX, ground - enemySize * 1.15f, 30, Color(0xFFFFD54F))
                                hpEnemy = e.enemyHp
                                a.x.animateTo(0f, tween(380, easing = LinearOutSlowInEasing))
                            } else {
                                // 노덕 기술: 묵직하게 걸어가 몸통으로 깔고 앉기
                                launch { showBanner(banner, b.nodeok.form.skill) { bannerText = it } }
                                launch { a.sx.animateTo(1.15f, tween(300)) }
                                a.x.animateTo(reach * 0.8f, tween(420, easing = FastOutSlowInEasing))
                                a.y.animateTo(-catSize * 0.45f, tween(200, easing = LinearOutSlowInEasing))
                                launch { a.x.animateTo(reach + enemySize * 0.25f, tween(180)) }
                                a.y.animateTo(0f, tween(180, easing = FastOutLinearInEasing))
                                launch { enemy.sy.animateTo(0.55f, tween(80)) }
                                launch { a.sy.animateTo(0.8f, tween(80)); a.sy.animateTo(1f, tween(250)) }
                                impact(enemy, shake, 14f)
                                pop("쿵!", enemyX - enemySize * 0.55f, ground - enemySize * 0.45f, 26, Color.White, rise = false)
                                pop("-${e.dmg}", enemyX, ground - enemySize * 1.15f, 28, Color(0xFFFFD54F))
                                hpEnemy = e.enemyHp
                                delay(250)
                                launch { enemy.sy.animateTo(1f, tween(300)) }
                                launch { a.sx.animateTo(1f, tween(300)) }
                                a.x.animateTo(0f, tween(400, easing = LinearOutSlowInEasing))
                            }
                            delay(120)
                        }
                        is BattleEvent.Heal -> {
                            val a = actor(e.cat)
                            val cx = baseX.getValue(e.cat)
                            // 간식이 위에서 날아와 쏙
                            val treat = Fx(fxId++, "🍤", cx, ground - catSize * 1.1f, 26, Color.White, rise = false)
                            fx += treat
                            a.y.animateTo(-catSize * 0.3f, tween(180))
                            fx.remove(treat)
                            a.y.animateTo(0f, tween(180))
                            pop("+${e.amount}", cx, ground - catSize * 1.2f, 22, Color(0xFFA5D6A7))
                            pop("✨", cx + catSize * 0.3f, ground - catSize * 0.8f, 18, Color.White)
                            if (e.cat == CatId.KUNG) hpKung = e.hp else hpNodeok = e.hp
                            delay(300)
                        }
                        is BattleEvent.EnemyHit -> {
                            val a = actor(e.target)
                            // 적이 달려들어 들이받는다
                            val reach = baseX.getValue(e.target) + catSize * 0.45f + enemySize * 0.35f - enemyX
                            launch { enemy.rot.animateTo(-15f, tween(200)) }
                            enemy.x.animateTo(reach, tween(280, easing = FastOutSlowInEasing))
                            launch { a.flash.snapTo(1f); a.flash.animateTo(0f, tween(350)) }
                            launch { a.x.animateTo(-catSize * 0.25f, tween(90)); a.x.animateTo(0f, tween(250)) }
                            launch { a.rot.animateTo(-14f, tween(90)); a.rot.animateTo(0f, tween(250)) }
                            launch { shake.snapTo(8f); shake.animateTo(0f, tween(250)) }
                            pop("-${e.dmg}", baseX.getValue(e.target), ground - catSize * 1.15f, 22, Color(0xFFFF8A80))
                            if (e.target == CatId.KUNG) hpKung = e.hp else hpNodeok = e.hp
                            launch { enemy.rot.animateTo(0f, tween(250)) }
                            enemy.x.animateTo(0f, tween(330, easing = LinearOutSlowInEasing))
                            if (e.hp <= 0) pop("쓰러짐…", baseX.getValue(e.target), ground - catSize * 1.3f, 16, Color.White)
                            delay(150)
                        }
                        BattleEvent.Stunned -> stunnedShown = true
                        BattleEvent.EnemyDazed -> {
                            // 기절: 비틀비틀
                            repeat(2) {
                                enemy.rot.animateTo(10f, tween(150))
                                enemy.rot.animateTo(-10f, tween(150))
                            }
                            enemy.rot.animateTo(0f, tween(120))
                            stunnedShown = false
                        }
                        BattleEvent.EnemyDown -> {
                            // 빙글빙글 돌며 작아져 사라진다, 고양이들은 만세 점프
                            launch { enemy.rot.animateTo(720f, tween(700)) }
                            launch { enemy.sx.animateTo(0.2f, tween(700)) }
                            launch { enemy.sy.animateTo(0.2f, tween(700)) }
                            launch { enemy.y.animateTo(-h * 0.3f, tween(700)) }
                            enemy.alpha.animateTo(0f, tween(700))
                            for (c in listOf(kung, nodeok)) launch {
                                repeat(2) {
                                    c.y.animateTo(-catSize * 0.3f, tween(160))
                                    c.y.animateTo(0f, tween(160))
                                }
                            }
                            delay(700)
                        }
                    }
                }
            }
            hpKung = b.kung.hp; hpNodeok = b.nodeok.hp; hpEnemy = b.enemy.hp
            stunnedShown = b.enemyStunned
            onPlaying(false)
        }

        // ---- 그리기 ----
        val shakeX = shake.value
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = shakeX }) {
            // 캣타워 안 배경: 달 뜬 창문, 벽 선반, 마루 판자
            Canvas(Modifier.fillMaxSize()) {
                val sw = size.width
                val sh = size.height
                val floorTop = sh * 0.7f
                // 창문과 달
                val winL = sw * 0.06f; val winT = sh * 0.08f; val winW = sw * 0.24f; val winH = sh * 0.3f
                drawRect(Color(0xFF7A5A86), Offset(winL - 6f, winT - 6f), Size(winW + 12f, winH + 12f))
                drawRect(Color(0xFF1E2A55), Offset(winL, winT), Size(winW, winH))
                drawCircle(Color(0xFFFFF3B0), radius = winW * 0.16f, center = Offset(winL + winW * 0.68f, winT + winH * 0.32f))
                drawLine(Color(0xFF7A5A86), Offset(winL + winW / 2, winT), Offset(winL + winW / 2, winT + winH), 5f)
                drawLine(Color(0xFF7A5A86), Offset(winL, winT + winH / 2), Offset(winL + winW, winT + winH / 2), 5f)
                for ((sx, sy) in listOf(0.2f to 0.2f, 0.35f to 0.7f, 0.12f to 0.8f)) {
                    drawCircle(Color.White, 2.5f, Offset(winL + winW * sx, winT + winH * sy))
                }
                // 벽 선반 (캣타워 발판)
                for ((x, y, len) in listOf(Triple(0.55f, 0.18f, 0.3f), Triple(0.68f, 0.42f, 0.26f), Triple(0.4f, 0.5f, 0.18f))) {
                    drawRect(Color(0xFF9C6B47), Offset(sw * x, sh * y), Size(sw * len, sh * 0.025f))
                    drawRect(Color(0x55000000), Offset(sw * x, sh * y + sh * 0.025f), Size(sw * len, sh * 0.012f))
                }
                // 기둥
                drawRect(Color(0xFFC9A27A), Offset(sw * 0.93f, 0f), Size(sw * 0.035f, floorTop))
                // 마루 판자
                for (i in 1..4) {
                    val y = floorTop + (sh - floorTop) * i / 5f
                    drawLine(Color(0x33000000), Offset(0f, y), Offset(sw, y), 2f)
                }
            }

            for (id in CatId.entries) {
                val f = b.fighter(id)
                val hp = if (id == CatId.KUNG) hpKung else hpNodeok
                CatActor(
                    actor(id), f.form.largeAsset, hp, f.maxHp,
                    centerX = baseX.getValue(id), ground = ground, size = catSize,
                )
            }
            EnemyActor(enemy, b.enemy.icon, hpEnemy, b.enemy.maxHp, enemyX, ground, enemySize, stunnedShown)

            for (f in fx) FxText(f)

            if (banner.value > 0f) {
                Text(
                    bannerText,
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .graphicsLayer { alpha = banner.value; scaleX = 0.8f + 0.2f * banner.value; scaleY = scaleX }
                        .background(Color(0xCC000000), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    color = Color(0xFFFFE082),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }
    }
}

private suspend fun impact(enemy: Actor, shake: Animatable<Float, AnimationVector1D>, power: Float) = coroutineScope {
    launch { enemy.flash.snapTo(1f); enemy.flash.animateTo(0f, tween(300)) }
    launch { enemy.x.animateTo(power * 2.5f, tween(70)); enemy.x.animateTo(0f, tween(260)) }
    launch { enemy.rot.animateTo(power * 0.6f, tween(70)); enemy.rot.animateTo(0f, tween(260)) }
    launch { shake.snapTo(power); shake.animateTo(0f, tween(260)) }
}

private suspend fun showBanner(banner: Animatable<Float, AnimationVector1D>, text: String, set: (String) -> Unit) {
    set("$text!")
    banner.snapTo(0f)
    banner.animateTo(1f, tween(180))
    delay(900)
    banner.animateTo(0f, tween(300))
}

@Composable
private fun CatActor(a: Actor, asset: String, hp: Int, maxHp: Int, centerX: Float, ground: Float, size: Float) {
    val density = LocalDensity.current
    val sizeDp = with(density) { size.toDp() }
    val fainted = hp <= 0
    val gray = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    Box(
        Modifier
            .offset { IntOffset((centerX - size / 2).roundToInt(), (ground - size).roundToInt()) }
            .size(sizeDp),
    ) {
        Image(
            rememberSprite(asset),
            null,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    translationX = a.x.value
                    translationY = a.y.value
                    // 쓰러지면 옆으로 눕는다
                    rotationZ = a.rot.value + if (fainted) -80f else 0f
                    scaleX = a.sx.value
                    scaleY = a.sy.value
                    alpha = if (fainted) 0.55f else 1f
                },
            colorFilter = if (fainted) gray else null,
        )
        // 맞으면 빨갛게 번쩍
        if (a.flash.value > 0f) {
            Image(
                rememberSprite(asset), null,
                Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    translationX = a.x.value; translationY = a.y.value; rotationZ = a.rot.value
                    alpha = a.flash.value * 0.7f
                },
                colorFilter = ColorFilter.tint(Color.Red),
            )
        }
        StageHpBar(hp, maxHp, Color(0xFF81C784), Modifier.align(Alignment.TopCenter).offset(y = (-14).dp).width(sizeDp * 0.9f))
    }
}

@Composable
private fun EnemyActor(a: Actor, icon: String, hp: Int, maxHp: Int, centerX: Float, ground: Float, size: Float, stunned: Boolean) {
    val density = LocalDensity.current
    val sizeDp = with(density) { size.toDp() }
    val fontSp = with(density) { (size * 0.8f).toSp() }
    Box(
        Modifier
            .offset { IntOffset((centerX - size / 2).roundToInt(), (ground - size).roundToInt()) }
            .size(sizeDp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            icon,
            Modifier.graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationX = a.x.value
                translationY = a.y.value
                rotationZ = a.rot.value
                scaleX = a.sx.value
                scaleY = a.sy.value
                alpha = a.alpha.value
            },
            fontSize = fontSp,
        )
        if (a.flash.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = a.x.value; alpha = a.flash.value * 0.35f }
                    .background(Color.Red, RoundedCornerShape(50)),
            )
        }
        if (stunned && a.alpha.value > 0f) {
            Text("💫", Modifier.align(Alignment.TopCenter).offset(y = (-6).dp), fontSize = 20.sp)
        }
        if (a.alpha.value > 0f) {
            StageHpBar(hp, maxHp, Color(0xFFE57373), Modifier.align(Alignment.TopCenter).offset(y = (-16).dp).width(sizeDp))
        }
    }
}

@Composable
private fun StageHpBar(hp: Int, max: Int, color: Color, modifier: Modifier) {
    LinearProgressIndicator(
        progress = { (hp.toFloat() / max).coerceIn(0f, 1f) },
        modifier = modifier.padding(horizontal = 2.dp),
        color = color,
        trackColor = Color(0x55FFFFFF),
        drawStopIndicator = {},
    )
}

@Composable
private fun FxText(f: Fx) {
    val t = remember(f.id) { Animatable(0f) }
    LaunchedEffect(f.id) { t.animateTo(1f, tween(850)) }
    val density = LocalDensity.current
    Text(
        f.text,
        Modifier
            .offset { IntOffset(f.x.roundToInt() - with(density) { 16.dp.roundToPx() }, f.y.roundToInt()) }
            .graphicsLayer {
                translationY = if (f.rise) -60f * t.value else 0f
                alpha = if (f.rise) 1f - t.value * 0.8f else 1f - t.value
                val s = if (f.rise) 1f + 0.3f * (1 - t.value) else 0.7f + 0.6f * t.value
                scaleX = s
                scaleY = s
            },
        color = f.color,
        fontSize = f.sizeSp.sp,
        fontWeight = FontWeight.ExtraBold,
    )
}
