package com.sumitgouthaman.bloodpressuretracker.ui.main

import com.sumitgouthaman.bloodpressuretracker.data.HealthConnectManager
import androidx.health.connect.client.records.BloodPressureRecord
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class MainScreenViewModelTest {
  @Test
  fun uiState_initiallyLoading() = runTest {
    val viewModel = MainScreenViewModel(FakeHealthConnectManager())
    assertEquals(MainScreenUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun uiState_notSupported() = runTest {
    val viewModel = MainScreenViewModel(FakeHealthConnectManager(supported = false))
    // Wait for coroutine to run
    kotlinx.coroutines.delay(100)
    assertEquals(MainScreenUiState.NotSupported, viewModel.uiState.value)
  }

  @Test
  fun uiState_permissionsRequired() = runTest {
    val viewModel = MainScreenViewModel(FakeHealthConnectManager(hasPermissions = false))
    // Wait for coroutine to run
    kotlinx.coroutines.delay(100)
    assertEquals(MainScreenUiState.PermissionsRequired, viewModel.uiState.value)
  }
}

private class FakeHealthConnectManager(
  private val supported: Boolean = true,
  private val hasPermissions: Boolean = true,
  private val records: List<BloodPressureRecord> = emptyList()
) : HealthConnectManager(null) {
  override fun isSupported() = supported
  override suspend fun hasAllPermissions() = hasPermissions
  override suspend fun readRecentBloodPressureRecords(startTime: Instant) = records
  override suspend fun writeBloodPressure(
    systolic: Double,
    diastolic: Double,
    bodyPosition: Int,
    measurementLocation: Int,
    time: Instant
  ) {}
  override suspend fun deleteBloodPressure(recordId: String) {}
}
