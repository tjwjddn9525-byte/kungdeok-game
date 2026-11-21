package com.kungdeok.game.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CatEntity::class, GameEntity::class, ConditionSample::class],
    version = 2,
    exportSchema = true,
    // 폰에 설치된 저장 데이터를 지우지 않고 올린다. 버전을 올릴 때마다 여기에 추가할 것.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): GameDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "kungdeok.db").build()
    }
}
