package com.example.cymeter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cymeter.db.LocationDao
import com.example.cymeter.db.LocationPoint
import com.example.cymeter.db.Session
import com.example.cymeter.db.SessionDao
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_SPEED_THRESHOLD_KMH)

    init {
        loadLastSession()
        viewModelScope.launch(Dispatchers.Main) {
            pathPoints.collect { points ->
                updateCharts(points)
            }
        }
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

            // Vico 2.0 requires unique and strictly increasing X values.
            // Duplicate X values (e.g., when the user is stopped) will cause a crash.
            // We also downsample if there are too many points to improve performance.
            val processedPoints = points
                .distinctBy { round(it.totalDistanceMeters / 1000f * 10000f) / 10000f }
                .sortedBy { it.totalDistanceMeters }
                .let { list ->
                    if (list.size > 500) {
                        val step = list.size / 500.0
                        (0 until 500).map { i ->
                            list[(i * step).toInt().coerceAtMost(list.lastIndex)]
                        }.distinctBy { round(it.totalDistanceMeters / 1000f * 10000f) / 10000f }
                    } else {
                        list
                    }
                }

            val distances = processedPoints.map { round(it.totalDistanceMeters / 1000f * 10000f) / 10000f }
            val speeds = processedPoints.map { it.speed * 3.6f }
            val avgSpeeds = processedPoints.map { it.avgSpeed * 3.6f }
            val altitudes = processedPoints.map { it.altitude.toFloat() }

            speedChartModelProducer.runTransaction {
                lineSeries {
                    series(x = distances, y = speeds)
                    series(x = distances, y = avgSpeeds)
                }
            }

            altitudeChartModelProducer.runTransaction {
                lineSeries {
                    series(x = distances, y = altitudes)
                }
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

    fun selectHistoricalSession(sessionId: Long) {
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
        // Reset the service if it's running
        cruisingService?.resetData()
        // Reset the local state
        _uiState.value = CruisingService.CruisingState()
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
}
