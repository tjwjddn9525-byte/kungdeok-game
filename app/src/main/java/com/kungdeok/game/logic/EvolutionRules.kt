package com.kungdeok.game.logic

import com.kungdeok.game.data.CatEntity

/** 진화 조건 (SPEC 6-2). [fromStage] 에서 다음 단계로 가기 위한 조건. */
data class EvolutionRequirement(
    val fromStage: Int,
    val level: Int,
    val averageDays: Int = 0,
    val minAverage: Double = 0.0,
    val minFriendship: Int = 0,
) {
    fun describe(): String = buildList {
        add("레벨 $level")
        if (averageDays > 0) add("최근 ${averageDays}일 평균 상태 ${minAverage.toInt()} 이상")
        if (minFriendship > 0) add("사이좋음 $minFriendship 이상")
    }.joinToString(", ")
}

object EvolutionRules {
    const val MAX_STAGE = 5

    private val requirements = listOf(
        EvolutionRequirement(1, level = 5),
        EvolutionRequirement(2, level = 15, averageDays = 3, minAverage = 60.0),
        EvolutionRequirement(3, level = 30, averageDays = 7, minAverage = 60.0),
        EvolutionRequirement(4, level = 50, averageDays = 7, minAverage = 70.0, minFriendship = 50),
    ).associateBy { it.fromStage }

    fun requirement(stage: Int): EvolutionRequirement? = requirements[stage]

    /** [average] 는 (일수 → 최근 그 기간 평균 상태) 함수. */
    suspend fun canEvolve(cat: CatEntity, friendship: Int, average: suspend (Int) -> Double): Boolean {
        val req = requirement(cat.stage) ?: return false
        if (cat.level < req.level) return false
        if (friendship < req.minFriendship) return false
        if (req.averageDays > 0 && average(req.averageDays) < req.minAverage) return false
        return true
    }
}
