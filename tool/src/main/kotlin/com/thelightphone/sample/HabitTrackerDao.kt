package com.thelightphone.sample

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface HabitTrackerDao {

    @Insert
    fun insertPriority(priority: PriorityEntity): Long

    @Insert
    fun insertObjective(objective: ObjectiveEntity): Long

    @Insert
    fun insertRoutine(routine: RoutineEntity): Long

    @Insert
    fun insertCompletion(completion: RoutineCompletionEntity): Long

    @Delete
    fun deleteCompletion(completion: RoutineCompletionEntity)

    @Query("SELECT * FROM priorities ORDER BY id DESC LIMIT 1")
    fun getLatestPriority(): PriorityEntity?

    @Query("SELECT * FROM objectives WHERE priorityId = :priorityId ORDER BY orderIndex")
    fun getObjectivesForPriority(priorityId: Long): List<ObjectiveEntity>

    @Query("SELECT * FROM routines WHERE objectiveId = :objectiveId ORDER BY id")
    fun getRoutinesForObjective(objectiveId: Long): List<RoutineEntity>

    @Query("SELECT * FROM routine_completions WHERE routineId = :routineId AND date = :date LIMIT 1")
    fun getCompletionForRoutineOnDate(routineId: Long, date: String): RoutineCompletionEntity?

    @Query(
        "SELECT COUNT(*) FROM routine_completions " +
            "WHERE routineId = :routineId AND date BETWEEN :weekStart AND :weekEnd"
    )
    fun countCompletionsInRange(routineId: Long, weekStart: String, weekEnd: String): Int

    @Query("UPDATE routines SET name = :name, weeklyTarget = :weeklyTarget WHERE id = :routineId")
    fun updateRoutine(routineId: Long, name: String, weeklyTarget: Int)

    /** Relies on the routines->routine_completions foreign key CASCADE to also remove its completions. */
    @Query("DELETE FROM routines WHERE id = :routineId")
    fun deleteRoutine(routineId: Long)
}
