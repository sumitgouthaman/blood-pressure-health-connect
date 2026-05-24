package com.sumitgouthaman.bloodpressuretracker.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.sumitgouthaman.bloodpressuretracker.data.HealthConnectManager
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val healthConnectManager = remember { HealthConnectManager(context) }
    val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(healthConnectManager) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
        onResult = { granted ->
            if (granted.containsAll(healthConnectManager.permissions)) {
                viewModel.checkHealthConnectState()
            }
        }
    )

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val uiState = state) {
            is MainScreenUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is MainScreenUiState.NotSupported -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Health Connect is not supported or not installed on this device.", style = MaterialTheme.typography.bodyLarge)
                }
            }
            is MainScreenUiState.PermissionsRequired -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("We need access to your Health Connect data to read and write Blood Pressure records.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(healthConnectManager.permissions) }) {
                        Text("Grant Permissions")
                    }
                }
            }
            is MainScreenUiState.Dashboard -> {
                DashboardScreen(
                    records = uiState.records,
                    onSave = { systolic, diastolic, pos, loc -> viewModel.saveBloodPressure(systolic, diastolic, pos, loc) },
                    onDelete = { id -> viewModel.deleteRecord(id) },
                    viewModel = viewModel
                )
            }
            is MainScreenUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${uiState.throwable.message}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

object BpLabels {
    val bodyPositions = mapOf(
        BloodPressureRecord.BODY_POSITION_UNKNOWN to "Unknown",
        BloodPressureRecord.BODY_POSITION_STANDING_UP to "Standing",
        BloodPressureRecord.BODY_POSITION_SITTING_DOWN to "Sitting",
        BloodPressureRecord.BODY_POSITION_LYING_DOWN to "Lying Down",
        BloodPressureRecord.BODY_POSITION_RECLINING to "Reclining"
    )

    val measurementLocations = mapOf(
        BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN to "Unknown",
        BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_WRIST to "Left Wrist",
        BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_WRIST to "Right Wrist",
        BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM to "Left Arm",
        BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_UPPER_ARM to "Right Arm"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    records: List<BloodPressureRecord>,
    onSave: (Double, Double, Int, Int) -> Unit,
    onDelete: (String) -> Unit,
    viewModel: MainScreenViewModel
) {
    val selectedRange by viewModel.selectedTimeRange.collectAsStateWithLifecycle()
    var showAddBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddBottomSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Record")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header item (Spacer at top + title)
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Blood Pressure",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Summary statistics card
            item {
                SummaryStatsCard(records = records)
            }

            // Time range chips selector
            item {
                TimeRangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = { viewModel.setTimeRange(it) }
                )
            }

            // Trend Graph
            item {
                BloodPressureChart(records = records)
            }

            // Section header: History Logs
            item {
                Text(
                    text = "Recent History (${selectedRange.displayName})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // History records list (swipe-to-delete)
            if (records.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No history records found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                items(records, key = { it.metadata.id }) { record ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                onDelete(record.metadata.id)
                                true
                            } else false
                        }
                    )
                    
                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = Modifier.fillMaxWidth(),
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.error, MaterialTheme.shapes.medium),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    text = "Delete",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 16.dp),
                                    color = MaterialTheme.colorScheme.onError
                                )
                            }
                        }
                    ) {
                        BloodPressureCard(record)
                    }
                }
            }
            
            // Spacer at bottom to avoid FAB blocking the last list items
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showAddBottomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        
        ModalBottomSheet(
            onDismissRequest = { showAddBottomSheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AddRecordFormSheet(
                records = records,
                onSave = { systolic, diastolic, pos, loc ->
                    onSave(systolic, diastolic, pos, loc)
                    showAddBottomSheet = false
                },
                viewModel = viewModel,
                onDismiss = { showAddBottomSheet = false }
            )
        }
    }
}

data class BpClassification(
    val label: String,
    val color: Color,
    val description: String
)

fun getBpClassification(systolic: Double, diastolic: Double): BpClassification {
    return when {
        systolic > 180 || diastolic > 120 -> BpClassification(
            "Crisis",
            Color(0xFFB71C1C), // Deep Red
            "Seek medical attention immediately!"
        )
        systolic >= 140 || diastolic >= 90 -> BpClassification(
            "Stage 2 Hypertension",
            Color(0xFFE53935), // Red
            "Consult your doctor for management."
        )
        (systolic >= 130 && systolic <= 139) || (diastolic >= 80 && diastolic <= 89) -> BpClassification(
            "Stage 1 Hypertension",
            Color(0xFFFB8C00), // Orange
            "Monitor regularly and review lifestyle choices."
        )
        (systolic >= 120 && systolic < 130) && diastolic < 80 -> BpClassification(
            "Elevated",
            Color(0xFFFDD835), // Yellow
            "Adopt healthy habits to manage levels."
        )
        else -> BpClassification(
            "Normal",
            Color(0xFF43A047), // Green
            "Great! Your blood pressure is within range."
        )
    }
}

@Composable
fun SummaryStatsCard(records: List<BloodPressureRecord>, modifier: Modifier = Modifier) {
    if (records.isEmpty()) return
    
    val avgSystolic = remember(records) { records.map { it.systolic.inMillimetersOfMercury }.average() }
    val avgDiastolic = remember(records) { records.map { it.diastolic.inMillimetersOfMercury }.average() }
    
    val classification = getBpClassification(avgSystolic, avgDiastolic)
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AVERAGE BP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${avgSystolic.toInt()} / ${avgDiastolic.toInt()}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "mmHg",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f).padding(start = 16.dp)
            ) {
                Surface(
                    color = classification.color,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = classification.label.uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = classification.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangeSelector(
    selectedRange: BpTimeRange,
    onRangeSelected: (BpTimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BpTimeRange.values().forEach { range ->
            val isSelected = selectedRange == range
            FilterChip(
                selected = isSelected,
                onClick = { onRangeSelected(range) },
                label = { Text(range.displayName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = null
            )
        }
    }
}

@Composable
fun BloodPressureChart(
    records: List<BloodPressureRecord>,
    modifier: Modifier = Modifier
) {
    if (records.isEmpty()) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No readings in this period",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap + to log your blood pressure",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
        return
    }

    // Sort chronologically (left to right)
    val sortedRecords = remember(records) { records.sortedBy { it.time } }
    
    val systolicValues = sortedRecords.map { it.systolic.inMillimetersOfMercury }
    val diastolicValues = sortedRecords.map { it.diastolic.inMillimetersOfMercury }
    
    val maxBpVal = maxOf(180.0, systolicValues.maxOrNull() ?: 180.0)
    val minBpVal = minOf(60.0, diastolicValues.minOrNull() ?: 60.0)
    
    // Add margin to top and bottom of chart scale
    val yMax = maxBpVal + 15.0
    val yMin = maxOf(30.0, minBpVal - 15.0)
    val ySpan = yMax - yMin

    val gridColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    
    val gridPaint = remember(gridColor) {
        android.graphics.Paint().apply {
            color = gridColor
            strokeWidth = 1.5f
            style = android.graphics.Paint.Style.STROKE
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }
    }
    
    val textPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            color = labelColor
            textSize = 28f
            textAlign = android.graphics.Paint.Align.RIGHT
            isAntiAlias = true
        }
    }

    val xAxisTextPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            color = labelColor
            textSize = 26f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    // Color definitions
    val sysColor = MaterialTheme.colorScheme.primary
    val diaColor = MaterialTheme.colorScheme.tertiary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(sysColor, MaterialTheme.shapes.small))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Systolic", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(diaColor, MaterialTheme.shapes.small))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Diastolic", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                val paddingRight = 10.dp.toPx()
                val paddingLeft = 50.dp.toPx() // Room for Y-axis labels
                val paddingTop = 10.dp.toPx()
                val paddingBottom = 30.dp.toPx() // Room for X-axis labels
                
                val chartWidth = width - paddingLeft - paddingRight
                val chartHeight = height - paddingTop - paddingBottom
                
                // Draw horizontal grid lines and Y labels
                val gridValues = listOf(60.0, 80.0, 100.0, 120.0, 140.0, 160.0, 180.0)
                gridValues.forEach { valBp ->
                    if (valBp in yMin..yMax) {
                        val y = paddingTop + ((yMax - valBp) / ySpan * chartHeight).toFloat()
                        // Draw grid line
                        drawContext.canvas.nativeCanvas.drawLine(
                            paddingLeft, y, width - paddingRight, y, gridPaint
                        )
                        // Draw Y text
                        drawContext.canvas.nativeCanvas.drawText(
                            "${valBp.toInt()}",
                            paddingLeft - 8.dp.toPx(),
                            y + 10f, // Center vertically
                            textPaint
                        )
                    }
                }
                
                val minTime = sortedRecords.first().time.toEpochMilli()
                val maxTime = sortedRecords.last().time.toEpochMilli()
                val timeSpan = maxTime - minTime
                
                // Coordinates mapping
                val sysPoints = mutableListOf<androidx.compose.ui.geometry.Offset>()
                val diaPoints = mutableListOf<androidx.compose.ui.geometry.Offset>()
                
                sortedRecords.forEachIndexed { index, record ->
                    val x = if (timeSpan > 0) {
                        paddingLeft + ((record.time.toEpochMilli() - minTime).toDouble() / timeSpan * chartWidth).toFloat()
                    } else {
                        paddingLeft + (chartWidth / 2) // Single point in center
                    }
                    
                    val sysY = paddingTop + ((yMax - record.systolic.inMillimetersOfMercury) / ySpan * chartHeight).toFloat()
                    val diaY = paddingTop + ((yMax - record.diastolic.inMillimetersOfMercury) / ySpan * chartHeight).toFloat()
                    
                    sysPoints.add(androidx.compose.ui.geometry.Offset(x, sysY))
                    diaPoints.add(androidx.compose.ui.geometry.Offset(x, diaY))
                }

                // Draw Gradient Fill under curves
                if (sysPoints.size >= 2) {
                    val sysFillPath = Path().apply {
                        moveTo(sysPoints.first().x, sysPoints.first().y)
                        sysPoints.forEach { point ->
                            lineTo(point.x, point.y)
                        }
                        lineTo(sysPoints.last().x, paddingTop + chartHeight)
                        lineTo(sysPoints.first().x, paddingTop + chartHeight)
                        close()
                    }
                    
                    drawPath(
                        path = sysFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(sysColor.copy(alpha = 0.15f), Color.Transparent),
                            startY = paddingTop,
                            endY = paddingTop + chartHeight
                        )
                    )

                    val diaFillPath = Path().apply {
                        moveTo(diaPoints.first().x, diaPoints.first().y)
                        diaPoints.forEach { point ->
                            lineTo(point.x, point.y)
                        }
                        lineTo(diaPoints.last().x, paddingTop + chartHeight)
                        lineTo(diaPoints.first().x, paddingTop + chartHeight)
                        close()
                    }
                    
                    drawPath(
                        path = diaFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(diaColor.copy(alpha = 0.15f), Color.Transparent),
                            startY = paddingTop,
                            endY = paddingTop + chartHeight
                        )
                    )
                }

                // Draw lines connecting points
                if (sysPoints.size >= 2) {
                    val sysPath = Path().apply {
                        moveTo(sysPoints.first().x, sysPoints.first().y)
                        for (i in 1 until sysPoints.size) {
                            lineTo(sysPoints[i].x, sysPoints[i].y)
                        }
                    }
                    drawPath(
                        path = sysPath,
                        color = sysColor,
                        style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
                    )

                    val diaPath = Path().apply {
                        moveTo(diaPoints.first().x, diaPoints.first().y)
                        for (i in 1 until diaPoints.size) {
                            lineTo(diaPoints[i].x, diaPoints[i].y)
                        }
                    }
                    drawPath(
                        path = diaPath,
                        color = diaColor,
                        style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
                    )
                }

                // Draw dots on data points
                sysPoints.forEach { point ->
                    drawCircle(
                        color = sysColor.copy(alpha = 0.3f),
                        radius = 6.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = sysColor,
                        radius = 3.dp.toPx(),
                        center = point
                    )
                }

                diaPoints.forEach { point ->
                    drawCircle(
                        color = diaColor.copy(alpha = 0.3f),
                        radius = 6.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = diaColor,
                        radius = 3.dp.toPx(),
                        center = point
                    )
                }

                // Draw X Axis labels (Dates)
                if (sortedRecords.size >= 2) {
                    // Let's draw 3 labels: Start date, Middle date, End date to prevent cluttering
                    val labelsIndices = listOf(0, sortedRecords.size / 2, sortedRecords.size - 1).distinct()
                    val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).withZone(ZoneId.systemDefault())
                    
                    labelsIndices.forEach { idx ->
                        val record = sortedRecords[idx]
                        val pt = sysPoints[idx]
                        val dateStr = formatter.format(record.time)
                        
                        drawContext.canvas.nativeCanvas.drawText(
                            dateStr,
                            pt.x,
                            paddingTop + chartHeight + 20.dp.toPx(),
                            xAxisTextPaint
                        )
                    }
                } else if (sortedRecords.size == 1) {
                    val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).withZone(ZoneId.systemDefault())
                    val dateStr = formatter.format(sortedRecords.first().time)
                    drawContext.canvas.nativeCanvas.drawText(
                        dateStr,
                        paddingLeft + (chartWidth / 2),
                        paddingTop + chartHeight + 20.dp.toPx(),
                        xAxisTextPaint
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordFormSheet(
    records: List<BloodPressureRecord>,
    onSave: (Double, Double, Int, Int) -> Unit,
    viewModel: MainScreenViewModel,
    onDismiss: () -> Unit
) {
    var systolic by remember { mutableStateOf("") }
    var diastolic by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val initialRecord = records.firstOrNull()
    var bodyPosition by remember { mutableStateOf(initialRecord?.bodyPosition ?: BloodPressureRecord.BODY_POSITION_SITTING_DOWN) }
    var bodyPositionExpanded by remember { mutableStateOf(false) }
    var measurementLocation by remember { mutableStateOf(initialRecord?.measurementLocation ?: BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_WRIST) }
    var measurementLocationExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val modelName by viewModel.modelName.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                photoUri?.let { uri ->
                    try {
                        val bitmap = loadDownscaledBitmap(context, uri)
                        viewModel.scanBloodPressure(bitmap) { sys, dia ->
                            systolic = sys.toInt().toString()
                            diastolic = dia.toInt().toString()
                            errorMessage = null
                        }
                    } catch (e: Exception) {
                        // Scan error is handled by viewmodel
                    } finally {
                        try {
                            photoFile?.delete()
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }
                }
            } else {
                try {
                    photoFile?.delete()
                } catch (e: Exception) {
                    // Ignored
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Record Blood Pressure", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (val currentStatus = aiStatus) {
                    is AiStatus.Available -> {
                        IconButton(
                            onClick = {
                                viewModel.clearScanError()
                                try {
                                    val file = File(context.cacheDir, "bp_scan_temp.jpg")
                                    photoFile = file
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    photoUri = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    // Handled
                                }
                            },
                            enabled = !isScanning
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Scan with Camera",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    is AiStatus.Downloading -> {
                        val megabytes = currentStatus.bytesDownloaded / 1024 / 1024
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = "AI Model: ${megabytes}MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    is AiStatus.Downloadable -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Preparing AI...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    else -> {
                        // CheckPending or Unavailable - do not show anything
                    }
                }
                
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        }

        if (isScanning) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$modelName is reading your blood pressure monitor...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (scanError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = scanError!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.clearScanError() }) {
                        Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = systolic,
                onValueChange = { 
                    if (it.all { char -> char.isDigit() }) {
                        systolic = it
                        errorMessage = null
                    }
                },
                label = { Text("Systolic") },
                singleLine = true,
                isError = errorMessage != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }
                ),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = diastolic,
                onValueChange = { 
                    if (it.all { char -> char.isDigit() }) {
                        diastolic = it
                        errorMessage = null
                    }
                },
                label = { Text("Diastolic") },
                singleLine = true,
                isError = errorMessage != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                modifier = Modifier.weight(1f)
            )
        }
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ExposedDropdownMenuBox(
                expanded = bodyPositionExpanded,
                onExpandedChange = { bodyPositionExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = BpLabels.bodyPositions[bodyPosition] ?: "Unknown",
                    onValueChange = { },
                    label = { Text("Posture") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bodyPositionExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = bodyPositionExpanded,
                    onDismissRequest = { bodyPositionExpanded = false }
                ) {
                    BpLabels.bodyPositions.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                bodyPosition = key
                                bodyPositionExpanded = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = measurementLocationExpanded,
                onExpandedChange = { measurementLocationExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = BpLabels.measurementLocations[measurementLocation] ?: "Unknown",
                    onValueChange = { },
                    label = { Text("Location") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = measurementLocationExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = measurementLocationExpanded,
                    onDismissRequest = { measurementLocationExpanded = false }
                ) {
                    BpLabels.measurementLocations.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                measurementLocation = key
                                measurementLocationExpanded = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                val sys = systolic.toDoubleOrNull()
                val dia = diastolic.toDoubleOrNull()
                if (sys == null || dia == null) {
                    errorMessage = "Please enter both values."
                } else if (sys <= dia) {
                    errorMessage = "Systolic must be greater than Diastolic."
                } else if (sys < 50 || sys > 300 || dia < 30 || dia > 200) {
                    errorMessage = "Please enter realistic values."
                } else {
                    errorMessage = null
                    onSave(sys, dia, bodyPosition, measurementLocation)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Record")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun BloodPressureCard(record: BloodPressureRecord) {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    val timeStr = formatter.format(record.time)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "${record.systolic.inMillimetersOfMercury.toInt()} / ${record.diastolic.inMillimetersOfMercury.toInt()} mmHg",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${BpLabels.measurementLocations[record.measurementLocation]} • ${BpLabels.bodyPositions[record.bodyPosition]}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timeStr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun loadDownscaledBitmap(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap {
    val inputStream = context.contentResolver.openInputStream(uri)
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeStream(inputStream, null, options)
    inputStream?.close()

    var scale = 1
    while (options.outWidth / scale / 2 >= maxDimension && options.outHeight / scale / 2 >= maxDimension) {
        scale *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = scale
    }
    val finalInputStream = context.contentResolver.openInputStream(uri)
    var bitmap = BitmapFactory.decodeStream(finalInputStream, null, decodeOptions)
    finalInputStream?.close()

    if (bitmap == null) {
        throw Exception("Failed to decode bitmap")
    }

    // Correct orientation from EXIF metadata
    try {
        context.contentResolver.openInputStream(uri)?.use { exifInputStream ->
            val exif = ExifInterface(exifInputStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            var needsRotation = true
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> needsRotation = false
            }
            if (needsRotation) {
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = rotatedBitmap
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("BpAiScan", "Failed to apply EXIF rotation", e)
    }

    return bitmap
}
