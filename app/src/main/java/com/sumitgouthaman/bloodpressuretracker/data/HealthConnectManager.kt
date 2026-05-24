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

open class HealthConnectManager(private val context: Context?) {

    private val healthConnectClient by lazy {
        context?.let { HealthConnectClient.getOrCreate(it) } ?: throw IllegalStateException("Context is null")
    }

    val permissions = setOf(
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getWritePermission(BloodPressureRecord::class)
    )

    open fun isSupported(): Boolean {
        return context?.let { HealthConnectClient.getSdkStatus(it) == HealthConnectClient.SDK_AVAILABLE } ?: false
    }

    open suspend fun hasAllPermissions(): Boolean {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    open suspend fun writeBloodPressure(
        systolic: Double,
        diastolic: Double,
        bodyPosition: Int,
        measurementLocation: Int,
        time: Instant = Instant.now()
    ) {
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

    open suspend fun readRecentBloodPressureRecords(startTime: Instant): List<BloodPressureRecord> {
        val endTime = Instant.now()
        val request = ReadRecordsRequest(
            recordType = BloodPressureRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )
        val response = healthConnectClient.readRecords(request)
        return response.records.sortedByDescending { it.time }
    }

    open suspend fun deleteBloodPressure(recordId: String) {
        healthConnectClient.deleteRecords(
            recordType = BloodPressureRecord::class,
            recordIdsList = listOf(recordId),
            clientRecordIdsList = emptyList()
        )
    }
}
