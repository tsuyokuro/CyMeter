package com.example.cymeter.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_points")
data class LocationPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val avgSpeed: Float,
    val totalDistanceMeters: Float,
    val movingTimeMillis: Long,
    val timestamp: Long
)
