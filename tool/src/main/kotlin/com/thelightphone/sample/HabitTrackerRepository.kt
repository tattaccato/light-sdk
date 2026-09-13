package com.thelightphone.sample

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class RoutineWithProgress(
    val id: Long,
    val name: String,
    val weeklyTarget: Int,
    val doneToday: Boolean,
    val completionsThisWeek: Int,
    /** Consecutive past (fully elapsed, Mon-Sun) weeks where completions reached weeklyTarget. */
    val streak: Int,
)

data class ObjectiveWithRoutines(
    val id: Long,
    val title: String,
    val routines: List<RoutineWithProgress>,
)

data class PriorityOverview(
    val id: Long,
    val title: String,
    val quarter: String,
    val objectives: List<ObjectiveWithRoutines>,
)

private fun currentQuarterLabel(today: LocalDate): String {
    val quarter = (today.monthValue - 1) / 3 + 1
    return "${today.year}-Q$quarter"
}

private const val WEEKS_PER_QUARTER = 13

enum class WeekStatus { SUCCESS, MISS, FUTURE }

data class RoutineQuarterProgress(
    val name: String,
    val weeklyTarget: Int,
    /** One entry per week since the priority started, oldest first, always [WEEKS_PER_QUARTER] long. */
    val weeks: List<WeekStatus>,
    val streak: Int,
)

data class ObjectiveQuarterProgress(
    val title: String,
    val routines: List<RoutineQuarterProgress>,
)

data class QuarterOverview(
    val quarterLabel: String,
    val objectives: List<ObjectiveQuarterProgress>,
)

class HabitTrackerRepository private constructor(
    database: HabitTrackerDatabase,
) {
    private val dao = database.habitTrackerDao()

    /** Builds the full priority -> objectives -> routines (with today/week progress) view. */
    fun getOverview(today: LocalDate = LocalDate.now()): PriorityOverview? {
        val priority = dao.getLatestPriority() ?: return null
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)

        val objectives = dao.getObjectivesForPriority(priority.id).map { objective ->
            val routines = dao.getRoutinesForObjective(objective.id).map { routine ->
                RoutineWithProgress(
                    id = routine.id,
                    name = routine.name,
                    weeklyTarget = routine.weeklyTarget,
                    doneToday = dao.getCompletionForRoutineOnDate(routine.id, today.toString()) != null,
                    completionsThisWeek = dao.countCompletionsInRange(
                        routine.id,
                        weekStart.toString(),
                        weekEnd.toString(),
                    ),
                    streak = weeklyStreak(routine.id, routine.weeklyTarget, weekStart),
                )
            }
            ObjectiveWithRoutines(id = objective.id, title = objective.title, routines = routines)
        }

        return PriorityOverview(
            id = priority.id,
            title = priority.title,
            quarter = priority.quarter,
            objectives = objectives,
        )
    }

    /** Builds the 13-week (one quarter) success/miss/future grid for every routine, since the priority started. */
    fun getQuarterOverview(today: LocalDate = LocalDate.now()): QuarterOverview? {
        val priority = dao.getLatestPriority() ?: return null
        val firstWeekStart = LocalDate.parse(priority.startDate).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val objectives = dao.getObjectivesForPriority(priority.id).map { objective ->
            val routines = dao.getRoutinesForObjective(objective.id).map { routine ->
                val weeks = (0 until WEEKS_PER_QUARTER).map { weekIndex ->
                    val weekStart = firstWeekStart.plusWeeks(weekIndex.toLong())
                    if (weekStart >= currentWeekStart) {
                        WeekStatus.FUTURE
                    } else {
                        val weekEnd = weekStart.plusDays(6)
                        val count = dao.countCompletionsInRange(routine.id, weekStart.toString(), weekEnd.toString())
                        if (count >= routine.weeklyTarget) WeekStatus.SUCCESS else WeekStatus.MISS
                    }
                }
                RoutineQuarterProgress(
                    name = routine.name,
                    weeklyTarget = routine.weeklyTarget,
                    weeks = weeks,
                    streak = weeklyStreak(routine.id, routine.weeklyTarget, currentWeekStart),
                )
            }
            ObjectiveQuarterProgress(title = objective.title, routines = routines)
        }

        return QuarterOverview(quarterLabel = priority.quarter, objectives = objectives)
    }

    /**
     * Walks backward week by week from the last FULLY elapsed week (the one before [currentWeekStart]),
     * counting consecutive weeks that reached [weeklyTarget]. Stops at the first week that missed it.
     */
    private fun weeklyStreak(routineId: Long, weeklyTarget: Int, currentWeekStart: LocalDate): Int {
        var weekStart = currentWeekStart.minusWeeks(1)
        var streak = 0
        while (true) {
            val weekEnd = weekStart.plusDays(6)
            val count = dao.countCompletionsInRange(routineId, weekStart.toString(), weekEnd.toString())
            if (count < weeklyTarget) break
            streak++
            weekStart = weekStart.minusWeeks(1)
        }
        return streak
    }

    /** Adds a new routine under an existing objective. */
    fun addRoutine(objectiveId: Long, name: String, weeklyTarget: Int) {
        dao.insertRoutine(RoutineEntity(objectiveId = objectiveId, name = name, weeklyTarget = weeklyTarget))
    }

    /** Renames a routine and/or changes its weekly target. Does not affect its completion history. */
    fun updateRoutine(routineId: Long, name: String, weeklyTarget: Int) {
        dao.updateRoutine(routineId, name, weeklyTarget)
    }

    /** Deletes a routine and all of its completion history. */
    fun deleteRoutine(routineId: Long) {
        dao.deleteRoutine(routineId)
    }

    /** Marks a routine done for today, or un-marks it if it was already done. */
    fun toggleRoutineToday(routineId: Long, today: LocalDate = LocalDate.now()) {
        val dateString = today.toString()
        val existing = dao.getCompletionForRoutineOnDate(routineId, dateString)
        if (existing != null) {
            dao.deleteCompletion(existing)
        } else {
            dao.insertCompletion(RoutineCompletionEntity(routineId = routineId, date = dateString))
        }
    }

    /** First-run only: populates Ago's real quarterly priority/objectives/routines, with this week's progress. */
    fun seedIfEmpty(today: LocalDate = LocalDate.now()) {
        if (dao.getLatestPriority() != null) return

        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val priorityId = dao.insertPriority(
            PriorityEntity(
                title = "Procreare | concepire 153",
                quarter = currentQuarterLabel(today),
                startDate = today.toString(),
            )
        )

        val interioritaId = dao.insertObjective(
            ObjectiveEntity(priorityId = priorityId, title = "Interiorità", orderIndex = 0)
        )
        val ricaricaId = dao.insertRoutine(
            RoutineEntity(objectiveId = interioritaId, name = "Ricarica col Signore", weeklyTarget = 5)
        )
        seedCompletionsThisWeek(ricaricaId, weekStart, count = 4)

        val relazioniId = dao.insertObjective(
            ObjectiveEntity(priorityId = priorityId, title = "Relazioni", orderIndex = 1)
        )
        val techDetoxId = dao.insertRoutine(
            RoutineEntity(objectiveId = relazioniId, name = "Tech detox permanente", weeklyTarget = 5)
        )
        seedCompletionsThisWeek(techDetoxId, weekStart, count = 3)

        val risorseId = dao.insertObjective(
            ObjectiveEntity(priorityId = priorityId, title = "Risorse", orderIndex = 2)
        )
        val concepireId = dao.insertRoutine(
            RoutineEntity(objectiveId = risorseId, name = "Concepire 153: un'appun./w", weeklyTarget = 1)
        )
        seedCompletionsThisWeek(concepireId, weekStart, count = 2)

        val saluteId = dao.insertObjective(
            ObjectiveEntity(priorityId = priorityId, title = "Salute", orderIndex = 3)
        )
        val camminareId = dao.insertRoutine(
            RoutineEntity(objectiveId = saluteId, name = "Camminare sobrio e casto", weeklyTarget = 6)
        )
        seedCompletionsThisWeek(camminareId, weekStart, count = 7)
    }

    private fun seedCompletionsThisWeek(routineId: Long, weekStart: LocalDate, count: Int) {
        repeat(count) { offset ->
            dao.insertCompletion(
                RoutineCompletionEntity(routineId = routineId, date = weekStart.plusDays(offset.toLong()).toString())
            )
        }
    }

    companion object {
        const val DATABASE_NAME = "habit_tracker.db"

        @Volatile
        private var instance: HabitTrackerRepository? = null

        fun getInstance(databaseProvider: () -> HabitTrackerDatabase): HabitTrackerRepository {
            return instance ?: synchronized(this) {
                instance ?: HabitTrackerRepository(databaseProvider()).also { instance = it }
            }
        }
    }
}
