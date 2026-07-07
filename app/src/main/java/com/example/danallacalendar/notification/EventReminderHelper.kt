package com.example.danallacalendar.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.danallacalendar.data.Event

object EventReminderHelper {
    fun scheduleAlarm(context: Context, event: Event) {
        if (event.reminderMinutes < 0) {
            cancelAlarm(context, event.id)
            return
        }

        val triggerTime = event.startMillis - (event.reminderMinutes * 60 * 1000L)
        if (triggerTime <= System.currentTimeMillis()) {
            // Already passed, cancel alarm just in case
            cancelAlarm(context, event.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            putExtra("eventId", event.id)
            putExtra("title", event.title)
            putExtra("notes", event.notes)
            putExtra("startMillis", event.startMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // API 31+ exact alarm permission check
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelAlarm(context: Context, eventId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, EventReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
