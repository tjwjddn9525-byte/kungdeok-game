package com.kungdeok.game.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM cats")
    fun observeCats(): Flow<List<CatEntity>>

    @Query("SELECT * FROM game WHERE id = 0")
    fun observeGame(): Flow<GameEntity?>

    @Query("SELECT * FROM cats")
    suspend fun cats(): List<CatEntity>

    @Query("SELECT * FROM game WHERE id = 0")
    suspend fun game(): GameEntity?

    @Upsert
    suspend fun upsertCats(cats: List<CatEntity>)

    @Upsert
    suspend fun upsertGame(game: GameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSamples(samples: List<ConditionSample>)

    @Query("SELECT AVG(value) FROM condition_samples WHERE catId = :catId AND at >= :since")
    suspend fun averageSince(catId: String, since: Long): Double?

    @Query("DELETE FROM condition_samples WHERE at < :before")
    suspend fun pruneSamples(before: Long)
}
