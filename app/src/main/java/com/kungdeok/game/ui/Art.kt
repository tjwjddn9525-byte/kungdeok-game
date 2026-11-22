package com.kungdeok.game.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.kungdeok.game.logic.MobType
import com.kungdeok.game.logic.CareAction

/**
 * 픽셀 아트 에셋 (raw/ui → tools/process_ui.py → sprites/ui).
 * 앱 곳곳에서 쓰던 이모지를 같은 뜻의 그림으로 바꿔 그린다. 그림이 없는 이모지는 그대로 글자로.
 */
object Art {
    private val byEmoji = mapOf(
        // 아이템
        "🍚" to "item_bowl", "🍤" to "item_shrimp", "🧶" to "item_yarn", "🪮" to "item_brush",
        "🎁" to "item_gift", "🪙" to "item_coin",
        // 가구
        "📦" to "furn_box", "🪴" to "furn_plant", "🧺" to "furn_cushion", "🪵" to "furn_scratcher", "🖼️" to "furn_picture",
        // 꾸미기
        "🎀" to "acc_ribbon", "🔔" to "acc_bell", "🌸" to "acc_flower", "🕶️" to "acc_glasses", "👑" to "acc_crown",
        // 효과
        "💥" to "fx_hit", "⭐" to "fx_star", "💫" to "fx_dizzy", "💦" to "fx_sweat", "💨" to "fx_dust",
        "✨" to "fx_sparkle", "❤" to "fx_heart", "❤️" to "fx_heart", "♥" to "fx_heart", "💤" to "fx_sleep",
        // 상태
        "🌙" to "status_sleep", "😊" to "status_mood", "🫧" to "status_clean",
    )

    const val BG_ROOM = "ui/bg_room.png"
    const val BG_TOWER = "ui/bg_tower.png"
    const val STATUS_HUNGER = "ui/status_hunger.png"
    const val STATUS_MOOD = "ui/status_mood.png"
    const val STATUS_CLEAN = "ui/status_clean.png"
    const val STATUS_SLEEP = "ui/status_sleep.png"

    /** 이모지에 해당하는 그림 경로 (없으면 null) */
    fun asset(emoji: String): String? = byEmoji[emoji]?.let { "ui/$it.png" }

    fun mob(type: MobType): String = "ui/mob_${type.name.lowercase()}.png"

    /** 돌봄 버튼 아이콘 */
    fun care(action: CareAction): String = "ui/care_${
        when (action) {
            CareAction.PET -> "pet"
            CareAction.FEED -> "feed"
            CareAction.SNACK -> "snack"
            CareAction.PLAY -> "play"
            CareAction.BRUSH -> "brush"
            CareAction.SLEEP -> "sleep"
        }
    }.png"

    const val TAB_ROOM = "ui/tab_room.png"
}

/** 그림이 있으면 그림, 없으면 이모지 글자 */
@Composable
fun ArtIcon(emoji: String, size: Dp, modifier: Modifier = Modifier, asset: String? = Art.asset(emoji)) {
    if (asset != null) {
        Image(rememberSprite(asset), emoji, modifier.size(size))
    } else {
        Text(emoji, modifier, fontSize = with(LocalDensity.current) { (size * 0.8f).toSp() })
    }
}
