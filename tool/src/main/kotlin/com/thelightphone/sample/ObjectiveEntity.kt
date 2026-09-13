package com.thelightphone.sample

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "objectives",
    foreignKeys = [
        ForeignKey(
            entity = PriorityEntity::class,
            parentColumns = ["id"],
            childColumns = ["priorityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("priorityId")],
)
data class ObjectiveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val priorityId: Long,
    val title: String,
    val orderIndex: Int,
)
