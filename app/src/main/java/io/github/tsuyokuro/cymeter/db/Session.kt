package io.github.tsuyokuro.cymeter.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long = 0,
    val avgSpeed: Float = 0f,
    val totalDistance: Float = 0f,
    val maxSpeed: Float = 0f,
    val representativeCruisingSpeed: Float = 0f,
    val bestSegmentSpeed: Float = 0f,
    val bestSegmentDistance: Float = 0f,
    val bestSegmentStartKm: Float = 0f,
    val bestSegmentEndKm: Float = 0f
)
