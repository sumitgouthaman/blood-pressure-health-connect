package com.sumitgouthaman.bloodpressuretracker.ui.debug

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sumitgouthaman.bloodpressuretracker.data.DebugLogEntry
import com.sumitgouthaman.bloodpressuretracker.data.DebugLogManager
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class LogFilter(val displayName: String) {
    ALL("All"),
    MODEL_CHECKS("Model Checks"),
    MODELS_FOUND("Models Found"),
    INFERENCES("Inferences"),
    ERRORS("Errors")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMenuScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logs by DebugLogManager.logs.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(LogFilter.ALL) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    val formatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault())
    }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            LogFilter.ALL -> logs.reversed()
            LogFilter.MODEL_CHECKS -> logs.filterIsInstance<DebugLogEntry.ModelCheck>().reversed()
            LogFilter.MODELS_FOUND -> logs.filterIsInstance<DebugLogEntry.ModelsFound>().reversed()
            LogFilter.INFERENCES -> logs.filterIsInstance<DebugLogEntry.InferenceRequest>().reversed()
            LogFilter.ERRORS -> logs.filter { entry ->
                when (entry) {
                    is DebugLogEntry.ModelCheck -> entry.error != null
                    is DebugLogEntry.ModelsFound -> entry.error != null
                    is DebugLogEntry.InferenceRequest -> entry.error != null
                    is DebugLogEntry.GeneralError -> true
                }
            }.reversed()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("AI Debug Logs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmation = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear Logs",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Horizontal scroll filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LogFilter.values().forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null
                    )
                }
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No logs matching criteria",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Logs are gathered dynamically during AI checks and scans.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredLogs) { logEntry ->
                        LogCard(entry = logEntry, timestampStr = formatter.format(logEntry.timestamp))
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear Debug Logs?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all temporary debug logs?") },
            confirmButton = {
                Button(
                    onClick = {
                        DebugLogManager.clearLogs()
                        showClearConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LogCard(entry: DebugLogEntry, timestampStr: String) {
    var expanded by remember { mutableStateOf(false) }

    val (cardColor, headerLabel, hasDetails) = remember(entry) {
        when (entry) {
            is DebugLogEntry.ModelCheck -> {
                val hasErr = entry.error != null
                Triple(
                    if (hasErr) Color(0xFFFDE8E8) else Color(0xFFEBF5FF),
                    if (hasErr) "MODEL CHECK (ERROR)" else "MODEL CHECK",
                    hasErr
                )
            }
            is DebugLogEntry.ModelsFound -> {
                val hasErr = entry.error != null
                Triple(
                    if (hasErr) Color(0xFFFDE8E8) else Color(0xFFEDFDF6),
                    if (hasErr) "MODELS FOUND (ERROR)" else "MODELS FOUND",
                    hasErr
                )
            }
            is DebugLogEntry.InferenceRequest -> {
                val hasErr = entry.error != null
                Triple(
                    if (hasErr) Color(0xFFFDE8E8) else Color(0xFFF3E8FF),
                    if (hasErr) "INFERENCE REQUEST (ERROR)" else "INFERENCE REQUEST",
                    true
                )
            }
            is DebugLogEntry.GeneralError -> {
                Triple(
                    Color(0xFFFDE8E8),
                    "SYSTEM ERROR",
                    entry.errorDetails != null
                )
            }
        }
    }

    val tagColor = when (entry) {
        is DebugLogEntry.ModelCheck -> if (entry.error != null) Color(0xFFC81E1E) else Color(0xFF1E429F)
        is DebugLogEntry.ModelsFound -> if (entry.error != null) Color(0xFFC81E1E) else Color(0xFF03543F)
        is DebugLogEntry.InferenceRequest -> if (entry.error != null) Color(0xFFC81E1E) else Color(0xFF6B21A8)
        is DebugLogEntry.GeneralError -> Color(0xFFC81E1E)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasDetails) { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = tagColor,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = headerLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = timestampStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            when (entry) {
                is DebugLogEntry.ModelCheck -> {
                    Text(
                        text = "Status check result: ${entry.status}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    entry.baseModelName?.let {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Base Model: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (entry.error != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Error: ${entry.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC81E1E),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                is DebugLogEntry.ModelsFound -> {
                    if (entry.models.isEmpty()) {
                        Text(
                            text = "No base models registered or found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Discovered models:\n${entry.models.joinToString("\n") { "• $it" }}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (entry.error != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Discovery error: ${entry.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC81E1E),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                is DebugLogEntry.InferenceRequest -> {
                    Text(
                        text = "Model used: ${entry.modelUsed}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Prompt Preview: ${entry.promptText.lines().firstOrNull() ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is DebugLogEntry.GeneralError -> {
                    Text(
                        text = "[${entry.tag}] ${entry.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC81E1E),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (hasDetails) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "Tap to collapse" else "Tap to expand details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        when (entry) {
                            is DebugLogEntry.InferenceRequest -> {
                                Text(
                                    text = "INPUT PROMPT:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = tagColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.promptText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "OUTPUT RESPONSE RECEIVED:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = tagColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.responseReceived ?: "<No response received>",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                )

                                if (entry.error != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "INFERENCE ERROR:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC81E1E)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = entry.error,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFFEAEA), RoundedCornerShape(4.dp))
                                            .padding(8.dp)
                                    )
                                }
                            }
                            is DebugLogEntry.GeneralError -> {
                                entry.errorDetails?.let {
                                    Text(
                                        text = "EXCEPTION STACK TRACE:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC81E1E)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFFEAEA), RoundedCornerShape(4.dp))
                                            .padding(8.dp)
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
