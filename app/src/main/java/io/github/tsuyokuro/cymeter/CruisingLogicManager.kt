package io.github.tsuyokuro.cymeter

import java.util.ArrayDeque

/**
 * Manages the business logic for tracking speed, distance, and cruising segments.
 * This class is designed to be testable without Android framework dependencies.
 */
class CruisingLogicManager(
    var speedThresholdMps: Float,
    private val rollingWindowMs: Long = 30000L,
    private val minSegmentDurationMs: Long = 60000L
) {
    // Basic stats
    private var totalSpeedSum: Double = 0.0
    private var speedSamplesCount: Long = 0
    private var maxSpeedInternal: Float = 0.0f
    private var totalDistanceMeters: Float = 0.0f

    // Rolling Cruising Speed state
    private val rollingSamples = ArrayDeque<Pair<Long, Float>>()
    private var lastValidRollingSpeed: Float = 0f

    // Segment Analysis state
    private var segmentStartTime: Long = 0L
    private var segmentStartDistance: Float = 0f
    private val validSegments = mutableListOf<CruisingSegment>()

    data class CruisingSegment(
        val durationMs: Long,
        val distanceMeters: Float,
        val startDistanceMeters: Float,
        val endDistanceMeters: Float
    ) {
        val avgSpeed: Float get() = if (durationMs > 0) distanceMeters / (durationMs / 1000f) else 0f
    }

    data class LogicResult(
        val currentSpeed: Float,
        val avgSpeed: Float,
        val maxSpeed: Float,
        val totalDistanceMeters: Float,
        val rollingSpeed: Float,
        val isRollingHeld: Boolean,
        val representativeCruisingSpeed: Float,
        val bestSegmentSpeed: Float,
        val bestSegmentDistance: Float,
        val bestSegmentStartKm: Float,
        val bestSegmentEndKm: Float
    )

    /**
     * Updates the internal state with a new location point.
     * @param distanceIncrement The distance in meters since the last point.
     */
    fun onLocationUpdate(
        currentTime: Long,
        speed: Float,
        distanceIncrement: Float
    ): LogicResult {
        if (speed > maxSpeedInternal) {
            maxSpeedInternal = speed
        }

        if (speed >= speedThresholdMps) {
            totalSpeedSum += speed
            speedSamplesCount++
        }

        totalDistanceMeters += distanceIncrement

        // Rolling Speed Logic
        rollingSamples.add(currentTime to speed)
        while (rollingSamples.isNotEmpty() && currentTime - rollingSamples.peekFirst()!!.first > rollingWindowMs) {
            rollingSamples.removeFirst()
        }

        val validRollingSamples = rollingSamples.filter { it.second >= speedThresholdMps }
        val (rollingSpeed, isHeld) = if (validRollingSamples.isNotEmpty()) {
            val avg = validRollingSamples.map { it.second }.average().toFloat()
            lastValidRollingSpeed = avg
            avg to false
        } else {
            lastValidRollingSpeed to true
        }

        // Segment Logic
        if (speed >= speedThresholdMps) {
            if (segmentStartTime == 0L) {
                segmentStartTime = currentTime
                segmentStartDistance = totalDistanceMeters - distanceIncrement // Start from before this increment
            }
        } else {
            finalizeCurrentSegment(currentTime)
        }

        val liveMetrics = calculateLiveCruisingMetrics(currentTime)

        return LogicResult(
            currentSpeed = speed,
            avgSpeed = getAverageSpeed(),
            maxSpeed = maxSpeedInternal,
            totalDistanceMeters = totalDistanceMeters,
            rollingSpeed = rollingSpeed,
            isRollingHeld = isHeld,
            representativeCruisingSpeed = liveMetrics.representativeCruisingSpeed,
            bestSegmentSpeed = liveMetrics.bestSegmentSpeed,
            bestSegmentDistance = liveMetrics.bestSegmentDistance,
            bestSegmentStartKm = liveMetrics.bestSegmentStartKm,
            bestSegmentEndKm = liveMetrics.bestSegmentEndKm
        )
    }

    private fun getAverageSpeed(): Float {
        return if (speedSamplesCount > 0) (totalSpeedSum / speedSamplesCount).toFloat() else 0f
    }

    private fun calculateLiveCruisingMetrics(currentTime: Long): LiveMetrics {
        var totalValidDistance = validSegments.sumOf { it.distanceMeters.toDouble() }.toFloat()
        var totalValidDuration = validSegments.sumOf { it.durationMs.toDouble() }.toLong()

        val initialBest = validSegments.maxByOrNull { it.avgSpeed }
        var bestSegSpeed = initialBest?.avgSpeed ?: 0f
        var bestSegDist = initialBest?.distanceMeters ?: 0f
        var bestSegStart = (initialBest?.startDistanceMeters ?: 0f) / 1000f
        var bestSegEnd = (initialBest?.endDistanceMeters ?: 0f) / 1000f

        if (segmentStartTime > 0) {
            val currentDuration = currentTime - segmentStartTime
            if (currentDuration >= minSegmentDurationMs) {
                val currentDistance = totalDistanceMeters - segmentStartDistance
                totalValidDistance += currentDistance
                totalValidDuration += currentDuration

                val currentAvgSpeed = currentDistance / (currentDuration / 1000f)
                if (currentAvgSpeed > bestSegSpeed) {
                    bestSegSpeed = currentAvgSpeed
                    bestSegDist = currentDistance
                    bestSegStart = segmentStartDistance / 1000f
                    bestSegEnd = totalDistanceMeters / 1000f
                }
            }
        }

        val repCruisingSpeed = if (totalValidDuration > 0) {
            totalValidDistance / (totalValidDuration / 1000f)
        } else 0f

        return LiveMetrics(
            representativeCruisingSpeed = repCruisingSpeed,
            bestSegmentSpeed = bestSegSpeed,
            bestSegmentDistance = bestSegDist,
            bestSegmentStartKm = bestSegStart,
            bestSegmentEndKm = bestSegEnd
        )
    }

    fun stop(currentTime: Long): LogicResult {
        finalizeCurrentSegment(currentTime)
        val metrics = calculateLiveCruisingMetrics(currentTime)
        return LogicResult(
            currentSpeed = 0f,
            avgSpeed = getAverageSpeed(),
            maxSpeed = maxSpeedInternal,
            totalDistanceMeters = totalDistanceMeters,
            rollingSpeed = 0f,
            isRollingHeld = false,
            representativeCruisingSpeed = metrics.representativeCruisingSpeed,
            bestSegmentSpeed = metrics.bestSegmentSpeed,
            bestSegmentDistance = metrics.bestSegmentDistance,
            bestSegmentStartKm = metrics.bestSegmentStartKm,
            bestSegmentEndKm = metrics.bestSegmentEndKm
        )
    }

    fun reset() {
        totalSpeedSum = 0.0
        speedSamplesCount = 0
        maxSpeedInternal = 0.0f
        totalDistanceMeters = 0.0f
        rollingSamples.clear()
        lastValidRollingSpeed = 0f
        segmentStartTime = 0L
        segmentStartDistance = 0f
        validSegments.clear()
    }

    private fun finalizeCurrentSegment(currentTime: Long) {
        if (segmentStartTime > 0) {
            val duration = currentTime - segmentStartTime
            if (duration >= minSegmentDurationMs) {
                val distance = totalDistanceMeters - segmentStartDistance
                validSegments.add(
                    CruisingSegment(
                        durationMs = duration,
                        distanceMeters = distance,
                        startDistanceMeters = segmentStartDistance,
                        endDistanceMeters = totalDistanceMeters
                    )
                )
            }
            segmentStartTime = 0L
            segmentStartDistance = 0f
        }
    }

    private data class LiveMetrics(
        val representativeCruisingSpeed: Float,
        val bestSegmentSpeed: Float,
        val bestSegmentDistance: Float,
        val bestSegmentStartKm: Float,
        val bestSegmentEndKm: Float
    )
}
