package com.sumitgouthaman.bloodpressuretracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sumitgouthaman.bloodpressuretracker.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
  private val _shortcutAction = MutableStateFlow<String?>(null)
  val shortcutAction = _shortcutAction.asStateFlow()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleIntent(intent)

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    intent?.getStringExtra("action")?.let { action ->
      _shortcutAction.value = action
      intent.removeExtra("action")
    }
  }

  override fun onResume() {
    super.onResume()
    cancelNotification()
  }

  private fun cancelNotification() {
    try {
      val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
      notificationManager.cancel(ReminderReceiver.NOTIFICATION_ID)
    } catch (e: java.lang.Exception) {
      // Ignored
    }
  }

  fun clearShortcutAction() {
    _shortcutAction.value = null
  }
}
