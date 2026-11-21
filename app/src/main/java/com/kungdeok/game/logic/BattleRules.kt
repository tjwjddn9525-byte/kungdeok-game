package com.kungdeok.game.logic

import com.kungdeok.game.data.CatEntity
import com.kungdeok.game.model.CatId
import com.kungdeok.game.model.Form
import kotlin.math.roundToInt
import kotlin.random.Random

data class Fighter(
    val cat: CatId,
    val form: Form,
    val maxHp: Int,
    val hp: Int,
    val atk: Int,
    val skillCooldown: Int = 0,
) {
    val alive: Boolean get() = hp > 0
}

data class Enemy(val name: String, val icon: String, val maxHp: Int, val hp: Int, val atk: Int, val boss: Boolean) {
    val alive: Boolean get() = hp > 0
}

enum class BattleAction { ATTACK, KUNG_SKILL, NODEOK_SKILL, SNACK }

enum class BattleResult { WIN, LOSE }

data class Battle(
    val floor: Int,
    val kung: Fighter,
    val nodeok: Fighter,
    val enemy: Enemy,
    val turn: Int = 1,
    val enemyStunned: Boolean = false,
    val log: List<String> = emptyList(),
    val result: BattleResult? = null,
) {
    fun fighter(id: CatId) = if (id == CatId.KUNG) kung else nodeok
    fun withFighter(f: Fighter) = if (f.cat == CatId.KUNG) copy(kung = f) else copy(nodeok = f)
}

data class Reward(val gold: Int, val exp: Int, val firstClear: Boolean)

/** 캣타워 층별 전투 (SPEC 9, 3단계). 턴제: 내가 행동 하나를 고르면 적이 한 번 반격한다. */
object BattleRules {
    const val SKILL_COOLDOWN = 3
    const val BOSS_EVERY = 5
    /** 도전할 때 드는 체력: 두 마리 배고픔·졸림 -8 */
    const val ENTRY_COST = 8.0

    private val normals = listOf(
        "생쥐" to "🐭", "왕파리" to "🪰", "참새" to "🐦", "도마뱀" to "🦎",
        "거미" to "🕷️", "까마귀" to "🐦‍⬛", "뒤집힌 양말" to "🧦", "비닐봉지 유령" to "👻",
    )
    private val bosses = listOf(
        "빗자루 대장" to "🧹", "로봇청소기" to "🤖", "드라이기" to "💨",
        "옆집 강아지" to "🐕", "목욕통" to "🛁", "동물병원 가방" to "🧳",
    )

    fun isBoss(floor: Int) = floor % BOSS_EVERY == 0

    fun enemy(floor: Int): Enemy {
        val boss = isBoss(floor)
        val (name, icon) = if (boss) {
            val i = floor / BOSS_EVERY - 1
            val round = i / bosses.size
            bosses[i % bosses.size].let { (n, ic) -> (if (round > 0) "강화 $n" else n) to ic }
        } else {
            normals[(floor - 1) % normals.size]
        }
        var hp = 30 + floor * 14
        var atk = 4 + floor * 2.2
        if (boss) {
            hp *= 2
            atk *= 1.3
        }
        return Enemy(name, icon, hp, hp, atk.roundToInt(), boss)
    }

    /** 컨디션(평균 상태)이 좋을수록 세다: 공격력 x0.7 ~ x1.0 */
    fun fighter(id: CatId, cat: CatEntity): Fighter {
        val stage = cat.stage
        var hp = 40 + cat.level * 5 + (stage - 1) * 25
        var atk = (6 + cat.level + (stage - 1) * 5) * (0.7 + 0.3 * cat.average / 100.0)
        if (id == CatId.KUNG) atk *= 1.15 // 쿵: 공격형
        if (id == CatId.NODEOK) hp = (hp * 1.25).roundToInt() // 노덕: 탱커
        return Fighter(id, Form.of(id, stage), hp, hp, atk.roundToInt().coerceAtLeast(1))
    }

    fun start(floor: Int, kung: CatEntity, nodeok: CatEntity): Battle {
        val e = enemy(floor)
        return Battle(
            floor, fighter(CatId.KUNG, kung), fighter(CatId.NODEOK, nodeok), e,
            log = listOf("${floor}층: ${e.icon} ${e.name}${if (e.boss) " (보스)" else ""}이(가) 나타났다!"),
        )
    }

    private fun roll(base: Int, mult: Double, random: Random): Int =
        (base * mult * (0.85 + random.nextDouble() * 0.3)).roundToInt().coerceAtLeast(1)

    fun canUse(b: Battle, action: BattleAction, snacks: Int): Boolean = when (action) {
        BattleAction.ATTACK -> true
        BattleAction.KUNG_SKILL -> b.kung.alive && b.kung.skillCooldown == 0
        BattleAction.NODEOK_SKILL -> b.nodeok.alive && b.nodeok.skillCooldown == 0
        BattleAction.SNACK -> snacks > 0 && (b.kung.alive && b.kung.hp < b.kung.maxHp || b.nodeok.alive && b.nodeok.hp < b.nodeok.maxHp)
    }

    /** 한 턴 진행. 간식을 썼으면 호출한 쪽이 가방에서 하나 뺀다. */
    fun act(battle: Battle, action: BattleAction, random: Random = Random.Default): Battle {
        if (battle.result != null) return battle
        var b = battle
        val log = mutableListOf<String>()
        var enemy = b.enemy
        var stun = false

        fun hit(f: Fighter, mult: Double, verb: String) {
            if (!f.alive || !enemy.alive) return
            val d = roll(f.atk, mult, random)
            enemy = enemy.copy(hp = (enemy.hp - d).coerceAtLeast(0))
            log += "${f.form.monName}의 $verb! $d 데미지"
        }

        when (action) {
            BattleAction.ATTACK -> {
                hit(b.kung, 1.0, "냥냥펀치")
                hit(b.nodeok, 1.0, "냥냥펀치")
            }
            BattleAction.KUNG_SKILL -> {
                hit(b.kung, 2.5, b.kung.form.skill)
                hit(b.nodeok, 1.0, "냥냥펀치")
                b = b.withFighter(b.kung.copy(skillCooldown = SKILL_COOLDOWN + 1))
            }
            BattleAction.NODEOK_SKILL -> {
                hit(b.nodeok, 1.5, b.nodeok.form.skill)
                hit(b.kung, 1.0, "냥냥펀치")
                stun = enemy.alive
                if (stun) log += "${enemy.name}이(가) 꼼짝 못 한다!"
                b = b.withFighter(b.nodeok.copy(skillCooldown = SKILL_COOLDOWN + 1))
            }
            BattleAction.SNACK -> {
                val target = listOf(b.kung, b.nodeok).filter { it.alive }.minByOrNull { it.hp.toDouble() / it.maxHp }
                if (target != null) {
                    val heal = (target.maxHp * 0.4).roundToInt()
                    b = b.withFighter(target.copy(hp = (target.hp + heal).coerceAtMost(target.maxHp)))
                    log += "🍤 간식을 먹은 ${target.form.monName}, 체력 +$heal"
                }
            }
        }
        b = b.copy(enemy = enemy)

        if (!enemy.alive) {
            log += "${enemy.icon} ${enemy.name}을(를) 물리쳤다!"
            return b.copy(log = (b.log + log).takeLast(12), result = BattleResult.WIN)
        }

        // 적의 반격
        if (b.enemyStunned) {
            log += "${enemy.name}은(는) 아직 정신을 못 차렸다."
        } else {
            val targets = listOf(b.kung, b.nodeok).filter { it.alive }
            val t = targets[random.nextInt(targets.size)]
            val d = roll(enemy.atk, if (t.cat == CatId.NODEOK) 0.7 else 1.0, random)
            val nt = t.copy(hp = (t.hp - d).coerceAtLeast(0))
            b = b.withFighter(nt)
            log += "${enemy.icon} ${enemy.name}의 공격! ${t.form.monName} -$d" + if (!nt.alive) " (쓰러졌다…)" else ""
        }

        // 쿨다운 감소
        b = b.copy(
            kung = b.kung.copy(skillCooldown = (b.kung.skillCooldown - 1).coerceAtLeast(0)),
            nodeok = b.nodeok.copy(skillCooldown = (b.nodeok.skillCooldown - 1).coerceAtLeast(0)),
            enemyStunned = stun,
            turn = b.turn + 1,
        )
        val result = if (!b.kung.alive && !b.nodeok.alive) BattleResult.LOSE else null
        if (result == BattleResult.LOSE) log += "둘 다 지쳐 버렸다… 다음에 다시 도전하자."
        return b.copy(log = (b.log + log).takeLast(12), result = result)
    }

    /** 이긴 층의 보상. 처음 깬 층이면 전부, 다시 깬 층이면 절반. */
    fun reward(floor: Int, bestFloor: Int): Reward {
        val first = floor > bestFloor
        var gold = 10 + floor * 5
        var exp = 5 + floor * 2
        if (isBoss(floor)) {
            gold *= 2
            exp *= 2
        }
        if (!first) {
            gold /= 2
            exp /= 2
        }
        return Reward(gold, exp, first)
    }

    /** 도전할 수 없는 이유 (없으면 null) */
    fun entryBlock(kung: CatEntity, nodeok: CatEntity, now: Long, fused: Boolean): String? = when {
        fused -> "합체 중에는 캣타워에 갈 수 없어요"
        kung.isAsleep(now) || nodeok.isAsleep(now) -> "자는 고양이가 있어요"
        kung.hunger < StatusRules.LOW || nodeok.hunger < StatusRules.LOW -> "배가 너무 고파요. 밥부터!"
        kung.sleepiness < StatusRules.LOW || nodeok.sleepiness < StatusRules.LOW -> "너무 졸려요. 재우고 와요"
        else -> null
    }
}
