package com.sumitgouthaman.bloodpressuretracker.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Pressure
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val permissions = setOf(
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getWritePermission(BloodPressureRecord::class)
    )

    fun isSupported(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    suspend fun writeBloodPressure(systolic: Double, diastolic: Double, bodyPosition: Int, measurementLocation: Int) {
        val time = Instant.now()
        val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(time)

        val record = BloodPressureRecord(
            time = time,
            zoneOffset = zoneOffset,
            systolic = Pressure.millimetersOfMercury(systolic),
            diastolic = Pressure.millimetersOfMercury(diastolic),
            bodyPosition = bodyPosition,
            measurementLocation = measurementLocation
        )

        healthConnectClient.insertRecords(listOf(record))
    }

    suspend fun readRecentBloodPressureRecords(): List<BloodPressureRecord> {
        val endTime = Instant.now()
        // Read records from the last 30 days
        val startTime = endTime.minus(30, ChronoUnit.DAYS)

        val request = ReadRecordsRequest(
            recordType = BloodPressureRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )
        val response = healthConnectClient.readRecords(request)
        return response.records.sortedByDescending { it.time }
    }

    suspend fun deleteBloodPressure(recordId: String) {
        healthConnectClient.deleteRecords(
            recordType = BloodPressureRecord::class,
            recordIdsList = listOf(recordId),
            clientRecordIdsList = emptyList()
        )
    }
}
