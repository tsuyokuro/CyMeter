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
            minSegmentDurationMs = 5000L // 5s for easier testing
        )
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
    fun `test rolling average and hold logic`() {
        // Window is 10s
        logicManager.onLocationUpdate(1000L, 10f / 3.6f, 10f)
        var result = logicManager.onLocationUpdate(5000L, 20f / 3.6f, 20f)
        
        assertEquals(15f, result.rollingSpeed * 3.6f, 0.1f)
        assertFalse(result.isRollingHeld)

        // Stop moving
        result = logicManager.onLocationUpdate(6000L, 0f, 0f)
        
        // Window still has (1000, 10) and (5000, 20). 0 is ignored.
        assertEquals(15f, result.rollingSpeed * 3.6f, 0.1f)
        assertFalse(result.isRollingHeld)

        // Wait until window (10s) expires for ALL valid points
        result = logicManager.onLocationUpdate(16000L, 0f, 0f)
        
        // Valid points (at 1000 and 5000) are now out of window (16000 - 10000 = 6000)
        // Window only has (6000, 0), (12000, 0) and (16000, 0), all invalid.
        // It should HOLD the last valid rolling average (15 km/h in this case, 
        // since (1000, 10) and (5000, 20) were the last valid set before it became empty)
        assertEquals(15f, result.rollingSpeed * 3.6f, 0.1f)
        assertTrue(result.isRollingHeld)

        // Move again
        result = logicManager.onLocationUpdate(17000L, 25f / 3.6f, 5f)
        
        // Window has (12000, 0), (16000, 0), (17000, 25).
        // Only 25 is valid.
        assertEquals(25f, result.rollingSpeed * 3.6f, 0.1f)
        assertFalse(result.isRollingHeld)
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
    fun `test reset`() {
        logicManager.onLocationUpdate(1000L, 10f / 3.6f, 10f)
        logicManager.reset()
        
        val result = logicManager.onLocationUpdate(2000L, 5f / 3.6f, 5f)
        assertEquals(5f, result.totalDistanceMeters, 0.001f)
        assertEquals(5f, result.avgSpeed * 3.6f, 0.1f)
    }
}
