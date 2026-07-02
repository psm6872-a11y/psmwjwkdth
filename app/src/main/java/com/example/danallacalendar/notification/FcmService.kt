package com.example.danallacalendar.notification

import android.util.Log
import com.example.danallacalendar.data.local.UserPreferences
import com.example.danallacalendar.data.repository.CalendarRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject
    lateinit var repository: CalendarRepository

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FcmService", "Refreshed token: $token")
        val roomCode = userPreferences.getLastRoomCode()
        if (roomCode.isNotEmpty()) {
            repository.registerMemberInFirestore(roomCode)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FcmService", "Message received from: ${remoteMessage.from}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            val title = remoteMessage.data["title"] ?: "알림"
            val body = remoteMessage.data["body"] ?: ""
            val dateMillisStr = remoteMessage.data["dateMillis"]
            val dateMillis = dateMillisStr?.toLongOrNull() ?: System.currentTimeMillis()
            val syncId = remoteMessage.data["syncId"]

            NotificationHelper.showNotification(
                context = this,
                title = title,
                body = body,
                dateMillis = dateMillis,
                syncId = syncId
            )
        }
    }
}
