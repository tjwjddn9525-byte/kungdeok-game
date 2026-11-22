package com.kungdeok.game.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.data.GameEntity
import com.kungdeok.game.data.GameState
import com.kungdeok.game.model.CatId
import com.kungdeok.game.model.Form
import com.kungdeok.game.overlay.CatMenu
import org.junit.Rule
import org.junit.Test

/** 머리 위 돌봄 메뉴가 고양이를 가리지 않는지 확인 */
class MenuShot {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        environment = detectEnvironment().run { copy(compileSdkVersion = 34) },
    )

    @Test fun menuAboveCat() {
        val now = System.currentTimeMillis()
        val st = GameState(
            CatEntity("kung", stage = 2, level = 7, hunger = 25.0, lastFeedAt = now - 20 * 60_000L),
            CatEntity("nodeok", stage = 1, level = 4),
            GameEntity(),
        )
        val ctx = paparazzi.context
        val wm = ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val menu = CatMenu(ctx, wm, Any(), mutableStateOf(st), { _, _ -> }, {}, {})
        paparazzi.snapshot {
            KungdeokTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF6A8CAF))) {
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 40.dp)) {
                        // 메뉴 창 크기 그대로 (340 x 124dp), 꼬리는 고양이 가운데(왼쪽에서 90dp)
                        Box(Modifier.width(340.dp).height(124.dp)) { menu.MenuContent(CatId.KUNG, 90f * 2.75f, false) }
                        Image(
                            rememberSprite(Form.KUNG_2.smallAsset), null,
                            Modifier.offset(x = 64.dp).size(52.dp),
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
            }
        }
    }
}
