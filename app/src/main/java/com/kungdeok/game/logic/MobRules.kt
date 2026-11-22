package com.kungdeok.game.logic

import kotlin.random.Random

/** 잡몹이 움직이는 방식 */
enum class MobMove {
    /** 바닥을 걸어 다님 */
    WALK,
    /** 공중을 이리저리 날아다님 */
    FLY,
    /** 바닥에서 폴짝폴짝 */
    HOP,
    /** 위에서 실을 타고 내려옴 */
    DROP,
}

/** 바탕화면에 가끔 나오는 잡몹 */
enum class MobType(
    val label: String,
    val icon: String,
    val move: MobMove,
    val hp: Int,
    val exp: Int,
    val goldMin: Int,
    val goldMax: Int,
    /** 나올 확률 가중치 */
    val weight: Int,
    /** 초당 이동 속도 (dp) */
    val speedDp: Float,
) {
    MOUSE("생쥐", "🐭", MobMove.WALK, 1, 6, 3, 6, 30, 55f),
    FLY("파리", "🪰", MobMove.FLY, 1, 4, 2, 4, 25, 70f),
    CRICKET("귀뚜라미", "🦗", MobMove.HOP, 2, 7, 3, 7, 15, 45f),
    LADYBUG("무당벌레", "🐞", MobMove.WALK, 1, 5, 2, 5, 15, 20f),
    SPIDER("거미", "🕷️", MobMove.DROP, 2, 8, 4, 8, 10, 30f),
    BUTTERFLY("나비", "🦋", MobMove.FLY, 1, 14, 8, 14, 5, 40f),
}

/** 잡몹 시스템 (바탕화면 오버레이) */
object MobRules {
    /** 다음 잡몹이 나올 때까지 (초): 2~5분 */
    fun nextSpawnDelaySec(random: Random = Random.Default): Float = 120f + random.nextFloat() * 180f

    /** 오버레이를 켠 직후 첫 잡몹은 조금 빨리: 30초~1분 30초 */
    fun firstSpawnDelaySec(random: Random = Random.Default): Float = 30f + random.nextFloat() * 60f

    /** 아무도 안 잡으면 이만큼 뒤에 도망간다 (초) */
    const val LIFETIME_SEC = 40f

    /** 고양이 가까이 있으면 고양이가 알아서 사냥에 나설 확률 (5초마다) */
    const val AUTO_HUNT_CHANCE = 0.3f

    fun pick(random: Random = Random.Default): MobType {
        val total = MobType.entries.sumOf { it.weight }
        var r = random.nextInt(total)
        for (m in MobType.entries) {
            r -= m.weight
            if (r < 0) return m
        }
        return MobType.MOUSE
    }

    fun gold(type: MobType, random: Random = Random.Default): Int =
        type.goldMin + random.nextInt(type.goldMax - type.goldMin + 1)
}
