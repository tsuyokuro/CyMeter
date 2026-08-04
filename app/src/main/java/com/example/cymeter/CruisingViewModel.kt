package com.example.cymeter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cymeter.db.LocationDao
import com.example.cymeter.db.LocationPoint
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

class CruisingViewModel(private val locationDao: LocationDao) : ViewModel() {

    private val _uiState = MutableStateFlow(CruisingService.CruisingState())
    val uiState: StateFlow<CruisingService.CruisingState> = _uiState.asStateFlow()

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
        // We might want to filter or process the state here
        // For now, just pass it through.
        _uiState.value = state
    }

    fun resetData(cruisingService: CruisingService?) {
        // Reset the service if it's running
        cruisingService?.resetData()
        // Reset the local state
        _uiState.value = CruisingService.CruisingState()
    }
}
