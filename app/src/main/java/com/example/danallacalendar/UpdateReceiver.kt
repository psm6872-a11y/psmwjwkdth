package com.example.danallacalendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("UpdateReceiver", "App updated! ACTION_MY_PACKAGE_REPLACED received.")

            // 1. Try to start MainActivity directly (works on some devices/older Android versions)
            try {
                val startIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(startIntent)
                Log.d("UpdateReceiver", "Direct activity start succeeded.")
            } catch (e: Exception) {
                Log.e("UpdateReceiver", "Failed to start activity directly: ${e.localizedMessage}")
            }

            // 2. Post a notification as a fallback so the user can easily launch the app if direct launch was blocked
            try {
                showUpdateNotification(context)
            } catch (e: Exception) {
                Log.e("UpdateReceiver", "Failed to show notification: ${e.localizedMessage}")
            }
        }
    }

    private fun showUpdateNotification(context: Context) {
        val channelId = "app_update_channel"
        val channelName = "앱 업데이트 알림"
        val notificationId = 1001

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "앱이 업데이트되었을 때 알림을 보냅니다."
            }
            manager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // built-in Android icon
            .setContentTitle("다날라 캘린더 업데이트 완료")
            .setContentText("최신 버전으로 업데이트되었습니다. 터치하여 앱을 실행하세요!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(notificationId, notification)
        Log.d("UpdateReceiver", "Notification posted successfully.")
    }
}
