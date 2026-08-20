package io.github.tsuyokuro.cymeter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
        private const val STOP_THRESHOLD = 0.5f
        private const val STOP_DURATION_MS = 2000L
        private const val ACCEL_LPF_ALPHA = 0.7f

        private const val SPEED_LPF_ALPHA = 0.5f

        private const val DISTANCE_TIME_INTERVAL_MS = 10000L
    }

    private val binder = LocalBinder()

    private lateinit var settingsRepository: SettingsRepository
    private var speedThresholdMps: Float = 5.0f / 3.6f

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager
    private var linearAccelerationSensor: Sensor? = null

    private lateinit var linearAccelerationListener: SensorEventListener


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

    private var lastBelowThresholdTime: Long = 0L
    private var isMovingInternal: Boolean = false
    private var totalSpeedSum: Double = 0.0
    private var speedSamplesCount: Long = 0
    private var maxSpeedInternal: Float = 0.0f

    private var lpfSpeed = 0.0f

    private var lastMovingTimeUpdate: Long = 0L
    private var totalMovingTimeMillis: Long = 0L

    private var lastDistanceLocation: Location? = null
    private var totalDistanceMeters: Float = 0.0f

    private var lpfAccelX = 0.0f
    private var lpfAccelY = 0.0f
    private var lpfAccelZ = 0.0f

    data class CruisingState(
        val isMoving: Boolean = false,
        val currentSpeed: Float = 0f,
        val avgCruisingSpeed: Float = 0f,
        val maxSpeed: Float = 0f,
        val movingTimeMillis: Long = 0L,
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
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    trackLocation(location)
                }
            }
        }

        linearAccelerationListener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            override fun onSensorChanged(event: SensorEvent?) {
                onAccelSensorChanged(event)
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

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("CyMeter Cruising")
            .setContentText("Tracking speed and acceleration")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private var isTracking = false

    private fun startTracking() {
        if (isTracking) return
        isTracking = true

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

            linearAccelerationSensor?.let {
                sensorManager.registerListener(
                    linearAccelerationListener,
                    it,
                    SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    private fun stopTracking() {
        if (!isTracking) return
        isTracking = false

        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(linearAccelerationListener)

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
                    movingTimeMillis = totalMovingTimeMillis,
                    timestamp = currentTime
                )
                database.locationDao().insert(point)
            }

            currentSession?.let { session ->
                database.sessionDao().update(session.copy(
                    endTime = currentTime,
                    avgSpeed = avgSpeed,
                    maxSpeed = maxSpeed,
                    totalDistance = totalDistanceMeters,
                    totalMovingTime = totalMovingTimeMillis
                ))
            }
        }
    }

    fun resetData() {
        val lastAvgSpeed = _cruisingData.value.avgCruisingSpeed
        val lastMaxSpeed = maxSpeedInternal
        val lastTotalDistance = totalDistanceMeters
        val lastTotalMovingTime = totalMovingTimeMillis
        val lastCurrentSessionId = currentSessionId
        val lastCurrentSession = currentSession

        totalSpeedSum = 0.0
        speedSamplesCount = 0
        maxSpeedInternal = 0.0f
        totalMovingTimeMillis = 0
        lastMovingTimeUpdate = if (isMovingInternal) System.currentTimeMillis() else 0L
        totalDistanceMeters = 0.0f
        lastDistanceLocation = null
        lastSaveLocation = null
        lastSaveTime = 0L

        serviceScope.launch {
            if (isTracking && lastCurrentSessionId != 0L) {
                val endTime = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    lastCurrentSession?.let { session ->
                        database.sessionDao().update(session.copy(
                            endTime = endTime,
                            avgSpeed = lastAvgSpeed,
                            maxSpeed = lastMaxSpeed,
                            totalDistance = lastTotalDistance,
                            totalMovingTime = lastTotalMovingTime
                        ))
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
                isMoving = isMovingInternal,
                currentSpeed = _cruisingData.value.currentSpeed,
                avgCruisingSpeed = 0f,
                maxSpeed = 0f,
                movingTimeMillis = 0L,
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

    private fun onAccelSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Apply Low-Pass Filter
            lpfAccelX = ACCEL_LPF_ALPHA * lpfAccelX + (1 - ACCEL_LPF_ALPHA) * x
            lpfAccelY = ACCEL_LPF_ALPHA * lpfAccelY + (1 - ACCEL_LPF_ALPHA) * y
            lpfAccelZ = ACCEL_LPF_ALPHA * lpfAccelZ + (1 - ACCEL_LPF_ALPHA) * z

            val magnitude = sqrt(lpfAccelX * lpfAccelX + lpfAccelY * lpfAccelY + lpfAccelZ * lpfAccelZ)
            val currentTime = System.currentTimeMillis()

            val oldIsMoving = isMovingInternal

            if (magnitude > STOP_THRESHOLD) {
                isMovingInternal = true
                lastBelowThresholdTime = 0L
            } else {
                if (lastBelowThresholdTime == 0L) {
                    lastBelowThresholdTime = currentTime
                } else if (currentTime - lastBelowThresholdTime > STOP_DURATION_MS) {
                    isMovingInternal = false
                }
            }

            if (oldIsMoving != isMovingInternal) {
                _cruisingData.value = _cruisingData.value.copy(
                    isMoving = isMovingInternal
                )
            }
        }
    }

    private fun trackLocation(location : Location) {

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

        if (isMovingInternal) {
            if (lastMovingTimeUpdate > 0) {
                totalMovingTimeMillis += (currentTime - lastMovingTimeUpdate)
            }
            lastMovingTimeUpdate = currentTime
        } else {
            lastMovingTimeUpdate = 0L
        }

        val avgSpeed = if (speedSamplesCount > 0) (totalSpeedSum / speedSamplesCount).toFloat() else 0f
        val maxSpeed = maxSpeedInternal

        totalDistanceMeters += calcDistance(lastDistanceLocation, location)
        lastDistanceLocation = location

        if (currentTime - lastSaveTime >= DISTANCE_TIME_INTERVAL_MS) {
            //val distance = calcDistance(lastLogLocation, location)
            //if (distance > 1.0f) {
                writeLocationLog(currentTime, avgSpeed, maxSpeed, totalDistanceMeters, location)
                lastSaveLocation = location
                lastSaveTime = currentTime
            //}
        }

        _cruisingData.value = _cruisingData.value.copy(
            currentSpeed = speed,
            avgCruisingSpeed = avgSpeed,
            maxSpeed = maxSpeed,
            movingTimeMillis = totalMovingTimeMillis,
            distanceKm = totalDistanceMeters / 1000f
        )
    }

    private fun writeLocationLog(
        timestamp: Long,
        avgSpeed: Float,
        maxSpeed: Float,
        totalDistanceMeters: Float,
        location: Location) {

        val point = LocationPoint(
            sessionId = currentSessionId,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            speed = lpfSpeed,
            avgSpeed = avgSpeed,
            totalDistanceMeters = totalDistanceMeters,
            movingTimeMillis = totalMovingTimeMillis,
            timestamp = timestamp
        )

        serviceScope.launch(Dispatchers.IO) {
            database.locationDao().insert(point)
            
            val session = currentSession
            if (session != null) {
                val updatedSession = session.copy(
                    avgSpeed = avgSpeed,
                    maxSpeed = maxSpeed,
                    totalDistance = totalDistanceMeters,
                    totalMovingTime = totalMovingTimeMillis
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
