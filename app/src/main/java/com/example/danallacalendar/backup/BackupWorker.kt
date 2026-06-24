package com.example.danallacalendar.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.danallacalendar.R
import com.example.danallacalendar.data.EventDao
import com.example.danallacalendar.data.local.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val userPreferences: UserPreferences,
    private val eventDao: EventDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "DanallaAutoBackup"
        private const val NOTIF_CHANNEL_ID = "backup_channel"
        private const val NOTIF_ID = 9001
    }

    override suspend fun doWork(): Result {
        return try {
            val roomCode = userPreferences.getLastRoomCode()
            if (roomCode.isEmpty()) return Result.success()

            // 로컬 DB 전체 이벤트 조회
            val allEvents = eventDao.getAllEventsList()
            if (allEvents.isEmpty()) {
                showNotification("📦 백업 완료", "백업할 일정이 없습니다.")
                return Result.success()
            }

            // 오늘 날짜 백업이 이미 존재하는지 검사 (공유방 중복 백업 방지)
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val existsResult = backupRepository.checkBackupExists(roomCode, todayStr)
            if (existsResult.getOrDefault(false)) {
                // 이미 존재하면 다른 기기에서 완료한 것이므로 성공 처리하고 종료 (알림 생략)
                return Result.success()
            }

            // Firestore에 저장
            val saveResult = backupRepository.saveBackup(roomCode, allEvents)
            if (saveResult.isFailure) {
                return Result.retry()
            }

            // 7일 초과 삭제
            backupRepository.deleteOldBackups(roomCode)

            // 알림
            showNotification(
                "📦 백업 완료",
                "총 ${allEvents.size}개 일정이 클라우드에 안전하게 백업되었습니다."
            )

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(WORK_NAME, "백업 실패", e)
            Result.retry()
        }
    }

    private fun showNotification(title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "자동 백업",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "일정 자동 백업 알림" }
            manager.createNotificationChannel(channel)
        }

        val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIF_ID, notif)
    }
}
