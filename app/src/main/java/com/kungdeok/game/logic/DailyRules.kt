package com.kungdeok.game.logic

import com.kungdeok.game.data.GameEntity
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** 날짜 계산 (폰의 현지 시간 기준 하루). */
fun epochDay(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

enum class Item(val label: String, val icon: String) {
    FOOD("밥", "🍚"), SNACK("간식", "🍤"), TOY("장난감", "🧶"), BRUSH("빗", "🪮");
}

sealed interface Gift {
    data class Gold(val amount: Int) : Gift
    data class Items(val item: Item, val count: Int) : Gift

    fun describe(): String = when (this) {
        is Gold -> "🪙 골드 $amount"
        is Items -> "${item.icon} ${item.label} ${count}개"
    }
}

/** 매일 선물 (SPEC 5-2): 하루 한 번, 골드 또는 아이템 랜덤 */
object DailyRules {
    fun giftAvailable(game: GameEntity, today: Long): Boolean = game.lastGiftDay != today

    fun rollGift(random: Random = Random.Default): Gift =
        if (random.nextFloat() < 0.6f) {
            Gift.Gold(30 + random.nextInt(71))
        } else {
            Gift.Items(Item.entries.random(random), 1 + random.nextInt(2))
        }

    fun apply(game: GameEntity, gift: Gift, today: Long): GameEntity {
        val g = game.copy(lastGiftDay = today)
        return when (gift) {
            is Gift.Gold -> g.copy(gold = g.gold + gift.amount)
            is Gift.Items -> when (gift.item) {
                Item.FOOD -> g.copy(food = g.food + gift.count)
                Item.SNACK -> g.copy(snack = g.snack + gift.count)
                Item.TOY -> g.copy(toy = g.toy + gift.count)
                Item.BRUSH -> g.copy(brush = g.brush + gift.count)
            }
        }
    }
}
