package io.github.tsuyokuro.cymeter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CruisingLogicManagerTest {

    private lateinit var logicManager: CruisingLogicManager
    private val thresholdKmh = 5f
    private val thresholdMps = thresholdKmh / 3.6f

    @Before
    fun setUp() {
        logicManager = CruisingLogicManager(
            speedThresholdMps = thresholdMps,
            rollingWindowMs = 10000L, // 10s for easier testing
            minSegmentDurationMs = 5000L, // 5s for easier testing
            lpfAlpha = 0f // Disable LPF for logic verification
        )
    }

    @Test
    fun `test moving and elapsed time tracking`() {
        // Threshold is 5 km/h (1.38 m/s)
        
        // 1. Initial update at 1000ms, speed 10 km/h
        var result = logicManager.onLocationUpdate(1000L, 10f / 3.6f, 10f)
        assertEquals(0L, result.movingTimeMs)
        assertEquals(0L, result.elapsedTimeMs)

        // 2. Next update at 2000ms (+1s), speed 10 km/h (above threshold)
        result = logicManager.onLocationUpdate(2000L, 10f / 3.6f, 10f)
        assertEquals(1000L, result.elapsedTimeMs)
        assertEquals(1000L, result.movingTimeMs)

        // 3. Next update at 5000ms (+3s), speed 2 km/h (below threshold)
        result = logicManager.onLocationUpdate(5000L, 2f / 3.6f, 2f)
        assertEquals(4000L, result.elapsedTimeMs)
        assertEquals(1000L, result.movingTimeMs) // Current speed (2) is below threshold, so 3s NOT added

        // 4. Next update at 10000ms (+5s), speed 10 km/h (above threshold)
        result = logicManager.onLocationUpdate(10000L, 10f / 3.6f, 10f)
        assertEquals(9000L, result.elapsedTimeMs)
        assertEquals(6000L, result.movingTimeMs) // Current speed (10) is above threshold, so 5s ADDED (1000 + 5000)
    }

    @Test
    fun `test basic speed and distance tracking`() {
        val currentTime = 1000L
        val speed = 10f / 3.6f // 10 km/h
        
        val result = logicManager.onLocationUpdate(currentTime, speed, 10f)
        
        assertEquals(speed, result.currentSpeed, 0.001f)
        assertEquals(speed, result.avgSpeed, 0.001f)
        assertEquals(10f, result.totalDistanceMeters, 0.001f)
    }

    @Test
    fun `test average speed excludes values below threshold`() {
        logicManager.onLocationUpdate(1000L, 10f / 3.6f, 10f) // Above
        logicManager.onLocationUpdate(2000L, 2f / 3.6f, 2f)   // Below
        
        val result = logicManager.onLocationUpdate(3000L, 10f / 3.6f, 10f) // Above
        
        // Only 10 km/h samples are counted
        assertEquals(10f, result.avgSpeed * 3.6f, 0.1f)
    }

    @Test
    fun `test rolling average hold logic`() {
        // Window 10s. Threshold 5 km/h.
        logicManager.onLocationUpdate(1000L, 10f / 3.6f, 10f)
        logicManager.onLocationUpdate(2000L, 20f / 3.6f, 10f)
        var result = logicManager.onLocationUpdate(3000L, 30f / 3.6f, 10f)
        
        // Size = 3. Top 70% = ceil(2.1) = 3 points [30, 20, 10]. Avg = 20.
        assertEquals(20f, result.rollingSpeed * 3.6f, 0.1f)
        assertFalse(result.isRollingHeld)

        // Drop below threshold at 4000L
        result = logicManager.onLocationUpdate(4000L, 0f, 0f)
        // 0 is ignored in average calculation but NOT in window. Window has [10, 20, 30, 0].
        // Valid points are [10, 20, 30]. Top 70% of 3 is 3 -> avg 20.
        assertEquals(20f, result.rollingSpeed * 3.6f, 0.1f)
        assertFalse(result.isRollingHeld)

        // Expire all valid points (Wait until 14000L)
        result = logicManager.onLocationUpdate(14000L, 0f, 0f)
        // Window only has (4000, 0) and (14000, 0). No valid points.
        // Should HOLD last valid average (20).
        assertEquals(20f, result.rollingSpeed * 3.6f, 0.1f)
        assertTrue(result.isRollingHeld)
    }

    @Test
    fun `test segment analysis`() {
        // Min segment duration is 5s
        
        // Segment A: 6 seconds above threshold
        logicManager.onLocationUpdate(1000L, 10f / 3.6f, 0f)
        logicManager.onLocationUpdate(7000L, 10f / 3.6f, 60f) // 6s, 60m -> 10m/s = 36km/h
        
        // During tracking, if current segment > 5s, it should be reflected
        var result = logicManager.onLocationUpdate(7000L, 10f / 3.6f, 0f)
        assertEquals(36f, result.representativeCruisingSpeed * 3.6f, 0.1f)

        // Drop below threshold to finalize segment
        result = logicManager.onLocationUpdate(8000L, 0f, 0f)
        
        // Segment finalized at 8000. Duration = 8000 - 1000 = 7000ms.
        // Distance = 60m. Avg = 60/7 = 8.57 m/s = 30.85 km/h.
        assertEquals(30.85f, result.representativeCruisingSpeed * 3.6f, 0.1f)
        assertEquals(30.85f, result.bestSegmentSpeed * 3.6f, 0.1f)

        // Segment B: 10 seconds above threshold, faster
        logicManager.onLocationUpdate(10000L, 20f / 3.6f, 0f)
        result = logicManager.onLocationUpdate(20000L, 20f / 3.6f, 200f) // 10s, 200m -> 20m/s = 72km/h
        
        // During tracking, if current segment > 5s, it should be reflected
        assertEquals(72f, result.bestSegmentSpeed * 3.6f, 0.1f)
    }

    @Test
    fun `test rolling average with different top percentage`() {
        val customLogic = CruisingLogicManager(
            speedThresholdMps = thresholdMps,
            rollingWindowMs = 10000L,
            lpfAlpha = 0f, // Disable LPF for logic verification
            rollingTopPercentage = 0.5f // Only top 50%
        )
        
        customLogic.onLocationUpdate(1000L, 10f / 3.6f, 10f)
        customLogic.onLocationUpdate(2000L, 20f / 3.6f, 10f)
        customLogic.onLocationUpdate(3000L, 30f / 3.6f, 10f)
        val result = customLogic.onLocationUpdate(4000L, 40f / 3.6f, 10f)
        
        // Size = 4. Top 50% = ceil(2.0) = 2 points [40, 30]. Avg = 35.
        assertEquals(35f, result.rollingSpeed * 3.6f, 0.1f)
    }
}
