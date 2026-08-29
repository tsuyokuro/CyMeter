package io.github.tsuyokuro.cymeter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.tsuyokuro.cymeter.db.AppDatabase
import io.github.tsuyokuro.cymeter.db.LocationPoint
import io.github.tsuyokuro.cymeter.db.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class CruisingService : Service() {

    companion object {
        private const val SPEED_LPF_ALPHA = 0.5f

        private const val DISTANCE_TIME_INTERVAL_MS = 2000L
        private const val SAVE_DISTANCE_THRESHOLD_METERS = 2.0f
        private const val SAVE_TIME_FALLBACK_MS = 30000L

        private const val ROLLING_WINDOW_MS = 30000L
        private const val MIN_SEGMENT_DURATION_MS = 60000L
    }

    private val binder = LocalBinder()

    private lateinit var settingsRepository: SettingsRepository
    private var speedThresholdMps: Float = 5.0f / 3.6f

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // DB
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentSessionId: Long = 0L

    @Volatile
    private var currentSession: Session? = null
    private var lastSaveTime: Long = 0L
    private var lastSaveLocation: Location? = null
    private val database by lazy { AppDatabase.getDatabase(this) }


    private val _cruisingData = MutableStateFlow<CruisingState>(CruisingState())
    val cruisingData: StateFlow<CruisingState> = _cruisingData.asStateFlow()

    private var totalSpeedSum: Double = 0.0
    private var speedSamplesCount: Long = 0
    private var maxSpeedInternal: Float = 0.0f

    private var lpfSpeed = 0.0f

    private var lastDistanceLocation: Location? = null
    private var totalDistanceMeters: Float = 0.0f
    private var lastSavedTotalDistance: Float = -1f

    // Rolling Cruising Speed
    private val rollingSamples = java.util.ArrayDeque<Pair<Long, Float>>()
    private var lastValidRollingSpeed: Float = 0f

    // Segment Analysis
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

    data class CruisingState(
        val isTracking: Boolean = false,
        val currentSpeed: Float = 0f,
        val avgCruisingSpeed: Float = 0f,
        val rollingCruisingSpeed: Float = 0f,
        val isRollingSpeedHeld: Boolean = false,
        val maxSpeed: Float = 0f,
        val distanceKm: Float = 0.0f,
        val representativeCruisingSpeed: Float = 0f,
        val bestSegmentSpeed: Float = 0f,
        val bestSegmentDistance: Float = 0f,
        val bestSegmentStartKm: Float = 0f,
        val bestSegmentEndKm: Float = 0f,
        val sessionId: Long = 0L,
        val isViewingHistory: Boolean = false
    )

    inner class LocalBinder : Binder() {
        fun getService(): CruisingService = this@CruisingService
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        serviceScope.launch {
            settingsRepository.speedThresholdFlow.collect { thresholdKmh ->
                speedThresholdMps = thresholdKmh / 3.6f
            }
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    trackLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        startTracking()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun startForegroundService() {
        val channelId = "cruising_service_channel"
        val channel = NotificationChannel(
            channelId,
            "Cruising Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("CyMeter Cruising")
            .setContentText("Tracking speed and acceleration")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private var isTracking = false

    private fun startTracking() {
        if (isTracking) return
        isTracking = true
        _cruisingData.value = _cruisingData.value.copy(isTracking = true)

        serviceScope.launch {
            val startTime = System.currentTimeMillis()
            currentSessionId = withContext(Dispatchers.IO) {
                val session = Session(startTime = startTime)
                val id = database.sessionDao().insert(session)
                currentSession = session.copy(id = id)
                id
            }
            _cruisingData.value = _cruisingData.value.copy(sessionId = currentSessionId)

            lastSaveTime = 0L
            lastSaveLocation = null
            lastSavedTotalDistance = -1f
            
            rollingSamples.clear()
            lastValidRollingSpeed = 0f
            segmentStartTime = 0L
            segmentStartDistance = 0f
            validSegments.clear()

            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .build()

            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            } catch (_: SecurityException) {
                // Permissions should be handled in Activity
            }
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false

        fusedLocationClient.removeLocationUpdates(locationCallback)

        val currentTime = System.currentTimeMillis()
        val avgSpeed = _cruisingData.value.avgCruisingSpeed
        val maxSpeed = maxSpeedInternal

        // Finalize current segment if valid
        finalizeCurrentSegment(currentTime)

        val totalValidDistance = validSegments.sumOf { it.distanceMeters.toDouble() }.toFloat()
        val totalValidDuration = validSegments.sumOf { it.durationMs.toDouble() }.toLong()
        val repCruisingSpeed = if (totalValidDuration > 0) {
            totalValidDistance / (totalValidDuration / 1000f)
        } else 0f

        val bestSegment = validSegments.maxByOrNull { it.avgSpeed }
        val bestSegSpeed = bestSegment?.avgSpeed ?: 0f
        val bestSegDist = bestSegment?.distanceMeters ?: 0f
        val bestSegStart = (bestSegment?.startDistanceMeters ?: 0f) / 1000f
        val bestSegEnd = (bestSegment?.endDistanceMeters ?: 0f) / 1000f

        // Update state with final calculated values
        _cruisingData.value = _cruisingData.value.copy(
            isTracking = false,
            representativeCruisingSpeed = repCruisingSpeed,
            bestSegmentSpeed = bestSegSpeed,
            bestSegmentDistance = bestSegDist,
            bestSegmentStartKm = bestSegStart,
            bestSegmentEndKm = bestSegEnd
        )

        kotlinx.coroutines.runBlocking {
            if (lastDistanceLocation != null) {
                val point = LocationPoint(
                    sessionId = currentSessionId,
                    latitude = lastDistanceLocation!!.latitude,
                    longitude = lastDistanceLocation!!.longitude,
                    altitude = lastDistanceLocation!!.altitude,
                    speed = lpfSpeed,
                    avgSpeed = avgSpeed,
                    totalDistanceMeters = totalDistanceMeters,
                    timestamp = currentTime
                )
                database.locationDao().insert(point)
            }

            currentSession?.let { session ->
                database.sessionDao().update(
                    session.copy(
                        endTime = currentTime,
                        avgSpeed = avgSpeed,
                        maxSpeed = maxSpeed,
                        totalDistance = totalDistanceMeters,
                        representativeCruisingSpeed = repCruisingSpeed,
                        bestSegmentSpeed = bestSegSpeed,
                        bestSegmentDistance = bestSegDist,
                        bestSegmentStartKm = bestSegStart,
                        bestSegmentEndKm = bestSegEnd
                    )
                )
            }
        }
    }

    private fun finalizeCurrentSegment(currentTime: Long) {
        if (segmentStartTime > 0) {
            val duration = currentTime - segmentStartTime
            if (duration >= MIN_SEGMENT_DURATION_MS) {
                val distance = totalDistanceMeters - segmentStartDistance
                validSegments.add(
                    CruisingSegment(
                        duration,
                        distance,
                        segmentStartDistance,
                        totalDistanceMeters
                    )
                )
            }
            segmentStartTime = 0L
            segmentStartDistance = 0f
        }
    }

    fun resetData() {
        val currentTime = System.currentTimeMillis()
        val lastAvgSpeed = _cruisingData.value.avgCruisingSpeed
        val lastMaxSpeed = maxSpeedInternal
        val lastTotalDistance = totalDistanceMeters
        val lastCurrentSessionId = currentSessionId
        val lastCurrentSession = currentSession
        
        // Finalize segment for the resetting session
        finalizeCurrentSegment(currentTime)
        val totalValidDistance = validSegments.sumOf { it.distanceMeters.toDouble() }.toFloat()
        val totalValidDuration = validSegments.sumOf { it.durationMs.toDouble() }.toLong()
        val repCruisingSpeed = if (totalValidDuration > 0) {
            totalValidDistance / (totalValidDuration / 1000f)
        } else 0f
        
        val bestSegment = validSegments.maxByOrNull { it.avgSpeed }
        val bestSegSpeed = bestSegment?.avgSpeed ?: 0f
        val bestSegDist = bestSegment?.distanceMeters ?: 0f

        totalSpeedSum = 0.0
        speedSamplesCount = 0
        maxSpeedInternal = 0.0f
        totalDistanceMeters = 0.0f
        lastDistanceLocation = null
        lastSaveLocation = null
        lastSaveTime = 0L
        lastSavedTotalDistance = -1f
        
        rollingSamples.clear()
        lastValidRollingSpeed = 0f
        segmentStartTime = 0L
        segmentStartDistance = 0f
        validSegments.clear()

        serviceScope.launch {
            if (isTracking && lastCurrentSessionId != 0L) {
                withContext(Dispatchers.IO) {
                    lastCurrentSession?.let { session ->
                        database.sessionDao().update(
                            session.copy(
                                endTime = currentTime,
                                avgSpeed = lastAvgSpeed,
                                maxSpeed = lastMaxSpeed,
                                totalDistance = lastTotalDistance,
                                representativeCruisingSpeed = repCruisingSpeed,
                                bestSegmentSpeed = bestSegSpeed,
                                bestSegmentDistance = bestSegDist
                            )
                        )
                    }
                }
            }

            val startTime = System.currentTimeMillis()
            currentSessionId = withContext(Dispatchers.IO) {
                val newSession = Session(startTime = startTime)
                val id = database.sessionDao().insert(newSession)
                currentSession = newSession.copy(id = id)
                id
            }

            _cruisingData.value = CruisingState(
                isTracking = isTracking,
                currentSpeed = _cruisingData.value.currentSpeed,
                avgCruisingSpeed = 0f,
                maxSpeed = 0f,
                distanceKm = 0.0f,
                sessionId = currentSessionId
            )
        }
    }

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun trackLocation(location: Location) {

        lpfSpeed = SPEED_LPF_ALPHA * lpfSpeed + (1.0f - SPEED_LPF_ALPHA) * location.speed

        val speed = lpfSpeed
        val currentTime = System.currentTimeMillis()

        if (speed > maxSpeedInternal) {
            maxSpeedInternal = speed
        }

        if (speed > speedThresholdMps) {
            totalSpeedSum += speed
            speedSamplesCount++
        }

        val avgSpeed =
            if (speedSamplesCount > 0) (totalSpeedSum / speedSamplesCount).toFloat() else 0f
        val maxSpeed = maxSpeedInternal

        totalDistanceMeters += calcDistance(lastDistanceLocation, location)
        lastDistanceLocation = location
        
        // Rolling Speed Logic
        rollingSamples.add(currentTime to speed)
        while (rollingSamples.isNotEmpty() && currentTime - rollingSamples.peekFirst()!!.first > ROLLING_WINDOW_MS) {
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
                segmentStartDistance = totalDistanceMeters
            }
        } else {
            finalizeCurrentSegment(currentTime)
        }

        if (currentTime - lastSaveTime >= DISTANCE_TIME_INTERVAL_MS) {
            if (lastSavedTotalDistance == -1f ||
                totalDistanceMeters - lastSavedTotalDistance >= SAVE_DISTANCE_THRESHOLD_METERS ||
                currentTime - lastSaveTime >= SAVE_TIME_FALLBACK_MS
            ) {
                writeLocationLog(
                    currentTime,
                    avgSpeed, maxSpeed,
                    totalDistanceMeters,
                    location)

                lastSaveLocation = location
                lastSaveTime = currentTime

                lastSavedTotalDistance = totalDistanceMeters
            }
        }

        // Live Cruising Metrics Calculation
        val liveMetrics = calculateLiveCruisingMetrics(currentTime)

        _cruisingData.value = _cruisingData.value.copy(
            currentSpeed = speed,
            avgCruisingSpeed = avgSpeed,
            rollingCruisingSpeed = rollingSpeed,
            isRollingSpeedHeld = isHeld,
            maxSpeed = maxSpeed,
            distanceKm = totalDistanceMeters / 1000f,
            representativeCruisingSpeed = liveMetrics.representativeCruisingSpeed,
            bestSegmentSpeed = liveMetrics.bestSegmentSpeed,
            bestSegmentDistance = liveMetrics.bestSegmentDistance,
            bestSegmentStartKm = liveMetrics.bestSegmentStartKm,
            bestSegmentEndKm = liveMetrics.bestSegmentEndKm
        )
    }

    private fun calculateLiveCruisingMetrics(currentTime: Long): LiveCruisingMetrics {
        var totalValidDistance = validSegments.sumOf { it.distanceMeters.toDouble() }.toFloat()
        var totalValidDuration = validSegments.sumOf { it.durationMs.toDouble() }.toLong()
        
        val initialBest = validSegments.maxByOrNull { it.avgSpeed }
        var bestSegSpeed = initialBest?.avgSpeed ?: 0f
        var bestSegDist = initialBest?.distanceMeters ?: 0f
        var bestSegStart = (initialBest?.startDistanceMeters ?: 0f) / 1000f
        var bestSegEnd = (initialBest?.endDistanceMeters ?: 0f) / 1000f

        if (segmentStartTime > 0) {
            val currentDuration = currentTime - segmentStartTime
            if (currentDuration >= MIN_SEGMENT_DURATION_MS) {
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

        return LiveCruisingMetrics(
            repCruisingSpeed,
            bestSegSpeed,
            bestSegDist,
            bestSegStart,
            bestSegEnd
        )
    }

    private data class LiveCruisingMetrics(
        val representativeCruisingSpeed: Float,
        val bestSegmentSpeed: Float,
        val bestSegmentDistance: Float,
        val bestSegmentStartKm: Float,
        val bestSegmentEndKm: Float
    )

    private fun writeLocationLog(
        timestamp: Long,
        avgSpeed: Float,
        maxSpeed: Float,
        totalDistanceMeters: Float,
        location: Location
    ) {

        val point = LocationPoint(
            sessionId = currentSessionId,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            speed = lpfSpeed,
            avgSpeed = avgSpeed,
            totalDistanceMeters = totalDistanceMeters,
            timestamp = timestamp
        )

        serviceScope.launch(Dispatchers.IO) {
            database.locationDao().insert(point)

            val session = currentSession
            if (session != null) {
                val updatedSession = session.copy(
                    avgSpeed = avgSpeed,
                    maxSpeed = maxSpeed,
                    totalDistance = totalDistanceMeters
                )
                database.sessionDao().update(updatedSession)
                currentSession = updatedSession
            }
        }
    }

    private fun calcDistance(lastLoc: Location?, newLocation: Location?): Float {

        if (lastLoc == null || newLocation == null) {
            return 0.0f
        }

        val results = FloatArray(1)
        Location.distanceBetween(
            lastLoc.latitude, lastLoc.longitude,
            newLocation.latitude, newLocation.longitude,
            results
        )

        return results[0]
    }
}
