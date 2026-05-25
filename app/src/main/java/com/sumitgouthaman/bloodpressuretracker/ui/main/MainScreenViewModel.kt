package com.sumitgouthaman.bloodpressuretracker.ui.main

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.records.BloodPressureRecord
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.sumitgouthaman.bloodpressuretracker.data.HealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "BpAiScan"

enum class BpTimeRange(val displayName: String, val days: Long) {
    LAST_30_DAYS("30 Days", 30),
    LAST_3_MONTHS("3 Months", 90),
    LAST_6_MONTHS("6 Months", 180),
    LAST_YEAR("1 Year", 365)
}

sealed interface AiStatus {
    object Unavailable : AiStatus
    object CheckPending : AiStatus
    object Downloadable : AiStatus
    data class Downloading(val bytesDownloaded: Long) : AiStatus
    object Available : AiStatus
}

class MainScreenViewModel(private val healthConnectManager: HealthConnectManager) : ViewModel() {

    private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading)
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(BpTimeRange.LAST_30_DAYS)
    val selectedTimeRange: StateFlow<BpTimeRange> = _selectedTimeRange.asStateFlow()

    private val generativeModel by lazy {
        Generation.getClient(
            generationConfig {
                modelConfig = modelConfig {
                    releaseStage = ModelReleaseStage.STABLE
                    preference = ModelPreference.FULL
                }
            }
        )
    }

    private val _modelName = MutableStateFlow("Gemini Nano")
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    private val _aiStatus = MutableStateFlow<AiStatus>(AiStatus.CheckPending)
    val aiStatus: StateFlow<AiStatus> = _aiStatus.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    init {
        checkHealthConnectState()
        checkAiModelStatus()
    }

    fun setTimeRange(range: BpTimeRange) {
        _selectedTimeRange.value = range
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
            val days = _selectedTimeRange.value.days
            val startTime = Instant.now().minus(days, ChronoUnit.DAYS)
            val records = healthConnectManager.readRecentBloodPressureRecords(startTime)
            _uiState.value = MainScreenUiState.Dashboard(records)
        } catch (e: Exception) {
            _uiState.value = MainScreenUiState.Error(e)
        }
    }

    fun saveBloodPressure(
        systolic: Double,
        diastolic: Double,
        bodyPosition: Int,
        measurementLocation: Int,
        time: Instant = Instant.now()
    ) {
        viewModelScope.launch {
            try {
                healthConnectManager.writeBloodPressure(systolic, diastolic, bodyPosition, measurementLocation, time)
                loadRecords() // Reload records after saving
            } catch (e: Exception) {
                _uiState.value = MainScreenUiState.Error(e)
            }
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch {
            try {
                healthConnectManager.deleteBloodPressure(recordId)
                loadRecords()
            } catch (e: Exception) {
                _uiState.value = MainScreenUiState.Error(e)
            }
        }
    }

    fun deleteRecords(recordIds: List<String>) {
        viewModelScope.launch {
            try {
                healthConnectManager.deleteBloodPressures(recordIds)
                loadRecords()
            } catch (e: Exception) {
                _uiState.value = MainScreenUiState.Error(e)
            }
        }
    }

    private var recheckJob: kotlinx.coroutines.Job? = null

    private fun scheduleStatusRecheck() {
        recheckJob?.cancel()
        recheckJob = viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            Log.d(TAG, "Running scheduled AI model status re-check...")
            checkAiModelStatus()
        }
    }

    fun checkAiModelStatus() {
        Log.d(TAG, "checkAiModelStatus() called")
        viewModelScope.launch {
            try {
                val status = generativeModel.checkStatus()
                Log.d(TAG, "generativeModel.checkStatus() returned: $status")
                try {
                    val baseModelName = generativeModel.getBaseModelName()
                    Log.d(TAG, "Active on-device base model name: $baseModelName")
                    if (!baseModelName.isNullOrEmpty()) {
                        _modelName.value = baseModelName
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Could not retrieve base model name: ${e.localizedMessage}")
                }
                when (status) {
                    FeatureStatus.AVAILABLE -> {
                        Log.d(TAG, "Model is AVAILABLE on device.")
                        _aiStatus.value = AiStatus.Available
                        recheckJob?.cancel()
                    }
                    FeatureStatus.DOWNLOADABLE -> {
                        Log.d(TAG, "Model is DOWNLOADABLE on device. Starting download...")
                        _aiStatus.value = AiStatus.Downloading(0) // Transition to downloading immediately
                        startModelDownload()
                        scheduleStatusRecheck()
                    }
                    FeatureStatus.DOWNLOADING -> {
                        Log.d(TAG, "Model download is currently IN PROGRESS.")
                        startModelDownload()
                        scheduleStatusRecheck()
                    }
                    FeatureStatus.UNAVAILABLE -> {
                        Log.w(TAG, "Model is UNAVAILABLE on this device.")
                        _aiStatus.value = AiStatus.Unavailable
                        recheckJob?.cancel()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking AI model status", e)
                _aiStatus.value = AiStatus.Unavailable
                recheckJob?.cancel()
            }
        }
    }

    private fun startModelDownload() {
        viewModelScope.launch {
            try {
                generativeModel.download().collect { downloadStatus ->
                    when (downloadStatus) {
                        is DownloadStatus.DownloadStarted -> {
                            Log.d(TAG, "Download started.")
                            _aiStatus.value = AiStatus.Downloading(0)
                        }
                        is DownloadStatus.DownloadProgress -> {
                            Log.v(TAG, "Download progress: ${downloadStatus.totalBytesDownloaded} bytes")
                            _aiStatus.value = AiStatus.Downloading(downloadStatus.totalBytesDownloaded)
                        }
                        DownloadStatus.DownloadCompleted -> {
                            Log.i(TAG, "Download completed successfully! Model is now ready.")
                            _aiStatus.value = AiStatus.Available
                            recheckJob?.cancel()
                        }
                        is DownloadStatus.DownloadFailed -> {
                            Log.e(TAG, "Download failed: $downloadStatus")
                            _aiStatus.value = AiStatus.Downloadable // Let them retry or check again later
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during download collection", e)
                _aiStatus.value = AiStatus.Downloadable
            }
        }
    }

    fun scanBloodPressure(bitmap: Bitmap, onResult: (Double, Double) -> Unit) {
        Log.d(TAG, "scanBloodPressure called with bitmap dimensions: ${bitmap.width}x${bitmap.height}")
        viewModelScope.launch {
            _isScanning.value = true
            _scanError.value = null
            try {
                val prompt = "You are a blood pressure readings extractor. Analyze the provided image of a blood pressure monitor screen.\n" +
                        "Identify the systolic and diastolic numbers.\n" +
                        "Format the output as JSON:\n" +
                        "{\"systolic\": <number>, \"diastolic\": <number>}\n" +
                        "If the image does not display a blood pressure monitor or the values cannot be reliably determined, respond exactly with:\n" +
                        "{\"error\": \"Could not read blood pressure monitor\"}\n" +
                        "Do not include any formatting like ```json or markdown. Output only the raw JSON."

                Log.d(TAG, "Sending prompt and image to Gemini Nano...")
                val response = generativeModel.generateContent(
                    generateContentRequest(ImagePart(bitmap), TextPart(prompt)) { }
                )

                val text = response.candidates.firstOrNull()?.text ?: ""
                Log.d(TAG, "Gemini Nano responded with text: \"$text\"")
                
                // Parse values using regex for maximum robustness
                val systolicRegex = "\"systolic\"\\s*:\\s*(\\d+)".toRegex()
                val diastolicRegex = "\"diastolic\"\\s*:\\s*(\\d+)".toRegex()
                val errorRegex = "\"error\"\\s*:\\s*\"([^\"]+)\"".toRegex()

                val sysMatch = systolicRegex.find(text)
                val diaMatch = diastolicRegex.find(text)
                val errMatch = errorRegex.find(text)

                if (sysMatch != null && diaMatch != null) {
                    val sys = sysMatch.groupValues[1].toDoubleOrNull()
                    val dia = diaMatch.groupValues[1].toDoubleOrNull()
                    Log.d(TAG, "Successfully parsed: systolic=$sys, diastolic=$dia")
                    if (sys != null && dia != null) {
                        onResult(sys, dia)
                    } else {
                        Log.w(TAG, "Parsed values are null")
                        _scanError.value = "Failed to parse blood pressure values from image."
                    }
                } else if (errMatch != null) {
                    val errMsg = errMatch.groupValues[1]
                    Log.w(TAG, "Model returned error in JSON: $errMsg")
                    _scanError.value = errMsg
                } else {
                    Log.w(TAG, "No match found for systolic/diastolic or error in response.")
                    _scanError.value = "Could not extract blood pressure values from the image. Please make sure the image is clear and displays a blood pressure monitor."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing model or parsing response", e)
                _scanError.value = "Error scanning image: ${e.localizedMessage}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearScanError() {
        _scanError.value = null
    }
}

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    object NotSupported : MainScreenUiState
    object PermissionsRequired : MainScreenUiState
    data class Dashboard(val records: List<BloodPressureRecord>) : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
}
