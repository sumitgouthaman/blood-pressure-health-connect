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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
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
    var systolic by remember { mutableStateOf("") }
    var diastolic by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val initialRecord = records.firstOrNull()
    var bodyPosition by remember { mutableStateOf(initialRecord?.bodyPosition ?: BloodPressureRecord.BODY_POSITION_SITTING_DOWN) }
    var bodyPositionExpanded by remember { mutableStateOf(false) }
    var measurementLocation by remember { mutableStateOf(initialRecord?.measurementLocation ?: BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_WRIST) }
    var measurementLocationExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Record Blood Pressure", style = MaterialTheme.typography.headlineMedium)
            
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
        Spacer(modifier = Modifier.height(16.dp))
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
                    systolic = ""
                    diastolic = ""
                    focusManager.clearFocus()
                }
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Save Record")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("Recent History", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
