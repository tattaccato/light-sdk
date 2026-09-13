package com.thelightphone.sample

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routines",
    foreignKeys = [
        ForeignKey(
            entity = ObjectiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["objectiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("objectiveId")],
)
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val objectiveId: Long,
    val name: String,
    /** Minimum number of completions in a Monday-Sunday week for that week to count as a success. */
    val weeklyTarget: Int,
)
