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

    data class CruisingState(
        val isTracking: Boolean = false,
        val currentSpeed: Float = 0f,
        val avgCruisingSpeed: Float = 0f,
        val maxSpeed: Float = 0f,
        val distanceKm: Float = 0.0f,
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

    private fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        _cruisingData.value = _cruisingData.value.copy(isTracking = false)

        fusedLocationClient.removeLocationUpdates(locationCallback)

        val currentTime = System.currentTimeMillis()
        val avgSpeed = _cruisingData.value.avgCruisingSpeed
        val maxSpeed = maxSpeedInternal

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
                        totalDistance = totalDistanceMeters
                    )
                )
            }
        }
    }

    fun resetData() {
        val lastAvgSpeed = _cruisingData.value.avgCruisingSpeed
        val lastMaxSpeed = maxSpeedInternal
        val lastTotalDistance = totalDistanceMeters
        val lastCurrentSessionId = currentSessionId
        val lastCurrentSession = currentSession

        totalSpeedSum = 0.0
        speedSamplesCount = 0
        maxSpeedInternal = 0.0f
        totalDistanceMeters = 0.0f
        lastDistanceLocation = null
        lastSaveLocation = null
        lastSaveTime = 0L
        lastSavedTotalDistance = -1f

        serviceScope.launch {
            if (isTracking && lastCurrentSessionId != 0L) {
                val endTime = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    lastCurrentSession?.let { session ->
                        database.sessionDao().update(
                            session.copy(
                                endTime = endTime,
                                avgSpeed = lastAvgSpeed,
                                maxSpeed = lastMaxSpeed,
                                totalDistance = lastTotalDistance
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

        _cruisingData.value = _cruisingData.value.copy(
            currentSpeed = speed,
            avgCruisingSpeed = avgSpeed,
            maxSpeed = maxSpeed,
            distanceKm = totalDistanceMeters / 1000f
        )
    }

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
