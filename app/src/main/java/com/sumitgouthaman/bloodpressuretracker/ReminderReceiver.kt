package com.sumitgouthaman.bloodpressuretracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import com.sumitgouthaman.bloodpressuretracker.data.ReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val NOTIFICATION_ID = 1
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("ReminderReceiver", "onReceive triggered with action: $action")
        
        if (action == ReminderManager.ACTION_TRIGGER_REMINDER || action == Intent.ACTION_BOOT_COMPLETED) {
            val reminderManager = ReminderManager(context)
            
            // Re-schedule alarm if we received a boot completed or if it triggered and we need to schedule for tomorrow
            if (reminderManager.isEnabled()) {
                reminderManager.scheduleDailyAlarm(reminderManager.getHour(), reminderManager.getMinute())
            }

            // Only show the notification if the alarm actually triggered (not on boot check)
            if (action == ReminderManager.ACTION_TRIGGER_REMINDER) {
                showDailyNotification(context)
            }
        }
    }

    private fun showDailyNotification(context: Context) {
        val pendingResult = goAsync()
        val coroutineScope = CoroutineScope(Dispatchers.Default)

        coroutineScope.launch {
            try {
                // Check if AICore and models are available
                val generativeModel = Generation.getClient(
                    generationConfig {
                        modelConfig = modelConfig {
                            releaseStage = ModelReleaseStage.STABLE
                            preference = ModelPreference.FULL
                        }
                    }
                )
                val status = generativeModel.checkStatus()
                val isAiAvailable = status == FeatureStatus.AVAILABLE
                Log.d("ReminderReceiver", "AICore status: $status, isAiAvailable: $isAiAvailable")

                // Create the notification channel
                createNotificationChannel(context)

                // 1. PendingIntent to open app manually
                val manualIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("action", "manually")
                }
                val manualPendingIntent = PendingIntent.getActivity(
                    context,
                    101,
                    manualIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )

                // Build notification
                val builder = NotificationCompat.Builder(context, "daily_reminder_channel")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // System default alarm icon
                    .setContentTitle("Daily Reminder")
                    .setContentText("Record Blood Pressure?")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(manualPendingIntent) // Clicking notification body behaves like manually
                    .addAction(
                        android.R.drawable.ic_menu_edit,
                        "Manually",
                        manualPendingIntent
                    )

                if (isAiAvailable) {
                    // 2. PendingIntent to open app with camera
                    val cameraIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("action", "camera")
                    }
                    val cameraPendingIntent = PendingIntent.getActivity(
                        context,
                        102,
                        cameraIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                    )
                    builder.addAction(
                        android.R.drawable.ic_menu_camera,
                        "Using Camera",
                        cameraPendingIntent
                    )
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, builder.build())
            } catch (e: Exception) {
                Log.e("ReminderReceiver", "Error building or showing notification", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Daily Reminder"
            val descriptionText = "Channel for blood pressure daily tracking reminders"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("daily_reminder_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
