package com.kungdeok.game.logic

/** 경험치와 레벨 (SPEC 6-1). */
object ExpRules {
    fun expToNext(level: Int): Int = 20 * level
}
