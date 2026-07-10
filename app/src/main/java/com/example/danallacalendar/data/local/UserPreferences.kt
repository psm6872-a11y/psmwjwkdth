package com.example.danallacalendar.data.local

import android.content.Context
import android.provider.Settings
import java.util.UUID

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEVICE_UUID = "device_uuid"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_LAST_ROOM_CODE = "last_room_code"
        private const val KEY_IS_SHARE_ENABLED = "is_share_enabled"
        private const val KEY_IS_GOOGLE_DRIVE_SAVE_ENABLED = "is_google_drive_save_enabled"
        private const val KEY_IS_AUTO_DRIVE_SYNC_ENABLED = "is_auto_drive_sync_enabled"
        private const val KEY_SMS_BODY_TEMPLATE = "smsTemplate"
        private const val KEY_IS_BACKUP_SCHEDULED_AT_6AM = "is_backup_scheduled_at_6am"
    }

    init {
        // Generate and save device UUID if it doesn't exist
        if (prefs.getString(KEY_DEVICE_UUID, null) == null) {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            val uuid = if (!androidId.isNullOrEmpty()) {
                androidId
            } else {
                UUID.randomUUID().toString()
            }
            prefs.edit().putString(KEY_DEVICE_UUID, uuid).apply()
        }
    }

    fun getDeviceUUID(): String {
        return prefs.getString(KEY_DEVICE_UUID, "") ?: ""
    }

    fun setDeviceUUID(uuid: String) {
        prefs.edit().putString(KEY_DEVICE_UUID, uuid).apply()
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

    fun isShareEnabled(): Boolean {
        return prefs.getBoolean(KEY_IS_SHARE_ENABLED, true)
    }

    fun setShareEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_SHARE_ENABLED, enabled).apply()
    }

    fun isGoogleDriveSaveEnabled(): Boolean {
        return prefs.getBoolean(KEY_IS_GOOGLE_DRIVE_SAVE_ENABLED, false)
    }

    fun setGoogleDriveSaveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_GOOGLE_DRIVE_SAVE_ENABLED, enabled).apply()
    }

    fun isAutoDriveSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_IS_AUTO_DRIVE_SYNC_ENABLED, false)
    }

    fun setAutoDriveSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_AUTO_DRIVE_SYNC_ENABLED, enabled).apply()
    }

    fun getSmsBodyTemplate(): String {
        return prefs.getString(KEY_SMS_BODY_TEMPLATE, "위와 같이 견적 합니다. 검토해 보시고 연락주세요. 감사합니다.") ?: "위와 같이 견적 합니다. 검토해 보시고 연락주세요. 감사합니다."
    }

    fun setSmsBodyTemplate(template: String) {
        prefs.edit().putString(KEY_SMS_BODY_TEMPLATE, template).apply()
    }

    fun isBackupScheduledAt6AM(): Boolean {
        return prefs.getBoolean(KEY_IS_BACKUP_SCHEDULED_AT_6AM, false)
    }

    fun setBackupScheduledAt6AM(scheduled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_BACKUP_SCHEDULED_AT_6AM, scheduled).apply()
    }

    fun clearAll() {
        prefs.edit().remove(KEY_NICKNAME).remove(KEY_LAST_ROOM_CODE).apply()
    }

    fun getDismissedContractSyncIds(): Set<String> {
        return prefs.getStringSet("dismissed_contract_sync_ids", null) ?: emptySet()
    }

    fun addDismissedContractSyncId(syncId: String) {
        val current = getDismissedContractSyncIds().toMutableSet()
        current.add(syncId)
        prefs.edit().putStringSet("dismissed_contract_sync_ids", current).apply()
    }
}
