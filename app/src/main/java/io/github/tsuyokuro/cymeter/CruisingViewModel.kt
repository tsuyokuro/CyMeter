package io.github.tsuyokuro.cymeter

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import io.github.tsuyokuro.cymeter.db.AppDatabase
import io.github.tsuyokuro.cymeter.db.LocationDao
import io.github.tsuyokuro.cymeter.db.LocationPoint
import io.github.tsuyokuro.cymeter.db.Session
import io.github.tsuyokuro.cymeter.db.SessionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.round

class CruisingViewModel(
    private val locationDao: LocationDao,
    private val sessionDao: SessionDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CruisingService.CruisingState())
    val uiState: StateFlow<CruisingService.CruisingState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val pathPoints: StateFlow<List<LocationPoint>> = _uiState
        .map { it.sessionId }
        .flatMapLatest { sessionId ->
            if (sessionId != 0L) {
                locationDao.getPointsFlowBySessionId(sessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val speedChartModelProducer = CartesianChartModelProducer()
    val altitudeChartModelProducer = CartesianChartModelProducer()

    val allSessions: StateFlow<List<Session>> = sessionDao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val speedThresholdKmh: StateFlow<Float> = settingsRepository.speedThresholdFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SettingsRepository.DEFAULT_SPEED_THRESHOLD_KMH
        )

    val distanceLabelIntervalKm: StateFlow<Float> = settingsRepository.distanceLabelIntervalFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SettingsRepository.DEFAULT_DISTANCE_LABEL_INTERVAL_KM
        )

    init {
        loadLastSession()
        viewModelScope.launch(Dispatchers.Main) {
            pathPoints.collect { points ->
                updateCharts(points)
            }
        }
    }

    private val _selectedLocationPoint = MutableStateFlow<LocationPoint?>(null)
    val selectedLocationPoint: StateFlow<LocationPoint?> = _selectedLocationPoint.asStateFlow()
    private var isUserSelected = false

    private fun toChartsDistance(v: Float): Float {
        return round(v / 1000f * 10000f) / 10000f
    }

    fun selectPointByX(x: Double) {
        val points = pathPoints.value
        if (points.isEmpty()) return

        isUserSelected = true
        // Find the point with the closest totalDistanceMeters (converted to km)
        val targetDistanceKm = x.toFloat()
        val closestPoint = points.minByOrNull {
            kotlin.math.abs(toChartsDistance(it.totalDistanceMeters) - targetDistanceKm)
        }
        _selectedLocationPoint.value = closestPoint
    }

    fun clearSelectedPoint() {
        isUserSelected = false
        _selectedLocationPoint.value = null
    }

    private fun updateCharts(points: List<LocationPoint>) {
        viewModelScope.launch(Dispatchers.Default) {
            if (points.isEmpty()) {
                speedChartModelProducer.runTransaction {
                    /* no series */
                }
                altitudeChartModelProducer.runTransaction {
                    /* no series */
                }
                return@launch
            }

            val processedPoints = points
                // totalDistanceMetersが同じアイテムを削除
                .distinctBy { toChartsDistance(it.totalDistanceMeters) }
                .sortedBy { it.totalDistanceMeters }
                .let { list ->
                    if (list.size > 500) {
                        // 500sampleを超える場合は、500に減らす
                        val step = list.size / 500.0
                        (0 until 500).map { i ->
                            list[(i * step).toInt().coerceAtMost(list.lastIndex)]
                        }.distinctBy { toChartsDistance(it.totalDistanceMeters) }
                    } else {
                        list
                    }
                }

            val distances = processedPoints.map { toChartsDistance(it.totalDistanceMeters) }
            val speeds = processedPoints.map { it.speed * 3.6f }
            val avgSpeeds = processedPoints.map { it.avgSpeed * 3.6f }
            val altitudes = processedPoints.map { it.altitude.toFloat() }

            speedChartModelProducer.runTransaction {
                lineModel {
                    series(x = distances, y = speeds)
                    series(x = distances, y = avgSpeeds)
                }
            }

            altitudeChartModelProducer.runTransaction {
                lineModel {
                    series(x = distances, y = altitudes)
                }
            }

            if (!isUserSelected && processedPoints.isNotEmpty()) {
                _selectedLocationPoint.value = processedPoints.last()
            }
        }
    }

    private fun loadLastSession() {
        viewModelScope.launch {
            val lastSession = sessionDao.getLatestSession()
            if (lastSession != null && _uiState.value.sessionId == 0L) {
                _uiState.value = CruisingService.CruisingState(
                    sessionId = lastSession.id,
                    avgCruisingSpeed = lastSession.avgSpeed,
                    maxSpeed = lastSession.maxSpeed,
                    distanceKm = lastSession.totalDistance / 1000f,
                    movingTimeMillis = lastSession.totalMovingTime,
                    currentSpeed = 0f,
                    isMoving = false
                )
            }
        }
    }

    fun updateState(state: CruisingService.CruisingState) {
        // If we are viewing history, we ignore updates from the service
        // unless they are explicitly about starting a new session or something.
        // Actually, let's just ignore if isViewingHistory is true.
        if (!_uiState.value.isViewingHistory) {
            _uiState.value = state
        }
    }

    fun setTracking(isTracking: Boolean) {
        _uiState.value = _uiState.value.copy(isTracking = isTracking)
    }

    fun selectHistoricalSession(sessionId: Long) {
        isUserSelected = false
        _selectedLocationPoint.value = null
        viewModelScope.launch {
            val session = sessionDao.getSessionById(sessionId)
            if (session != null) {
                _uiState.value = CruisingService.CruisingState(
                    sessionId = session.id,
                    avgCruisingSpeed = session.avgSpeed,
                    maxSpeed = session.maxSpeed,
                    distanceKm = session.totalDistance / 1000f,
                    movingTimeMillis = session.totalMovingTime,
                    isViewingHistory = true,
                    isMoving = false,
                    currentSpeed = 0f
                )
            }
        }
    }

    fun exitHistoryMode() {
        _uiState.value = _uiState.value.copy(isViewingHistory = false)
    }

    fun resetData(cruisingService: CruisingService?) {
        isUserSelected = false
        _selectedLocationPoint.value = null
        val currentTracking = _uiState.value.isTracking
        // Reset the service if it's running
        cruisingService?.resetData()
        // Reset the local state
        _uiState.value = CruisingService.CruisingState(isTracking = currentTracking)
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            sessionDao.delete(session)
            // Also delete points associated with this session to keep DB clean
            locationDao.deletePointsBySessionId(session.id)

            // If the deleted session is the current one, reset the UI state
            if (_uiState.value.sessionId == session.id) {
                _uiState.value = CruisingService.CruisingState()
            }
        }
    }

    fun updateSpeedThreshold(thresholdKmh: Float) {
        viewModelScope.launch {
            settingsRepository.updateSpeedThreshold(thresholdKmh)
        }
    }

    fun updateDistanceLabelInterval(intervalKm: Float) {
        viewModelScope.launch {
            settingsRepository.updateDistanceLabelInterval(intervalKm)
        }
    }

    fun exportDatabase(context: Context, contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                db.checkpoint()
                val dbPath = AppDatabase.getDatabasePath(context)
                val dbFile = File(dbPath)

                contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(dbFile).use { input ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importDatabase(
        context: Context,
        contentResolver: ContentResolver,
        uri: Uri,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.closeDatabase()
                val dbPath = AppDatabase.getDatabasePath(context)
                val dbFile = File(dbPath)

                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                viewModelScope.launch(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
