package com.sumitgouthaman.bloodpressuretracker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.records.BloodPressureRecord
import com.sumitgouthaman.bloodpressuretracker.data.HealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainScreenViewModel(private val healthConnectManager: HealthConnectManager) : ViewModel() {

    private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading)
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    init {
        checkHealthConnectState()
    }

    fun checkHealthConnectState() {
        viewModelScope.launch {
            if (!healthConnectManager.isSupported()) {
                _uiState.value = MainScreenUiState.NotSupported
                return@launch
            }

            if (!healthConnectManager.hasAllPermissions()) {
                _uiState.value = MainScreenUiState.PermissionsRequired
                return@launch
            }

            loadRecords()
        }
    }

    private suspend fun loadRecords() {
        try {
            val records = healthConnectManager.readRecentBloodPressureRecords()
            _uiState.value = MainScreenUiState.Dashboard(records)
        } catch (e: Exception) {
            _uiState.value = MainScreenUiState.Error(e)
        }
    }

    fun saveBloodPressure(systolic: Double, diastolic: Double, bodyPosition: Int, measurementLocation: Int) {
        viewModelScope.launch {
            try {
                healthConnectManager.writeBloodPressure(systolic, diastolic, bodyPosition, measurementLocation)
                loadRecords() // Reload records after saving
            } catch (e: Exception) {
                _uiState.value = MainScreenUiState.Error(e)
            }
        }
    }
}

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    object NotSupported : MainScreenUiState
    object PermissionsRequired : MainScreenUiState
    data class Dashboard(val records: List<BloodPressureRecord>) : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
}
