package com.kungdeok.game.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.kungdeok.game.logic.CareAction
import com.kungdeok.game.model.Form
import org.junit.Rule
import org.junit.Test

/** 돌봄 장면을 진행도별로 늘어놓은 필름 (방 화면 RoomCat 과 같은 방식으로 그림) */
class CareFilmstrip {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3200),
        environment = detectEnvironment().run { copy(compileSdkVersion = 34) },
    )

    @Test fun filmstrip() = paparazzi.snapshot {
        val steps = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
        Column(Modifier.background(Floor).padding(8.dp)) {
            for (action in CareAction.entries) {
                Text(action.label, fontSize = 12.sp)
                Row {
                    for (p in steps) Frame(action, p)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Frame(action: CareAction, p: Float) {
        val sprite = rememberSprite(Form.KUNG_2.largeAsset)
        val pose = CareAnimation.pose(action, p)
        val props = CareAnimation.props(action, p)
        val px = with(LocalDensity.current) { 50.dp.toPx() }
        Box(Modifier.size(76.dp, 110.dp), contentAlignment = Alignment.BottomCenter) {
            Image(
                sprite, null,
                Modifier.size(50.dp).graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    translationX = pose.dx * size.width
                    translationY = pose.dy * size.height
                    scaleX = pose.sx * (if (pose.flip) -1f else 1f)
                    scaleY = pose.sy
                    rotationZ = pose.rot
                },
            )
            for (prop in props) {
                Text(
                    prop.text,
                    Modifier.graphicsLayer {
                        translationX = prop.x * px
                        translationY = prop.y * px
                        rotationZ = prop.rot
                        alpha = prop.alpha.coerceIn(0f, 1f)
                    },
                    fontSize = with(LocalDensity.current) { (prop.size * px).toSp() },
                    color = Choco,
                )
            }
            Text("${(p * 100).toInt()}%", Modifier.align(Alignment.TopStart), fontSize = 8.sp, color = Color.Gray)
        }
    }
}
