package com.sumitgouthaman.bloodpressuretracker.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
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
                    onDelete = { id -> viewModel.deleteRecord(id) }
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
    onDelete: (String) -> Unit
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Record Blood Pressure", style = MaterialTheme.typography.headlineMedium)
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
