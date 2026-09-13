package com.thelightphone.sample

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_completions",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("routineId"),
        Index(value = ["routineId", "date"], unique = true),
    ],
)
data class RoutineCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    /** ISO-8601 date ("yyyy-MM-dd") of the day this routine was marked done. One row per day. */
    val date: String,
)
