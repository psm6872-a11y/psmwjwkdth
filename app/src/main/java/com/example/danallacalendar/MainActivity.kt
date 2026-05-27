package com.example.danallacalendar

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.danallacalendar.theme.DanallaCalendarTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Request notification permission on Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (androidx.core.content.ContextCompat.checkSelfPermission(
          this,
          android.Manifest.permission.POST_NOTIFICATIONS
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
      ) {
        androidx.core.app.ActivityCompat.requestPermissions(
          this,
          arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
          101
        )
      }
    }

    enableEdgeToEdge()
    setContent {
      DanallaCalendarTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }
}
