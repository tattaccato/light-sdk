package com.thelightphone.sample

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PriorityEntity::class,
        ObjectiveEntity::class,
        RoutineEntity::class,
        RoutineCompletionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class HabitTrackerDatabase : RoomDatabase() {
    internal abstract fun habitTrackerDao(): HabitTrackerDao
}
