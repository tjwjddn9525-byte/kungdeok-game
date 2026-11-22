package com.kungdeok.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kungdeok.game.logic.Battle
import com.kungdeok.game.logic.BattleEvent
import com.kungdeok.game.model.CatId
import com.kungdeok.game.model.Form
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** 무대 위 배우 한 명(고양이 또는 적)의 움직임 */
private class Actor {
    val x = Animatable(0f)
    val y = Animatable(0f)
    val rot = Animatable(0f)
    val sx = Animatable(1f)
    val sy = Animatable(1f)
    /** 맞았을 때 번쩍 (1 = 하얗게, 0.5 쯤 = 빨갛게) */
    val flash = Animatable(0f)
    val alpha = Animatable(1f)
    /** 잔상 세기 (달릴 때) */
    val ghost = Animatable(0f)
    /** 잔상이 남는 방향 (+1 = 오른쪽으로 달림, 잔상은 왼쪽에) */
    var ghostDir by mutableIntStateOf(1)
    /** 그릴 프레임: 0 기본, 1·2 걷기(달리기), 3 잠자기, 11~ 공격 프레임 (PixelLab) */
    var frame by mutableIntStateOf(0)
}

private enum class FxKind { TEXT, IMAGE, PARTICLE, RING, SLASH }

/**
 * 효과 하나. x, y 는 무대 좌표(px).
 * TEXT/IMAGE: 떠오르거나(rise) 그 자리에서 커지며 사라짐, PARTICLE: (vx, vy) 로 튀며 중력으로 떨어짐,
 * RING: 충격파 원이 퍼짐, SLASH: 할퀸 자국 세 줄이 그어짐
 */
private data class Fx(
    val id: Int,
    val kind: FxKind,
    val x: Float,
    val y: Float,
    val text: String = "",
    val sizeSp: Int = 20,
    val color: Color = Color.White,
    val rise: Boolean = true,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val size: Float = 0f,
    val durationMs: Int = 850,
)

/**
 * 캣타워 전투 무대: 화면 아래쪽 마루에서 고양이 둘과 적이 실제로 부딪친다.
 * 한 턴의 [Battle.events] 를 순서대로 장면으로 튼다. 재생 중에는 [onPlaying] true.
 *
 * 격투 게임식 연출: 웅크렸다(예비 동작) → 달리기 프레임과 잔상으로 돌진 → 할퀴기 자국·히트스톱(찰나 멈춤)
 * → 하얗게 번쩍·넉백·파편 → 데미지 숫자. 기술은 충격파와 먼지구름, 큰 화면 흔들림까지.
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
    val shakeY = remember { Animatable(0f) }
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
    val context = LocalContext.current
    // 형태별 공격 프레임 수 (PixelLab 으로 만든 sprites/large/<형태>_attack<n>.png, 없으면 0)
    val attackFrames = remember(b.kung.form, b.nodeok.form) {
        CatId.entries.associateWith { id ->
            val form = b.fighter(id).form
            (1..16).takeWhile { Sprites.bitmapOrNull(context, form.largeAttackAsset(it)) != null }.size
        }
    }

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

        // ---- 효과 만들기 ----
        fun CoroutineScope.add(f: Fx) {
            fx += f
            launch {
                delay(f.durationMs + 50L)
                fx.remove(f)
            }
        }
        fun CoroutineScope.pop(text: String, x: Float, y: Float, sizeSp: Int, color: Color, rise: Boolean = true) =
            add(Fx(fxId++, if (Art.asset(text) != null) FxKind.IMAGE else FxKind.TEXT, x, y, text, sizeSp, color, rise))

        /** 맞은 곳에서 파편이 사방으로 튄다 */
        fun CoroutineScope.burst(x: Float, y: Float, n: Int, power: Float, colors: List<Color>) {
            repeat(n) { i ->
                val ang = (i.toFloat() / n) * 2 * PI.toFloat() + Random.nextFloat() * 0.6f
                val sp = power * (0.6f + Random.nextFloat() * 0.6f)
                add(
                    Fx(
                        fxId++, FxKind.PARTICLE, x, y,
                        color = colors[i % colors.size],
                        vx = cos(ang) * sp, vy = sin(ang) * sp - power * 0.5f,
                        size = with(density) { (4 + Random.nextInt(4)).dp.toPx() },
                        durationMs = 520,
                    ),
                )
            }
        }
        fun CoroutineScope.ring(x: Float, y: Float, radius: Float, color: Color) =
            add(Fx(fxId++, FxKind.RING, x, y, color = color, size = radius, durationMs = 420))
        fun CoroutineScope.slash(x: Float, y: Float, size: Float) =
            add(Fx(fxId++, FxKind.SLASH, x, y, size = size, durationMs = 300))
        /** 먼지구름이 양옆으로 퍼진다 */
        fun CoroutineScope.dust(x: Float, y: Float, spread: Float) {
            for (dir in listOf(-1f, 1f)) {
                add(Fx(fxId++, FxKind.IMAGE, x, y, "💨", 30, rise = false, vx = dir * spread, durationMs = 600))
            }
        }
        suspend fun shakeScreen(power: Float, vertical: Boolean = false) = coroutineScope {
            launch { shake.snapTo(power); shake.animateTo(0f, tween(320)) }
            if (vertical) launch { shakeY.snapTo(power * 0.8f); shakeY.animateTo(0f, tween(320)) }
        }
        /** 달리는 동안 걷기 프레임을 빠르게 번갈아 */
        fun CoroutineScope.runFrames(a: Actor, periodMs: Long = 70): Job = launch {
            var i = 0
            try {
                while (true) {
                    a.frame = 1 + (i++ % 2)
                    delay(periodMs)
                }
            } finally {
                a.frame = 0
            }
        }
        /** 맞은 쪽 반응: 하얗게 번쩍 → 빨갛게, 뒤로 밀림 */
        suspend fun react(target: Actor, knock: Float, tilt: Float) = coroutineScope {
            launch { target.flash.snapTo(1f); target.flash.animateTo(0f, tween(380)) }
            launch { target.x.animateTo(target.x.value + knock, tween(80)); target.x.animateTo(0f, tween(320, easing = LinearOutSlowInEasing)) }
            launch { target.rot.animateTo(tilt, tween(80)); target.rot.animateTo(0f, tween(320)) }
            launch { target.sx.animateTo(0.88f, tween(60)); target.sx.animateTo(1f, tween(260)) }
        }

        // ---- 장면 재생 ----
        LaunchedEffect(b.turn, b.result) {
            if (alreadyPlayed || b.events.isEmpty()) {
                hpKung = b.kung.hp; hpNodeok = b.nodeok.hp; hpEnemy = b.enemy.hp
                stunnedShown = b.enemyStunned
                if (b.enemy.hp <= 0) enemy.alpha.snapTo(0f)
                return@LaunchedEffect
            }
            onPlaying(true)
            coroutineScope {
                for (e in b.events) {
                    when (e) {
                        is BattleEvent.CatHit -> {
                            val a = actor(e.cat)
                            val reach = enemyX - enemySize * 0.55f - catSize * 0.35f - baseX.getValue(e.cat)
                            val hitX = enemyX - enemySize * 0.15f
                            val hitY = ground - enemySize * 0.55f
                            when {
                                !e.skill -> {
                                    // 1) 예비 동작: 뒤로 살짝 웅크림
                                    launch { a.sx.animateTo(1.12f, tween(130)) }
                                    launch { a.sy.animateTo(0.86f, tween(130)) }
                                    launch { a.rot.animateTo(-6f, tween(130)) }
                                    a.x.animateTo(-catSize * 0.08f, tween(130))
                                    // 2) 돌진: 달리기 프레임 + 잔상
                                    a.ghostDir = 1
                                    launch { a.ghost.animateTo(1f, tween(80)) }
                                    val run = runFrames(a)
                                    launch { a.sx.animateTo(1f, tween(120)); }
                                    launch { a.sy.animateTo(1f, tween(120)) }
                                    launch { a.rot.animateTo(8f, tween(200)) }
                                    launch { a.y.animateTo(-catSize * 0.12f, tween(110)); a.y.animateTo(0f, tween(110)) }
                                    a.x.animateTo(reach, tween(220, easing = FastOutSlowInEasing))
                                    run.cancel()
                                    val nAtk = attackFrames.getValue(e.cat)
                                    if (nAtk > 0) {
                                        // 3) 공격 프레임: 힘 모으기 → 앞발 휘두르기 (그림 자체가 할퀸다)
                                        a.rot.snapTo(0f)
                                        val strikeAt = (nAtk * 3) / 8 + 1 // 앞발이 나가는 프레임
                                        launch {
                                            for (k in 1..nAtk) {
                                                a.frame = 10 + k
                                                delay(if (k < strikeAt) 70 else 60)
                                            }
                                            a.frame = 0
                                        }
                                        delay(70L * (strikeAt - 1))
                                        launch { a.rot.animateTo(6f, tween(60)) }
                                        slash(hitX, hitY, enemySize * 0.7f)
                                        delay(70) // 히트스톱
                                    } else {
                                        // 3) 할퀴기: 앞으로 확 기울며 자국
                                        a.rot.snapTo(20f)
                                        slash(hitX, hitY, enemySize * 0.7f)
                                        delay(70) // 히트스톱
                                    }
                                    // 4) 맞음
                                    launch { react(enemy, enemySize * 0.18f, 10f) }
                                    launch { shakeScreen(7f) }
                                    burst(hitX, hitY, 8, with(density) { 160.dp.toPx() }, listOf(Color(0xFFFFE082), Color.White, Color(0xFFFFB74D)))
                                    pop("💥", hitX, hitY - enemySize * 0.15f, 34, Color.White, rise = false)
                                    pop("-${e.dmg}", enemyX, ground - enemySize * 1.05f, 24, Color(0xFFFFE082))
                                    hpEnemy = e.enemyHp
                                    // 5) 돌아오기 (공격 프레임이 끝날 때까지 잠깐 기다림)
                                    if (nAtk > 0) delay(60L * (nAtk - ((nAtk * 3) / 8 + 1)).coerceAtLeast(0))
                                    launch { a.rot.animateTo(0f, tween(160)) }
                                    launch { a.ghost.animateTo(0f, tween(200)) }
                                    a.ghostDir = -1
                                    val back = runFrames(a, 90)
                                    a.x.animateTo(0f, tween(320, easing = LinearOutSlowInEasing))
                                    back.cancel()
                                }
                                e.cat == CatId.KUNG -> {
                                    // 쿵 기술: 깊게 웅크림 → 솟구쳐 회전 → 공중에서 멈칫 → 내리찍기
                                    launch { showBanner(banner, b.kung.form.skill) { bannerText = it } }
                                    launch { a.sx.animateTo(1.2f, tween(220)) }
                                    a.sy.animateTo(0.75f, tween(220))
                                    burst(baseX.getValue(e.cat), ground - catSize * 0.1f, 6, with(density) { 90.dp.toPx() }, listOf(Color(0xFFFFF59D), Color.White))
                                    a.ghostDir = 1
                                    launch { a.ghost.animateTo(1f, tween(100)) }
                                    launch { a.sx.animateTo(1f, tween(120)) }
                                    launch { a.sy.animateTo(1.12f, tween(160)) }
                                    launch { a.rot.animateTo(360f, tween(480)) }
                                    // 올라가면서 별 꼬리
                                    val trail = launch {
                                        while (true) {
                                            pop("⭐", baseX.getValue(e.cat) + a.x.value, ground + a.y.value - catSize * 0.5f, 16, Color.White, rise = false)
                                            delay(70)
                                        }
                                    }
                                    launch { a.x.animateTo(reach * 0.65f, tween(420, easing = LinearOutSlowInEasing)) }
                                    a.y.animateTo(-h * 0.58f, tween(420, easing = LinearOutSlowInEasing))
                                    a.rot.snapTo(0f)
                                    // 공중에서 멈칫, 몸을 키움
                                    launch { a.sx.animateTo(1.18f, tween(120)) }
                                    a.sy.animateTo(1.18f, tween(120))
                                    delay(80)
                                    // 내리찍기
                                    launch { a.rot.animateTo(25f, tween(170)) }
                                    launch { a.x.animateTo(reach, tween(170, easing = FastOutLinearInEasing)) }
                                    a.y.animateTo(0f, tween(170, easing = FastOutLinearInEasing))
                                    trail.cancel()
                                    launch { a.sx.animateTo(1.3f, tween(50)); a.sx.animateTo(1f, tween(260)) }
                                    launch { a.sy.animateTo(0.7f, tween(50)); a.sy.animateTo(1f, tween(260)) }
                                    delay(100) // 히트스톱
                                    launch { react(enemy, enemySize * 0.32f, 16f) }
                                    launch { shakeScreen(22f, vertical = true) }
                                    ring(enemyX - enemySize * 0.2f, ground - catSize * 0.05f, enemySize * 1.1f, Color(0xFFFFF59D))
                                    dust(enemyX - enemySize * 0.3f, ground - catSize * 0.3f, with(density) { 120.dp.toPx() })
                                    burst(hitX, hitY, 14, with(density) { 240.dp.toPx() }, listOf(Color(0xFFFFF59D), Color(0xFF90CAF9), Color.White))
                                    pop("💥", hitX, hitY - enemySize * 0.2f, 54, Color.White, rise = false)
                                    pop("-${e.dmg}", enemyX, ground - enemySize * 1.2f, 32, Color(0xFFFFD54F))
                                    hpEnemy = e.enemyHp
                                    delay(120)
                                    launch { a.rot.animateTo(0f, tween(200)) }
                                    launch { a.ghost.animateTo(0f, tween(260)) }
                                    a.ghostDir = -1
                                    val back = runFrames(a, 90)
                                    a.x.animateTo(0f, tween(380, easing = LinearOutSlowInEasing))
                                    back.cancel()
                                }
                                else -> {
                                    // 노덕 기술: 묵직하게 웅크림 → 크게 뛰어올라 → 배치기로 깔고 앉기
                                    launch { showBanner(banner, b.nodeok.form.skill) { bannerText = it } }
                                    launch { a.sx.animateTo(1.22f, tween(240)) }
                                    a.sy.animateTo(0.78f, tween(240))
                                    a.ghostDir = 1
                                    launch { a.ghost.animateTo(0.7f, tween(120)) }
                                    launch { a.sx.animateTo(1f, tween(150)) }
                                    launch { a.sy.animateTo(1.08f, tween(150)) }
                                    launch { a.x.animateTo(reach * 0.75f, tween(300, easing = LinearOutSlowInEasing)) }
                                    a.y.animateTo(-catSize * 0.95f, tween(300, easing = LinearOutSlowInEasing))
                                    // 꼭대기에서 몸을 앞으로 기울임
                                    launch { a.rot.animateTo(22f, tween(120)) }
                                    launch { a.sx.animateTo(1.2f, tween(120)) }
                                    delay(90)
                                    launch { a.x.animateTo(reach + enemySize * 0.22f, tween(150, easing = FastOutLinearInEasing)) }
                                    a.y.animateTo(0f, tween(150, easing = FastOutLinearInEasing))
                                    // 깔고 앉음: 적은 납작, 노덕은 찌그러짐
                                    launch { enemy.sy.animateTo(0.45f, tween(70)) }
                                    launch { enemy.sx.animateTo(1.3f, tween(70)) }
                                    launch { a.sy.animateTo(0.72f, tween(70)); a.sy.animateTo(1f, tween(280)) }
                                    delay(110) // 히트스톱
                                    launch { enemy.flash.snapTo(1f); enemy.flash.animateTo(0f, tween(400)) }
                                    launch { shakeScreen(18f, vertical = true) }
                                    ring(enemyX - enemySize * 0.1f, ground - catSize * 0.05f, enemySize * 1.2f, Color(0xFFFFE0B2))
                                    dust(enemyX - enemySize * 0.2f, ground - catSize * 0.25f, with(density) { 140.dp.toPx() })
                                    burst(enemyX, ground - enemySize * 0.3f, 12, with(density) { 200.dp.toPx() }, listOf(Color(0xFFD7CCC8), Color(0xFFFFE0B2), Color.White))
                                    pop("쿵!", enemyX - enemySize * 0.6f, ground - enemySize * 0.5f, 30, Color.White, rise = false)
                                    pop("-${e.dmg}", enemyX, ground - enemySize * 1.2f, 30, Color(0xFFFFD54F))
                                    hpEnemy = e.enemyHp
                                    delay(260)
                                    launch { enemy.sy.animateTo(1f, tween(300)) }
                                    launch { enemy.sx.animateTo(1f, tween(300)) }
                                    launch { a.sx.animateTo(1f, tween(200)) }
                                    launch { a.rot.animateTo(0f, tween(200)) }
                                    // 통통 튀며 돌아옴
                                    launch { a.y.animateTo(-catSize * 0.25f, tween(160)); a.y.animateTo(0f, tween(160)) }
                                    launch { a.ghost.animateTo(0f, tween(260)) }
                                    a.ghostDir = -1
                                    val back = runFrames(a, 100)
                                    a.x.animateTo(0f, tween(420, easing = LinearOutSlowInEasing))
                                    back.cancel()
                                }
                            }
                            delay(110)
                        }
                        is BattleEvent.Heal -> {
                            val a = actor(e.cat)
                            val cx = baseX.getValue(e.cat)
                            // 간식이 위에서 날아와 덥석
                            add(Fx(fxId++, FxKind.IMAGE, cx, ground - catSize * 1.4f, "🍤", 26, rise = false, vy = catSize * 0.5f, durationMs = 260))
                            delay(200)
                            a.y.animateTo(-catSize * 0.3f, tween(160))
                            a.y.animateTo(0f, tween(160))
                            ring(cx, ground - catSize * 0.4f, catSize * 0.8f, Color(0xFFA5D6A7))
                            burst(cx, ground - catSize * 0.5f, 8, with(density) { 90.dp.toPx() }, listOf(Color(0xFFA5D6A7), Color.White))
                            pop("+${e.amount}", cx, ground - catSize * 1.2f, 24, Color(0xFFA5D6A7))
                            pop("✨", cx + catSize * 0.3f, ground - catSize * 0.8f, 18, Color.White)
                            if (e.cat == CatId.KUNG) hpKung = e.hp else hpNodeok = e.hp
                            delay(300)
                        }
                        is BattleEvent.EnemyHit -> {
                            val a = actor(e.target)
                            val targetX = baseX.getValue(e.target)
                            val reach = targetX + catSize * 0.45f + enemySize * 0.35f - enemyX
                            // 1) 예비 동작: 뒤로 젖혔다가
                            launch { enemy.rot.animateTo(12f, tween(170)) }
                            launch { enemy.sx.animateTo(0.9f, tween(170)) }
                            enemy.x.animateTo(enemySize * 0.12f, tween(170))
                            // 2) 잔상을 끌며 달려듦
                            enemy.ghostDir = -1
                            launch { enemy.ghost.animateTo(1f, tween(60)) }
                            launch { enemy.rot.animateTo(-18f, tween(180)) }
                            launch { enemy.sx.animateTo(1.08f, tween(180)) }
                            enemy.x.animateTo(reach, tween(190, easing = FastOutLinearInEasing))
                            delay(60) // 히트스톱
                            // 3) 맞음
                            launch { react(a, -catSize * 0.3f, -16f) }
                            launch { shakeScreen(10f) }
                            burst(targetX + catSize * 0.2f, ground - catSize * 0.5f, 8, with(density) { 150.dp.toPx() }, listOf(Color(0xFFFF8A80), Color.White))
                            pop("💥", targetX + catSize * 0.25f, ground - catSize * 0.75f, 30, Color.White, rise = false)
                            pop("-${e.dmg}", targetX, ground - catSize * 1.15f, 24, Color(0xFFFF8A80))
                            if (e.target == CatId.KUNG) hpKung = e.hp else hpNodeok = e.hp
                            // 4) 적 돌아감
                            launch { enemy.ghost.animateTo(0f, tween(200)) }
                            launch { enemy.rot.animateTo(0f, tween(260)) }
                            launch { enemy.sx.animateTo(1f, tween(260)) }
                            enemy.x.animateTo(0f, tween(330, easing = LinearOutSlowInEasing))
                            if (e.hp <= 0) {
                                // 쓰러짐: 통 튀었다가 눕는다
                                a.y.animateTo(-catSize * 0.15f, tween(120))
                                a.y.animateTo(0f, tween(160))
                                dust(targetX, ground - catSize * 0.2f, with(density) { 50.dp.toPx() })
                                pop("쓰러짐…", targetX, ground - catSize * 1.3f, 16, Color.White)
                            }
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
                            // 하얗게 번쩍 → 빙글빙글 돌며 작아져 펑, 고양이들은 만세 점프
                            enemy.flash.snapTo(1f)
                            delay(120)
                            launch { enemy.flash.animateTo(0f, tween(300)) }
                            launch { enemy.rot.animateTo(720f, tween(650)) }
                            launch { enemy.sx.animateTo(0.2f, tween(650)) }
                            launch { enemy.sy.animateTo(0.2f, tween(650)) }
                            launch { enemy.y.animateTo(-h * 0.3f, tween(650)) }
                            enemy.alpha.animateTo(0f, tween(650))
                            burst(enemyX, ground - h * 0.3f - enemySize * 0.2f, 16, with(density) { 200.dp.toPx() }, listOf(Color(0xFFFFF59D), Color.White, Color(0xFFCE93D8)))
                            pop("✨", enemyX, ground - h * 0.3f - enemySize * 0.4f, 40, Color.White, rise = false)
                            for (c in listOf(kung, nodeok)) launch {
                                val jump = runFrames(c, 120)
                                repeat(2) {
                                    c.y.animateTo(-catSize * 0.3f, tween(160))
                                    c.y.animateTo(0f, tween(160))
                                }
                                jump.cancel()
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
        val shakeYv = shakeY.value
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                // 흔들림은 좌우로 떨리게
                translationX = shakeX * sin(shakeX * 3f)
                translationY = shakeYv * cos(shakeYv * 3f)
            },
        ) {
            // 캣타워 안 배경 그림 (달 뜬 창문, 캣타워, 마루). 바닥이 아래쪽에 오게 아래 기준으로 자른다.
            Image(
                rememberSprite(Art.BG_TOWER),
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter,
            )

            for (id in CatId.entries) {
                val f = b.fighter(id)
                val hp = if (id == CatId.KUNG) hpKung else hpNodeok
                CatActor(actor(id), f.form, hp, f.maxHp, centerX = baseX.getValue(id), ground = ground, size = catSize)
            }
            EnemyActor(enemy, b.enemy.icon, b.enemy.asset, hpEnemy, b.enemy.maxHp, enemyX, ground, enemySize, stunnedShown)

            for (f in fx) FxView(f)

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

private suspend fun showBanner(banner: Animatable<Float, AnimationVector1D>, text: String, set: (String) -> Unit) {
    set("$text!")
    banner.snapTo(0f)
    banner.animateTo(1f, tween(180))
    delay(900)
    banner.animateTo(0f, tween(300))
}

private val whiteFlash = ColorFilter.tint(Color.White)
private val redFlash = ColorFilter.tint(Color(0xFFFF5252))

@Composable
private fun CatActor(a: Actor, form: Form, hp: Int, maxHp: Int, centerX: Float, ground: Float, size: Float) {
    val density = LocalDensity.current
    val sizeDp = with(density) { size.toDp() }
    val fainted = hp <= 0
    val gray = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    val base = rememberSprite(form.largeAsset)
    val walk1 = rememberSpriteOrNull(form.largeFrameAsset("walk1"))
    val walk2 = rememberSpriteOrNull(form.largeFrameAsset("walk2"))
    val attack = (1..16).map { rememberSpriteOrNull(form.largeAttackAsset(it)) }
    val sprite = when {
        a.frame == 1 -> walk1 ?: base
        a.frame == 2 -> walk2 ?: base
        a.frame > 10 -> attack.getOrNull(a.frame - 11) ?: base
        else -> base
    }
    // 왼쪽을 보는 그림은 적(오른쪽) 쪽을 보게 뒤집는다
    val face = if (form.facesLeft) -1f else 1f
    Box(
        Modifier
            .offset { IntOffset((centerX - size / 2).roundToInt(), (ground - size).roundToInt()) }
            .size(sizeDp),
    ) {
        // 잔상
        if (a.ghost.value > 0.01f) {
            for (k in 1..3) {
                Image(
                    sprite, null,
                    Modifier.fillMaxSize().graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationX = a.x.value - a.ghostDir * size * 0.16f * k
                        translationY = a.y.value + (if (a.y.value < -1f) size * 0.08f * k else 0f)
                        rotationZ = a.rot.value
                        scaleX = a.sx.value * face
                        scaleY = a.sy.value
                        alpha = a.ghost.value * (0.32f / k)
                    },
                    colorFilter = ColorFilter.tint(Color(0xFFB3E5FC)),
                )
            }
        }
        Image(
            sprite,
            null,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    translationX = a.x.value
                    translationY = a.y.value
                    // 쓰러지면 옆으로 눕는다
                    rotationZ = a.rot.value + if (fainted) -80f else 0f
                    scaleX = a.sx.value * face
                    scaleY = a.sy.value
                    alpha = if (fainted) 0.55f else 1f
                },
            colorFilter = if (fainted) gray else null,
        )
        // 맞으면 하얗게 번쩍했다가 빨갛게
        if (a.flash.value > 0f) {
            Image(
                sprite, null,
                Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    translationX = a.x.value; translationY = a.y.value; rotationZ = a.rot.value
                    scaleX = a.sx.value * face; scaleY = a.sy.value
                    alpha = if (a.flash.value > 0.7f) 0.9f else a.flash.value * 0.9f
                },
                colorFilter = if (a.flash.value > 0.7f) whiteFlash else redFlash,
            )
        }
        StageHpBar(hp, maxHp, Color(0xFF81C784), Modifier.align(Alignment.TopCenter).offset(y = (-14).dp).width(sizeDp * 0.9f))
    }
}

@Composable
private fun EnemyActor(a: Actor, icon: String, asset: String?, hp: Int, maxHp: Int, centerX: Float, ground: Float, size: Float, stunned: Boolean) {
    val density = LocalDensity.current
    val sizeDp = with(density) { size.toDp() }
    val fontSp = with(density) { (size * 0.8f).toSp() }
    Box(
        Modifier
            .offset { IntOffset((centerX - size / 2).roundToInt(), (ground - size).roundToInt()) }
            .size(sizeDp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        fun layer(dx: Float = 0f, alphaMul: Float = 1f) = Modifier.graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 1f)
            translationX = a.x.value + dx
            translationY = a.y.value
            rotationZ = a.rot.value
            scaleX = a.sx.value
            scaleY = a.sy.value
            alpha = a.alpha.value * alphaMul
        }
        if (asset != null) {
            val img = rememberSprite(asset)
            // 달려들 때 잔상
            if (a.ghost.value > 0.01f) {
                for (k in 1..3) {
                    Image(
                        img, null,
                        layer(-a.ghostDir * size * 0.16f * k, a.ghost.value * 0.3f / k).fillMaxSize(),
                        colorFilter = ColorFilter.tint(Color(0xFFFFCDD2)),
                    )
                }
            }
            // 적 그림은 왼쪽(고양이 쪽)을 보고 있다
            Image(img, icon, layer().fillMaxSize())
            if (a.flash.value > 0f) {
                Image(
                    img, null,
                    layer(alphaMul = if (a.flash.value > 0.7f) 0.9f else a.flash.value).fillMaxSize(),
                    colorFilter = if (a.flash.value > 0.7f) whiteFlash else redFlash,
                )
            }
        } else {
            Text(icon, layer(), fontSize = fontSp)
        }
        if (stunned && a.alpha.value > 0f) {
            ArtIcon("💫", 40.dp, Modifier.align(Alignment.TopCenter).offset(y = (-14).dp))
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
private fun FxView(f: Fx) {
    val t = remember(f.id) { Animatable(0f) }
    LaunchedEffect(f.id) { t.animateTo(1f, tween(f.durationMs, easing = LinearEasing)) }
    val density = LocalDensity.current
    when (f.kind) {
        FxKind.PARTICLE -> {
            // 픽셀 느낌의 네모 파편: 튀어 나가며 중력으로 떨어짐
            val p = t.value
            val gravity = with(density) { 500.dp.toPx() }
            val px = f.x + f.vx * p * (f.durationMs / 1000f)
            val py = f.y + f.vy * p * (f.durationMs / 1000f) + gravity * (p * f.durationMs / 1000f).let { it * it }
            val sizeDp = with(density) { f.size.toDp() }
            Box(
                Modifier
                    .offset { IntOffset((px - f.size / 2).roundToInt(), (py - f.size / 2).roundToInt()) }
                    .size(sizeDp)
                    .graphicsLayer { alpha = 1f - p; rotationZ = p * 180f }
                    .background(f.color),
            )
        }
        FxKind.RING -> {
            Canvas(Modifier.fillMaxSize()) {
                val p = t.value
                drawCircle(
                    f.color.copy(alpha = (1f - p) * 0.9f),
                    radius = f.size * (0.2f + 0.8f * p),
                    center = Offset(f.x, f.y),
                    style = Stroke(width = (1f - p) * 14f + 2f),
                )
            }
        }
        FxKind.SLASH -> {
            // 할퀸 자국 세 줄: 위에서 아래로 빠르게 그어지고 사라진다
            Canvas(Modifier.fillMaxSize()) {
                val p = t.value
                val draw = (p / 0.4f).coerceAtMost(1f)
                val alpha = if (p < 0.4f) 1f else 1f - (p - 0.4f) / 0.6f
                for (k in -1..1) {
                    val ox = f.x + k * f.size * 0.18f
                    val start = Offset(ox + f.size * 0.25f, f.y - f.size * 0.4f)
                    val end = Offset(ox - f.size * 0.25f, f.y + f.size * 0.4f)
                    val cur = Offset(start.x + (end.x - start.x) * draw, start.y + (end.y - start.y) * draw)
                    drawLine(Color(0xFFFFF8E1).copy(alpha = alpha), start, cur, strokeWidth = 10f, cap = StrokeCap.Round)
                    drawLine(Color(0xFFFF7043).copy(alpha = alpha * 0.8f), start, cur, strokeWidth = 4f, cap = StrokeCap.Round)
                }
            }
        }
        FxKind.IMAGE, FxKind.TEXT -> {
            val asset = if (f.kind == FxKind.IMAGE) Art.asset(f.text) else null
            val sec = f.durationMs / 1000f
            val move = Modifier.graphicsLayer {
                translationX = f.vx * t.value * sec
                translationY = (if (f.rise) -60f * t.value else 0f) + f.vy * t.value * sec
                alpha = if (f.rise) 1f - t.value * 0.8f else 1f - t.value
                val s = if (f.rise) 1f + 0.4f * (1 - t.value) * (1 - t.value) else 0.7f + 0.6f * t.value
                scaleX = s
                scaleY = s
            }
            if (asset != null) {
                val sizeDp = (f.sizeSp * 1.5f).dp
                Image(
                    rememberSprite(asset),
                    null,
                    Modifier
                        .offset { IntOffset(f.x.roundToInt() - sizeDp.roundToPx() / 2, f.y.roundToInt() - sizeDp.roundToPx() / 2) }
                        .size(sizeDp)
                        .then(move),
                )
            } else {
                Text(
                    f.text,
                    Modifier
                        .offset { IntOffset(f.x.roundToInt() - with(density) { 16.dp.roundToPx() }, f.y.roundToInt()) }
                        .then(move),
                    color = f.color,
                    fontSize = f.sizeSp.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}
