package com.example.cymeter.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Insert
    suspend fun insert(locationPoint: LocationPoint)

    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getPointsBySessionId(sessionId: Long): List<LocationPoint>

    @Query("SELECT * FROM location_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getPointsFlowBySessionId(sessionId: Long): Flow<List<LocationPoint>>
}
