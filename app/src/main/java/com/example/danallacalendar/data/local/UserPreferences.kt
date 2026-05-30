package com.example.danallacalendar.data.local

import android.content.Context
import java.util.UUID

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEVICE_UUID = "device_uuid"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_LAST_ROOM_CODE = "last_room_code"
    }

    init {
        // Generate and save device UUID if it doesn't exist
        if (prefs.getString(KEY_DEVICE_UUID, null) == null) {
            val uuid = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_UUID, uuid).apply()
        }
    }

    fun getDeviceUUID(): String {
        return prefs.getString(KEY_DEVICE_UUID, "") ?: ""
    }

    fun getNickname(): String {
        return prefs.getString(KEY_NICKNAME, "") ?: ""
    }

    fun setNickname(nickname: String) {
        prefs.edit().putString(KEY_NICKNAME, nickname).apply()
    }

    fun getLastRoomCode(): String {
        return prefs.getString(KEY_LAST_ROOM_CODE, "") ?: ""
    }

    fun setLastRoomCode(roomCode: String) {
        prefs.edit().putString(KEY_LAST_ROOM_CODE, roomCode).apply()
    }

    fun clearAll() {
        prefs.edit().remove(KEY_NICKNAME).remove(KEY_LAST_ROOM_CODE).apply()
    }
}
