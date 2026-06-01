package com.example.danallacalendar.backup

import com.example.danallacalendar.data.Event
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class BackupEntry(
    val id: String = "",          // date string, e.g. "2026-06-01"
    val date: String = "",
    val createdAt: Timestamp? = null,
    val eventCount: Int = 0
)

@Singleton
class BackupRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "BackupRepository"
        private const val BACKUP_RETENTION_DAYS = 7
    }

    // ── 백업 저장 ─────────────────────────────────────────────────────────────
    suspend fun saveBackup(
        roomCode: String,
        events: List<Event>,
        customId: String? = null
    ): Result<String> = runCatching {
        if (roomCode.isEmpty()) throw Exception("방 코드가 없습니다.")

        val dateKey = customId ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val eventMaps = events.map { e ->
            hashMapOf(
                "id"              to e.id,
                "title"           to e.title,
                "startMillis"     to e.startMillis,
                "endMillis"       to e.endMillis,
                "isAllDay"        to e.isAllDay,
                "location"        to e.location,
                "notes"           to e.notes,
                "repeatType"      to e.repeatType,
                "reminderMinutes" to e.reminderMinutes,
                "calendarId"      to e.calendarId,
                "syncId"          to e.syncId,
                "isSynced"        to e.isSynced,
                "colorHex"        to e.colorHex,
                "isCompleted"     to e.isCompleted,
                "createdAt"       to e.createdAt,
                "updatedAt"       to e.updatedAt
            )
        }

        val backupData = hashMapOf(
            "date"       to dateKey,
            "createdAt"  to Timestamp.now(),
            "eventCount" to events.size,
            "events"     to eventMaps
        )

        firestore.collection("rooms")
            .document(roomCode)
            .collection("backups")
            .document(dateKey)
            .set(backupData)
            .await()

        dateKey
    }

    // ── 백업 목록 조회 (최근 7일) ─────────────────────────────────────────────
    suspend fun getBackupList(roomCode: String): Result<List<BackupEntry>> = runCatching {
        if (roomCode.isEmpty()) return@runCatching emptyList()

        val snapshot = firestore.collection("rooms")
            .document(roomCode)
            .collection("backups")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(BACKUP_RETENTION_DAYS.toLong())
            .get()
            .await()

        snapshot.documents.mapNotNull { doc ->
            val date       = doc.getString("date") ?: return@mapNotNull null
            val createdAt  = doc.getTimestamp("createdAt")
            val eventCount = doc.getLong("eventCount")?.toInt() ?: 0
            BackupEntry(id = doc.id, date = date, createdAt = createdAt, eventCount = eventCount)
        }
    }

    // ── 백업 복원 (이벤트 목록 반환) ──────────────────────────────────────────
    @Suppress("UNCHECKED_CAST")
    suspend fun restoreBackup(
        roomCode: String,
        backupId: String,
        targetCalendarId: Int
    ): Result<List<Event>> = runCatching {
        if (roomCode.isEmpty()) throw Exception("방 코드가 없습니다.")

        val doc = firestore.collection("rooms")
            .document(roomCode)
            .collection("backups")
            .document(backupId)
            .get()
            .await()

        if (!doc.exists()) throw Exception("백업 데이터를 찾을 수 없습니다.")

        val rawEvents = doc.get("events") as? List<Map<String, Any>> ?: emptyList()
        rawEvents.mapNotNull { map ->
            try {
                Event(
                    title           = map["title"] as? String ?: "",
                    startMillis     = (map["startMillis"] as? Long) ?: 0L,
                    endMillis       = (map["endMillis"] as? Long) ?: 0L,
                    isAllDay        = (map["isAllDay"] as? Boolean) ?: false,
                    location        = map["location"] as? String ?: "",
                    notes           = map["notes"] as? String ?: "",
                    repeatType      = map["repeatType"] as? String ?: "NONE",
                    reminderMinutes = (map["reminderMinutes"] as? Long)?.toInt() ?: -1,
                    calendarId      = (map["calendarId"] as? Long)?.toInt() ?: targetCalendarId,
                    syncId          = map["syncId"] as? String,
                    isSynced        = (map["isSynced"] as? Boolean) ?: false,
                    colorHex        = map["colorHex"] as? String,
                    isCompleted     = (map["isCompleted"] as? Boolean) ?: false,
                    createdAt       = (map["createdAt"] as? Long) ?: System.currentTimeMillis(),
                    updatedAt       = (map["updatedAt"] as? Long) ?: System.currentTimeMillis()
                )
            } catch (e: Exception) {
                android.util.Log.e(TAG, "이벤트 파싱 실패", e)
                null
            }
        }
    }

    // ── 7일 초과 백업 삭제 ────────────────────────────────────────────────────
    suspend fun deleteOldBackups(roomCode: String): Result<Int> = runCatching {
        if (roomCode.isEmpty()) return@runCatching 0

        val snapshot = firestore.collection("rooms")
            .document(roomCode)
            .collection("backups")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        val toDelete = snapshot.documents.drop(BACKUP_RETENTION_DAYS)
        toDelete.forEach { it.reference.delete().await() }
        toDelete.size
    }
}
