package com.sumitgouthaman.bloodpressuretracker.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

sealed interface DebugLogEntry {
    val timestamp: Instant

    data class ModelCheck(
        override val timestamp: Instant = Instant.now(),
        val status: String,
        val baseModelName: String? = null,
        val error: String? = null
    ) : DebugLogEntry

    data class ModelsFound(
        override val timestamp: Instant = Instant.now(),
        val models: List<String>,
        val error: String? = null
    ) : DebugLogEntry

    data class InferenceRequest(
        override val timestamp: Instant = Instant.now(),
        val modelUsed: String,
        val promptText: String,
        val responseReceived: String?,
        val error: String? = null
    ) : DebugLogEntry

    data class GeneralError(
        override val timestamp: Instant = Instant.now(),
        val tag: String,
        val message: String,
        val errorDetails: String? = null
    ) : DebugLogEntry
}

object DebugLogManager {
    private const val MAX_LOGS = 100

    private val _logs = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val logs: StateFlow<List<DebugLogEntry>> = _logs.asStateFlow()

    @Synchronized
    fun logModelCheck(status: String, baseModelName: String? = null, error: String? = null) {
        addLog(DebugLogEntry.ModelCheck(status = status, baseModelName = baseModelName, error = error))
    }

    @Synchronized
    fun logModelsFound(models: List<String>, error: String? = null) {
        addLog(DebugLogEntry.ModelsFound(models = models, error = error))
    }

    @Synchronized
    fun logInferenceRequest(modelUsed: String, promptText: String, responseReceived: String?, error: String? = null) {
        addLog(DebugLogEntry.InferenceRequest(
            modelUsed = modelUsed,
            promptText = promptText,
            responseReceived = responseReceived,
            error = error
        ))
    }

    @Synchronized
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val errorDetails = throwable?.let {
            "${it.localizedMessage}\n${it.stackTraceToString()}"
        }
        addLog(DebugLogEntry.GeneralError(tag = tag, message = message, errorDetails = errorDetails))
    }

    @Synchronized
    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun addLog(entry: DebugLogEntry) {
        val currentList = _logs.value
        val newList = if (currentList.size >= MAX_LOGS) {
            currentList.drop(currentList.size - MAX_LOGS + 1) + entry
        } else {
            currentList + entry
        }
        _logs.value = newList
    }
}
