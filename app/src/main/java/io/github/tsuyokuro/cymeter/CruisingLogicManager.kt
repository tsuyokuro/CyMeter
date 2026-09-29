package io.github.tsuyokuro.cymeter

import java.util.ArrayDeque
import kotlin.math.ceil

/**
 * Manages the business logic for tracking speed, distance, and cruising segments.
 * This class is designed to be testable without Android framework dependencies.
 */
class CruisingLogicManager(
    var speedThresholdMps: Float,
    private val rollingWindowMs: Long = 30000L,
    private val minSegmentDurationMs: Long = 60000L,
    private val lpfAlpha: Float = 0.2f,
    private val rollingTopPercentage: Float = 0.7f
) {
    // Basic stats
    private var totalSpeedSum: Double = 0.0
    private var speedSamplesCount: Long = 0
    private var maxSpeedInternal: Float = 0.0f
    private var totalDistanceMeters: Float = 0.0f

    // Time tracking
    private var sessionStartTime: Long = 0L
    private var lastUpdateTime: Long = 0L
    private var movingTimeMs: Long = 0L

    // Rolling Cruising Speed state
    private val rollingSamples = ArrayDeque<Pair<Long, Float>>()
    private var lastValidRollingSpeed: Float = 0f

    // Segment Analysis state
    private var segmentStartTime: Long = 0L
    private var segmentStartDistance: Float = 0f
    private var segmentRollingSpeedSum: Double = 0.0
    private var segmentRollingSpeedCount: Long = 0
    private val validSegments = mutableListOf<CruisingSegment>()

    // Speed LPF
    private var lpfSpeed: Float = 0f
    private var isFirstUpdate = true

    data class CruisingSegment(
        val durationMs: Long,
        val distanceMeters: Float,
        val startDistanceMeters: Float,
        val endDistanceMeters: Float,
        val avgSpeed: Float
    )

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
        val bestSegmentEndKm: Float,
        val movingTimeMs: Long,
        val elapsedTimeMs: Long
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
        if (isFirstUpdate) {
            lpfSpeed = (1 - lpfAlpha) * speed
            isFirstUpdate = false
            sessionStartTime = currentTime
            lastUpdateTime = currentTime
        } else {
            lpfSpeed = lpfAlpha * lpfSpeed + (1 - lpfAlpha) * speed
            val timeDelta = (currentTime - lastUpdateTime).coerceAtMost(5000L) // Limit delta to avoid jumps
            if (lpfSpeed >= speedThresholdMps) {
                movingTimeMs += timeDelta
            }
            lastUpdateTime = currentTime
        }

        val currentSpeed = lpfSpeed

        if (currentSpeed > maxSpeedInternal) {
            maxSpeedInternal = currentSpeed
        }

        if (currentSpeed >= speedThresholdMps) {
            totalSpeedSum += currentSpeed
            speedSamplesCount++
        }

        totalDistanceMeters += distanceIncrement

        // Rolling Speed Logic
        while (rollingSamples.isNotEmpty() && currentTime - rollingSamples.peekFirst()!!.first > rollingWindowMs) {
            rollingSamples.removeFirst()
        }

        if (currentSpeed >= speedThresholdMps) {
            rollingSamples.add(currentTime to currentSpeed)
        }

        val (rollingSpeed, isHeld) = if (rollingSamples.isNotEmpty()) {
            val sortedSpeeds = rollingSamples.map { it.second.toDouble() }.sortedDescending()
            // Top N%
            val countToTake = ceil(sortedSpeeds.size * rollingTopPercentage.toDouble()).toInt().coerceAtLeast(1)
            val avg = sortedSpeeds.take(countToTake).average().toFloat()
            lastValidRollingSpeed = avg
            avg to false
        } else {
            lastValidRollingSpeed to true
        }

        // Segment Logic
        if (currentSpeed >= speedThresholdMps) {
            if (segmentStartTime == 0L) {
                segmentStartTime = currentTime
                segmentStartDistance = totalDistanceMeters - distanceIncrement // Start from before this increment
                rollingSamples.clear()
                rollingSamples.add(currentTime to currentSpeed)
                segmentRollingSpeedSum = 0.0
                segmentRollingSpeedCount = 0
            }

            segmentRollingSpeedSum += rollingSpeed
            segmentRollingSpeedCount++
        } else {
            finalizeCurrentSegment(currentTime)
        }

        val liveMetrics = calculateLiveCruisingMetrics(currentTime)

        return LogicResult(
            currentSpeed = currentSpeed,
            avgSpeed = getAverageSpeed(),
            maxSpeed = maxSpeedInternal,
            totalDistanceMeters = totalDistanceMeters,
            rollingSpeed = rollingSpeed,
            isRollingHeld = isHeld,
            representativeCruisingSpeed = liveMetrics.representativeCruisingSpeed,
            bestSegmentSpeed = liveMetrics.bestSegmentSpeed,
            bestSegmentDistance = liveMetrics.bestSegmentDistance,
            bestSegmentStartKm = liveMetrics.bestSegmentStartKm,
            bestSegmentEndKm = liveMetrics.bestSegmentEndKm,
            movingTimeMs = movingTimeMs,
            elapsedTimeMs = currentTime - sessionStartTime
        )
    }

    private fun getAverageSpeed(): Float {
        return if (speedSamplesCount > 0) (totalSpeedSum / speedSamplesCount).toFloat() else 0f
    }

    private fun calculateLiveCruisingMetrics(currentTime: Long): LiveMetrics {
        var totalWeightedSpeedSum = validSegments.sumOf { it.avgSpeed.toDouble() * it.durationMs }
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
                val currentAvgSpeed = if (segmentRollingSpeedCount > 0) {
                    (segmentRollingSpeedSum / segmentRollingSpeedCount).toFloat()
                } else 0f

                totalWeightedSpeedSum += currentAvgSpeed.toDouble() * currentDuration
                totalValidDuration += currentDuration

                if (currentAvgSpeed > bestSegSpeed) {
                    bestSegSpeed = currentAvgSpeed
                    bestSegDist = currentDistance
                    bestSegStart = segmentStartDistance / 1000f
                    bestSegEnd = totalDistanceMeters / 1000f
                }
            }
        }

        val repCruisingSpeed = if (totalValidDuration > 0) {
            (totalWeightedSpeedSum / totalValidDuration).toFloat()
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
            bestSegmentEndKm = metrics.bestSegmentEndKm,
            movingTimeMs = movingTimeMs,
            elapsedTimeMs = currentTime - sessionStartTime
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
        segmentRollingSpeedSum = 0.0
        segmentRollingSpeedCount = 0
        validSegments.clear()
        lpfSpeed = 0f
        isFirstUpdate = true
        sessionStartTime = 0L
        lastUpdateTime = 0L
        movingTimeMs = 0L
    }

    private fun finalizeCurrentSegment(currentTime: Long) {
        if (segmentStartTime > 0) {
            val duration = currentTime - segmentStartTime
            if (duration >= minSegmentDurationMs) {
                val distance = totalDistanceMeters - segmentStartDistance
                val segmentAvgSpeed = if (segmentRollingSpeedCount > 0) {
                    (segmentRollingSpeedSum / segmentRollingSpeedCount).toFloat()
                } else 0f

                validSegments.add(
                    CruisingSegment(
                        durationMs = duration,
                        distanceMeters = distance,
                        startDistanceMeters = segmentStartDistance,
                        endDistanceMeters = totalDistanceMeters,
                        avgSpeed = segmentAvgSpeed
                    )
                )
            }
            segmentStartTime = 0L
            segmentStartDistance = 0f
            segmentRollingSpeedSum = 0.0
            segmentRollingSpeedCount = 0
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
