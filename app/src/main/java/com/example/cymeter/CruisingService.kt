package com.example.cymeter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class CruisingService : Service(), SensorEventListener {

    companion object {
        private const val STOP_THRESHOLD = 0.5f
        private const val STOP_DURATION_MS = 2000L
        private const val LPF_ALPHA = 0.7f
        private const val SPEED_THRESHOLD_MPS = 5.0f / 3.6f // 5.0 km/h

        private const val DISTANCE_TIME_INTERVAL_MS = 10000L
    }

    private val binder = LocalBinder()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager
    private var linearAccelerationSensor: Sensor? = null

    private val _cruisingData = MutableStateFlow<CruisingState>(CruisingState())
    val cruisingData: StateFlow<CruisingState> = _cruisingData.asStateFlow()

    private var lastBelowThresholdTime: Long = 0L
    private var isMovingInternal: Boolean = false
    private var totalSpeedSum: Double = 0.0
    private var speedSamplesCount: Long = 0
    private var lastMovingTimeUpdate: Long = 0L
    private var totalMovingTimeMillis: Long = 0L

    private var lastDistanceLocation: android.location.Location? = null
    private var lastDistanceUpdateTime: Long = 0L
    private var totalDistanceMeters: Float = 0.0f

    private var smoothedX = 0f
    private var smoothedY = 0f
    private var smoothedZ = 0f

    data class CruisingState(
        val isMoving: Boolean = false,
        val currentSpeed: Float = 0f,
        val avgCruisingSpeed: Float = 0f,
        val movingTimeMillis: Long = 0L,
        val accelerationMagnitude: Float = 0f,
        val distanceKm: Float = 0.0f
    )

    inner class LocalBinder : Binder() {
        fun getService(): CruisingService = this@CruisingService
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val speed = location.speed
                    val currentTime = System.currentTimeMillis()

                    if (isMovingInternal) {
                        if (speed > SPEED_THRESHOLD_MPS) {
                            totalSpeedSum += speed
                            speedSamplesCount++
                        }

                        if (lastMovingTimeUpdate > 0) {
                            totalMovingTimeMillis += (currentTime - lastMovingTimeUpdate)
                        }
                        lastMovingTimeUpdate = currentTime
                    } else {
                        lastMovingTimeUpdate = 0L
                    }

                    val avgSpeed = if (speedSamplesCount > 0) (totalSpeedSum / speedSamplesCount).toFloat() else 0f

                    if (currentTime - lastDistanceUpdateTime >= DISTANCE_TIME_INTERVAL_MS) {
                        lastDistanceLocation?.let { lastLoc ->
                            totalDistanceMeters += location.distanceTo(lastLoc)
                        }
                        lastDistanceLocation = location
                        lastDistanceUpdateTime = currentTime
                    }

                    _cruisingData.value = _cruisingData.value.copy(
                        currentSpeed = speed,
                        avgCruisingSpeed = avgSpeed,
                        movingTimeMillis = totalMovingTimeMillis,
                        distanceKm = totalDistanceMeters / 1000f
                    )
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

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("CyMeter Cruising")
            .setContentText("Tracking speed and acceleration")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun startTracking() {
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
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
    }

    fun resetData() {
        totalSpeedSum = 0.0
        speedSamplesCount = 0
        totalMovingTimeMillis = 0
        lastMovingTimeUpdate = if (isMovingInternal) System.currentTimeMillis() else 0L
        totalDistanceMeters = 0.0f
        lastDistanceLocation = null
        lastDistanceUpdateTime = 0L
        _cruisingData.value = CruisingState(
            isMoving = isMovingInternal,
            currentSpeed = _cruisingData.value.currentSpeed,
            avgCruisingSpeed = 0f,
            movingTimeMillis = 0L,
            accelerationMagnitude = _cruisingData.value.accelerationMagnitude,
            distanceKm = 0.0f
        )
    }

    override fun onDestroy() {
        stopTracking()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Apply Low-Pass Filter
            smoothedX = LPF_ALPHA * smoothedX + (1 - LPF_ALPHA) * x
            smoothedY = LPF_ALPHA * smoothedY + (1 - LPF_ALPHA) * y
            smoothedZ = LPF_ALPHA * smoothedZ + (1 - LPF_ALPHA) * z

            val magnitude = sqrt(smoothedX * smoothedX + smoothedY * smoothedY + smoothedZ * smoothedZ)
            val currentTime = System.currentTimeMillis()

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

            _cruisingData.value = _cruisingData.value.copy(
                isMoving = isMovingInternal,
                accelerationMagnitude = magnitude
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
