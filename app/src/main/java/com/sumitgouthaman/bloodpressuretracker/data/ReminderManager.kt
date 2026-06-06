package com.sumitgouthaman.bloodpressuretracker.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sumitgouthaman.bloodpressuretracker.ReminderReceiver
import java.util.Calendar

class ReminderManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean("reminder_enabled", false)
    fun getHour(): Int = prefs.getInt("reminder_hour", 8)
    fun getMinute(): Int = prefs.getInt("reminder_minute", 0)

    fun saveReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
        prefs.edit().apply {
            putBoolean("reminder_enabled", enabled)
            putInt("reminder_hour", hour)
            putInt("reminder_minute", minute)
            apply()
        }
        if (enabled) {
            scheduleDailyAlarm(hour, minute)
        } else {
            cancelAlarm()
        }
    }

    fun scheduleDailyAlarm(hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Set calendar to target time
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // If the target time is in the past today, schedule it for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Use setAndAllowWhileIdle for battery-saving compatibility on Android 6.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelAlarm() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.sumitgouthaman.bloodpressuretracker.ACTION_TRIGGER_REMINDER"
        const val ALARM_REQUEST_CODE = 200
    }
}
