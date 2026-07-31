package com.example.cymeter

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CruisingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CruisingService.CruisingState())
    val uiState: StateFlow<CruisingService.CruisingState> = _uiState.asStateFlow()

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
