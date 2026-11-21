package com.kungdeok.game.logic

import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.data.GameEntity
import com.kungdeok.game.data.joinIds
import com.kungdeok.game.logic.StatusRules.clamp

enum class ShopKind(val label: String) {
    CONSUMABLE("간식과 장난감"),
    FURNITURE("방 꾸미기"),
    ACCESSORY("고양이 꾸미기"),
}

/** 상점 물건 (SPEC 9, 3단계). 소모품은 가방에 쌓이고, 나머지는 한 번 사면 계속 쓴다. */
enum class ShopItem(
    val id: String,
    val label: String,
    val icon: String,
    val price: Int,
    val kind: ShopKind,
    val desc: String,
) {
    FOOD("food", "밥", "🍚", 20, ShopKind.CONSUMABLE, "가방에서 주면 쿨다운 없이 배고픔 +40"),
    SNACK("snack", "간식", "🍤", 15, ShopKind.CONSUMABLE, "배고픔 +10, 기분 +10. 캣타워에서 체력 회복"),
    TOY("toy", "장난감", "🧶", 30, ShopKind.CONSUMABLE, "쿨다운 없이 기분 +25"),
    BRUSH("brush", "빗", "🪮", 25, ShopKind.CONSUMABLE, "쿨다운 없이 청결 +40"),

    BOX("box", "종이 박스", "📦", 80, ShopKind.FURNITURE, "고양이들이 가끔 들어가 있어요"),
    PLANT("plant", "캣그라스 화분", "🪴", 60, ShopKind.FURNITURE, "방 한쪽에 초록 화분"),
    CUSHION("cushion", "푹신한 방석", "🧺", 100, ShopKind.FURNITURE, "러그 위에 방석"),
    SCRATCHER("scratcher", "스크래처", "🪵", 90, ShopKind.FURNITURE, "벅벅 긁는 판"),
    PICTURE("picture", "가족 사진 액자", "🖼️", 120, ShopKind.FURNITURE, "벽에 거는 사진"),

    RIBBON("ribbon", "빨간 리본", "🎀", 150, ShopKind.ACCESSORY, "머리에 다는 리본"),
    BELL("bell", "방울 목걸이", "🔔", 150, ShopKind.ACCESSORY, "딸랑딸랑"),
    FLOWER("flower", "꽃 머리핀", "🌸", 120, ShopKind.ACCESSORY, "귀 옆에 꽃"),
    GLASSES("glasses", "선글라스", "🕶️", 200, ShopKind.ACCESSORY, "시크함 두 배"),
    CROWN("crown", "작은 왕관", "👑", 500, ShopKind.ACCESSORY, "왕의 품격");

    companion object {
        fun byId(id: String): ShopItem? = entries.firstOrNull { it.id == id }
    }
}

sealed interface BuyResult {
    data object Ok : BuyResult
    data object NotEnoughGold : BuyResult
    data object AlreadyOwned : BuyResult
}

object ShopRules {
    fun bagCount(game: GameEntity, item: ShopItem): Int = when (item) {
        ShopItem.FOOD -> game.food
        ShopItem.SNACK -> game.snack
        ShopItem.TOY -> game.toy
        ShopItem.BRUSH -> game.brush
        else -> 0
    }

    fun addToBag(game: GameEntity, item: ShopItem, n: Int): GameEntity = when (item) {
        ShopItem.FOOD -> game.copy(food = game.food + n)
        ShopItem.SNACK -> game.copy(snack = game.snack + n)
        ShopItem.TOY -> game.copy(toy = game.toy + n)
        ShopItem.BRUSH -> game.copy(brush = game.brush + n)
        else -> game
    }

    fun buy(game: GameEntity, item: ShopItem): Pair<GameEntity, BuyResult> {
        if (item.kind != ShopKind.CONSUMABLE && item.id in game.owned) return game to BuyResult.AlreadyOwned
        if (game.gold < item.price) return game to BuyResult.NotEnoughGold
        var g = game.copy(gold = game.gold - item.price)
        g = when (item.kind) {
            ShopKind.CONSUMABLE -> addToBag(g, item, 1)
            // 가구는 사자마자 방에 놓는다
            ShopKind.FURNITURE -> g.copy(ownedDecor = (g.owned + item.id).joinIds(), placedDecor = (g.placed + item.id).joinIds())
            ShopKind.ACCESSORY -> g.copy(ownedDecor = (g.owned + item.id).joinIds())
        }
        return g to BuyResult.Ok
    }

    fun togglePlaced(game: GameEntity, item: ShopItem): GameEntity {
        if (item.kind != ShopKind.FURNITURE || item.id !in game.owned) return game
        val placed = if (item.id in game.placed) game.placed - item.id else game.placed + item.id
        return game.copy(placedDecor = placed.joinIds())
    }

    /** 가방의 소모품을 쓴다: 쿨다운 없이 효과. 대응하는 돌봄 행동 (경험치 계산용). */
    fun careActionOf(item: ShopItem): CareAction? = when (item) {
        ShopItem.FOOD -> CareAction.FEED
        ShopItem.SNACK -> CareAction.SNACK
        ShopItem.TOY -> CareAction.PLAY
        ShopItem.BRUSH -> CareAction.BRUSH
        else -> null
    }

    fun useEffect(cat: CatEntity, item: ShopItem): CatEntity = when (item) {
        ShopItem.FOOD -> cat.copy(hunger = clamp(cat.hunger + 40))
        ShopItem.SNACK -> cat.copy(hunger = clamp(cat.hunger + 10), mood = clamp(cat.mood + 10))
        ShopItem.TOY -> cat.copy(mood = clamp(cat.mood + 25), sleepiness = clamp(cat.sleepiness - 10))
        ShopItem.BRUSH -> cat.copy(cleanliness = clamp(cat.cleanliness + 40), mood = clamp(cat.mood + 5))
        else -> cat
    }
}
