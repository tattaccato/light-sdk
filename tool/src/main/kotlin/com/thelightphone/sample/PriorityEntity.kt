package com.thelightphone.sample

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "priorities")
data class PriorityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val quarter: String,
    /** ISO-8601 date this priority was created - anchors the quarter progress grid's week 1. */
    val startDate: String,
)
