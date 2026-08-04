package com.example.cymeter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cymeter.db.LocationDao
import com.example.cymeter.db.LocationPoint
import com.example.cymeter.db.Session
import com.example.cymeter.db.SessionDao
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

class CruisingViewModel(
    private val locationDao: LocationDao,
    private val sessionDao: SessionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(CruisingService.CruisingState())
    val uiState: StateFlow<CruisingService.CruisingState> = _uiState.asStateFlow()

    val allSessions: StateFlow<List<Session>> = sessionDao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadLastSession()
    }

    private fun loadLastSession() {
        viewModelScope.launch {
            val lastPoint = locationDao.getLatestPoint()
            if (lastPoint != null && _uiState.value.sessionId == 0L) {
                _uiState.value = CruisingService.CruisingState(
                    sessionId = lastPoint.sessionId,
                    avgCruisingSpeed = lastPoint.avgSpeed,
                    distanceKm = lastPoint.totalDistanceMeters / 1000f,
                    movingTimeMillis = lastPoint.movingTimeMillis,
                    currentSpeed = 0f,
                    isMoving = false
                )
            }
        }
    }

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
}
