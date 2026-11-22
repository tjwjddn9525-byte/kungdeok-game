package com.kungdeok.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.data.ConditionSample
import com.kungdeok.game.data.GameDao
import com.kungdeok.game.data.GameEntity
import com.kungdeok.game.data.GameRepository
import com.kungdeok.game.logic.epochDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/** 메모리 안 가짜 저장소 */
class FakeDao(now: Long) : GameDao {
    private val cats = MutableStateFlow(
        listOf(CatEntity("kung", stage = 2, level = 7, hunger = 25.0), CatEntity("nodeok", stage = 1, level = 4)),
    )
    private val game = MutableStateFlow<GameEntity?>(
        GameEntity(gold = 210, friendship = 66, lastTickAt = now, lastSampleAt = now, lastGiftDay = epochDay(now)),
    )
    override fun observeCats(): Flow<List<CatEntity>> = cats
    override fun observeGame(): Flow<GameEntity?> = game
    override suspend fun cats() = cats.value
    override suspend fun game() = game.value
    override suspend fun upsertCats(cats: List<CatEntity>) {
        val m = this.cats.value.associateBy { it.id }.toMutableMap()
        cats.forEach { m[it.id] = it }
        this.cats.value = m.values.toList()
    }
    override suspend fun upsertGame(game: GameEntity) { this.game.value = game }
    override suspend fun insertSamples(samples: List<ConditionSample>) = Unit
    override suspend fun averageSince(catId: String, since: Long): Double? = null
    override suspend fun pruneSamples(before: Long) = Unit
}

class PanelShot {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        environment = detectEnvironment().run { copy(compileSdkVersion = 34) },
    )

    @Test fun overlayPanel() {
        val now = System.currentTimeMillis()
        val controller = GameController(GameRepository(FakeDao(now)), CoroutineScope(Dispatchers.Unconfined))
        paparazzi.snapshot {
            KungdeokTheme {
                // QuickPanel 과 같은 모양: 어두운 바깥 + 아래에서 올라온 창
                Box(Modifier.fillMaxSize().background(Color(0xFF6A8CAF)).background(Color(0x66000000)), contentAlignment = Alignment.BottomCenter) {
                    Column(
                        Modifier.fillMaxWidth().fillMaxHeight(0.86f)
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Cream),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🐾 쿵덕", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Choco)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {}) { Text("앱에서 열기") }
                            TextButton(onClick = {}) { Text("✕", fontSize = 20.sp) }
                        }
                        GameApp(controller, initialTarget = Target.KUNG, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
