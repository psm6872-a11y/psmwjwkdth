package com.example.danallacalendar.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.danallacalendar.MainActivity
import com.example.danallacalendar.R

object NotificationHelper {
    private const val CHANNEL_ID = "calendar_shared_channel"
    private const val CHANNEL_NAME = "Calendar Event Updates"
    private const val CHANNEL_DESC = "Notifications for shared calendar updates"
    private const val NOTIFICATION_ID = 1001

    fun showNotification(context: Context, title: String, body: String, dateMillis: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Deep link Intent to open the app at the target date
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("danallacalendar://view?dateMillis=$dateMillis"),
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            dateMillis.toInt(), // unique request code to avoid overriding
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(NOTIFICATION_ID + dateMillis.toInt(), builder.build())
    }
}
