package com.kungdeok.game.logic

import com.kungdeok.game.data.GameEntity
import com.kungdeok.game.model.CatId

enum class Closeness(val label: String) {
    /** 80 이상: 서로 그루밍, 붙어서 같이 잠 */
    CLOSE("단짝"),
    /** 40–79: 각자 돌아다니다 가끔 나란히 앉음 */
    NORMAL("친구"),
    /** 40 미만: 가끔 투닥거림, 떨어져서 잠 */
    DISTANT("서먹"),
}

/** 관계 시스템 (SPEC 7)과 질투 (SPEC 5). */
object RelationshipRules {
    const val DAILY_DECAY = 2
    const val JEALOUS_STREAK = 10
    const val JEALOUS_MOOD = 5.0

    fun closeness(friendship: Int): Closeness = when {
        friendship >= 80 -> Closeness.CLOSE
        friendship >= 40 -> Closeness.NORMAL
        else -> Closeness.DISTANT
    }

    /** 날이 바뀐 만큼 사이좋음 -2 씩. */
    fun decay(game: GameEntity, today: Long): GameEntity {
        if (game.friendshipDay == 0L) return game.copy(friendshipDay = today)
        val days = today - game.friendshipDay
        if (days <= 0) return game
        val f = (game.friendship - DAILY_DECAY * days).coerceAtLeast(0).toInt()
        return game.copy(friendship = f, friendshipDay = today)
    }

    /**
     * [cat] 을 쓰다듬었을 때 연속 횟수를 갱신한다.
     * 한 마리만 연속 10번 쓰다듬으면 (새 상태, 질투한 고양이) 를 돌려준다.
     */
    fun onPet(game: GameEntity, cat: CatId): Pair<GameEntity, CatId?> {
        val streak = if (game.lastPetCat == cat.id) game.petStreak + 1 else 1
        return if (streak >= JEALOUS_STREAK) {
            game.copy(lastPetCat = cat.id, petStreak = 0) to cat.other
        } else {
            game.copy(lastPetCat = cat.id, petStreak = streak) to null
        }
    }
}
