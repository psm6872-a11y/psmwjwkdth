package com.example.danallacalendar.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.danallacalendar.MainActivity
import com.example.danallacalendar.R
import com.example.danallacalendar.data.CalendarDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = CalendarDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
                    val events = db.eventDao().getAllEventsList()
                    val now = System.currentTimeMillis()
                    for (event in events) {
                        if (event.reminderMinutes >= 0) {
                            val triggerTime = event.startMillis - (event.reminderMinutes * 60 * 1000L)
                            if (triggerTime > now) {
                                EventReminderHelper.scheduleAlarm(context, event)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val eventId = intent.getIntExtra("eventId", -1)
        val title = intent.getStringExtra("title") ?: "일정 알림"
        val notes = intent.getStringExtra("notes") ?: ""
        val startMillis = intent.getLongExtra("startMillis", 0L)

        if (eventId != -1) {
            showReminderNotification(context, eventId, title, notes, startMillis)
        }
    }

    private fun showReminderNotification(
        context: Context,
        eventId: Int,
        title: String,
        notes: String,
        startMillis: Long
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "event_reminder_channel_danalla_v2"
        val channelName = "일정 알림"
        val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.danalla}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .build()
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "일정 알람 및 리마인더 알림"
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("danallacalendar://view?dateMillis=$startMillis"),
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(notes.ifEmpty { "일정 시간이 되었습니다." })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)

        notificationManager.notify(2000 + eventId, builder.build())
    }
}
