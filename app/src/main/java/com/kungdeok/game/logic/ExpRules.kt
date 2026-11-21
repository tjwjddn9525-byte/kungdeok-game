package com.kungdeok.game.logic

import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.model.CatId

/** 경험치와 레벨 (SPEC 6-1), 성격 보너스 (SPEC 4-3). */
object ExpRules {
    const val PERSONALITY_BONUS = 1.5

    fun expToNext(level: Int): Int = 20 * level

    /** [action] 으로 얻는 경험치. 성격 보너스와 배고픔 벌칙을 반영한다. */
    fun gain(cat: CatEntity, action: CareAction): Double {
        var exp = action.baseExp.toDouble()
        val id = CatId.of(cat.id)
        if (id == CatId.KUNG && action == CareAction.PLAY) exp *= PERSONALITY_BONUS
        if (id == CatId.NODEOK && action == CareAction.SNACK) exp *= PERSONALITY_BONUS
        if (cat.hunger < StatusRules.LOW) exp *= 0.5
        return exp
    }

    /** 경험치를 더하고 레벨업을 처리한다. 레벨은 고양이별로 따로 오른다. */
    fun addExp(cat: CatEntity, amount: Double): CatEntity {
        var level = cat.level
        var exp = cat.exp + amount
        while (exp >= expToNext(level)) {
            exp -= expToNext(level)
            level++
        }
        return cat.copy(level = level, exp = exp)
    }
}
