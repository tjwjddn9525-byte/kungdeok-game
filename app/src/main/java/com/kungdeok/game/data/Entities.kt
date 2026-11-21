package com.kungdeok.game.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 고양이별 상태 (SPEC 4-1). 수치는 0–100, 높을수록 좋은 상태. */
@Entity(tableName = "cats")
data class CatEntity(
    @PrimaryKey val id: String,
    val stage: Int = 1,
    val level: Int = 1,
    val exp: Double = 0.0,
    val hunger: Double = 80.0,
    val mood: Double = 80.0,
    val cleanliness: Double = 80.0,
    /** 졸림 수치. 0에 가까울수록 졸리다. */
    val sleepiness: Double = 80.0,
    val lastPetAt: Long = 0,
    val lastFeedAt: Long = 0,
    val lastSnackAt: Long = 0,
    val lastPlayAt: Long = 0,
    val lastBrushAt: Long = 0,
    /** 이 시각까지 잠든 상태 (재우기, 또는 졸림이 바닥나서 잠듦). */
    val sleepUntil: Long = 0,
    /** 진화 조건을 채워서 진화 버튼을 누를 수 있는 상태. */
    val evolutionReady: Boolean = false,
    /** "진화할 수 있어요" 알림을 이미 보냈는지. 진화하면 다시 false. */
    val evolutionNotified: Boolean = false,
) {
    val average: Double get() = (hunger + mood + cleanliness + sleepiness) / 4.0
    fun isAsleep(now: Long): Boolean = now < sleepUntil
}

/** 두 마리가 함께 쓰는 것 (SPEC 4-2). 항상 id = 0 한 줄. */
@Entity(tableName = "game")
data class GameEntity(
    @PrimaryKey val id: Int = 0,
    val gold: Int = 0,
    val friendship: Int = 30,
    val food: Int = 0,
    val snack: Int = 0,
    val toy: Int = 0,
    val brush: Int = 0,
    /** 상태 감소를 마지막으로 계산한 시각. */
    val lastTickAt: Long = 0,
    /** 평균 상태 기록을 마지막으로 남긴 시각 (1시간 간격). */
    val lastSampleAt: Long = 0,
    val createdAt: Long = 0,

    // ---- 2단계 (DB 버전 2) ----
    /** 사이좋음 자연 감소(하루 -2)를 마지막으로 반영한 날 (epoch day, 0 = 아직 없음). */
    @ColumnInfo(defaultValue = "0") val friendshipDay: Long = 0,
    /** 질투: 마지막으로 쓰다듬은 고양이와 연속 횟수 (SPEC 5). */
    @ColumnInfo(defaultValue = "") val lastPetCat: String = "",
    @ColumnInfo(defaultValue = "0") val petStreak: Int = 0,
    /** 걸음 수 (SPEC 5-1): 오늘 날짜, 오늘 걸음, 마지막 센서 값(-1 = 없음), 오늘 걸음으로 준 경험치, 1만 보 보너스 지급 여부 */
    @ColumnInfo(defaultValue = "0") val stepDay: Long = 0,
    @ColumnInfo(defaultValue = "0") val todaySteps: Int = 0,
    @ColumnInfo(defaultValue = "-1") val lastStepSensor: Long = -1,
    @ColumnInfo(defaultValue = "0") val stepExpToday: Int = 0,
    @ColumnInfo(defaultValue = "0") val stepGoldGiven: Boolean = false,
    /** 합체 (SPEC 6-3): 이 시각까지 쿵덕몬, 마지막으로 합체한 날, 도감 등록 여부 */
    @ColumnInfo(defaultValue = "0") val fusionUntil: Long = 0,
    @ColumnInfo(defaultValue = "0") val lastFusionDay: Long = 0,
    @ColumnInfo(defaultValue = "0") val fusionSeen: Boolean = false,
    /** 매일 선물 (SPEC 5-2): 마지막으로 받은 날 */
    @ColumnInfo(defaultValue = "0") val lastGiftDay: Long = 0,
) {
    fun isFused(now: Long): Boolean = now < fusionUntil
}

/** 진화 조건(최근 N일 평균 상태)을 위한 1시간 간격 기록. */
@Entity(tableName = "condition_samples", indices = [Index("catId", "at")])
data class ConditionSample(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val catId: String,
    val at: Long,
    val value: Double,
)
