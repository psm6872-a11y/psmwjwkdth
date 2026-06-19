package com.example.danallacalendar.data.repository

import com.example.danallacalendar.data.CalendarCategory
import com.example.danallacalendar.data.Event
import com.example.danallacalendar.data.DeadlineDate
import com.example.danallacalendar.data.EventDao
import com.example.danallacalendar.data.local.UserPreferences
import com.example.danallacalendar.data.model.Room
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject

class CalendarRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userPreferences: UserPreferences,
    val eventDao: EventDao
) {
    // Suspending wrapper for createRoom with offline fallback
    suspend fun createRoomSuspended(): String {
        return try {
            kotlinx.coroutines.withTimeout(3000) {
                suspendCancellableCoroutine<String> { continuation ->
                    createRoom(
                        onSuccess = { code ->
                            if (continuation.isActive) continuation.resume(code)
                        },
                        onFailure = { exception ->
                            if (continuation.isActive) continuation.resumeWithException(exception)
                        }
                    )
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val code = userPreferences.getLastRoomCode()
            if (code.isNotEmpty()) code else throw Exception("방 생성 시간 초과")
        }
    }

    // Suspending wrapper for joinRoom with timeout
    suspend fun joinRoomSuspended(roomCode: String): Unit {
        try {
            kotlinx.coroutines.withTimeout(3000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    joinRoom(
                        roomCode = roomCode,
                        onSuccess = {
                            if (continuation.isActive) continuation.resume(Unit)
                        },
                        onFailure = { errorMessage ->
                            if (continuation.isActive) continuation.resumeWithException(Exception(errorMessage))
                        }
                    )
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw Exception("서버 연결 시간 초과. 네트워크 상태를 확인해 주세요.")
        }
    }

    // Generate Room Code: "###-###"
    fun generateRoomCode(): String {
        val code = (100000..999999).random().toString()
        return "${code.substring(0, 3)}-${code.substring(3)}"
    }

    // Create a new room
    fun createRoom(
        onSuccess: (roomCode: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val roomCode = generateRoomCode()
        userPreferences.setLastRoomCode(roomCode) // Save locally first for offline support!
        
        val roomData = Room(
            createdAt = Timestamp.now(),
            createdBy = userPreferences.getDeviceUUID()
        )

        firestore.collection("rooms")
            .document(roomCode)
            .collection("info")
            .document("details")
            .set(roomData)
            .addOnSuccessListener {
                registerMemberInFirestore(roomCode)
                onSuccess(roomCode)
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    // Join an existing room
    fun joinRoom(
        roomCode: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        firestore.collection("rooms")
            .document(roomCode)
            .collection("info")
            .document("details")
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userPreferences.setLastRoomCode(roomCode)
                    registerMemberInFirestore(roomCode)
                    onSuccess()
                } else {
                    onFailure("존재하지 않는 방 코드입니다.")
                }
            }
            .addOnFailureListener { e ->
                onFailure(e.localizedMessage ?: "방 확인 실패")
            }
    }

    fun registerMemberInFirestore(roomCode: String) {
        val currentNickname = userPreferences.getNickname()
        if (roomCode.isEmpty() || currentNickname.isEmpty()) return

        firestore.collection("rooms")
            .document(roomCode)
            .collection("members")
            .whereEqualTo("nickname", currentNickname)
            .get()
            .addOnSuccessListener { querySnapshot ->
                var targetDeviceUUID = userPreferences.getDeviceUUID()
                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    val existingDoc = querySnapshot.documents[0]
                    val existingUUID = existingDoc.id
                    if (existingUUID != targetDeviceUUID) {
                        userPreferences.setDeviceUUID(existingUUID)
                        targetDeviceUUID = existingUUID
                        android.util.Log.d("CalendarRepository", "Restored existing deviceUUID ($existingUUID) for nickname: $currentNickname")
                    }
                }
                
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        val fcmToken = if (task.isSuccessful) task.result else ""
                        val memberData = hashMapOf(
                            "nickname" to currentNickname,
                            "fcmToken" to fcmToken,
                            "updatedAt" to Timestamp.now()
                        )
                        val docRef = firestore.collection("rooms")
                            .document(roomCode)
                            .collection("members")
                            .document(targetDeviceUUID)
                            
                        docRef.get().addOnSuccessListener { memberDoc ->
                            if (!memberDoc.exists() || memberDoc.get("joinedAt") == null) {
                                memberData["joinedAt"] = Timestamp.now()
                            }
                            docRef.set(memberData, com.google.firebase.firestore.SetOptions.merge())
                        }.addOnFailureListener {
                            memberData["joinedAt"] = Timestamp.now()
                            docRef.set(memberData, com.google.firebase.firestore.SetOptions.merge())
                        }
                    }
            }
            .addOnFailureListener {
                val targetDeviceUUID = userPreferences.getDeviceUUID()
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        val fcmToken = if (task.isSuccessful) task.result else ""
                        val memberData = hashMapOf(
                            "nickname" to currentNickname,
                            "fcmToken" to fcmToken,
                            "updatedAt" to Timestamp.now(),
                            "joinedAt" to Timestamp.now()
                        )
                        firestore.collection("rooms")
                            .document(roomCode)
                            .collection("members")
                            .document(targetDeviceUUID)
                            .set(memberData, com.google.firebase.firestore.SetOptions.merge())
                    }
            }
    }

    // Room Database Wrapper functions
    fun getAllCategories(): Flow<List<CalendarCategory>> = eventDao.getAllCategories()
    
    suspend fun insertCategory(category: CalendarCategory) = eventDao.insertCategory(category)
    
    suspend fun updateCategory(category: CalendarCategory) = eventDao.updateCategory(category)
    
    fun getEventsInRange(start: Long, end: Long): Flow<List<Event>> = eventDao.getEventsInRange(start, end)
    
    fun searchEvents(query: String): Flow<List<Event>> = eventDao.searchEvents("%$query%")
    
    suspend fun getEventById(id: Int): Event? = eventDao.getEventById(id)
    
    suspend fun insertEvent(event: Event) {
        val id = eventDao.insertEvent(event)
        if (event.isSynced) {
            uploadEventToFirestore(event.copy(id = id.toInt()))
        }
    }
    
    suspend fun updateEvent(event: Event) {
        eventDao.updateEvent(event)
        if (event.isSynced) {
            uploadEventToFirestore(event)
        }
    }
    
    suspend fun deleteEvent(event: Event) {
        eventDao.deleteEvent(event)
        if (event.isSynced && event.syncId != null) {
            deleteEventFromFirestore(event.syncId)
        }
    }

    // DeadlineDates
    fun getAllDeadlineDates(): Flow<List<DeadlineDate>> = eventDao.getAllDeadlineDates()

    suspend fun insertDeadlineDate(deadlineDate: DeadlineDate) {
        eventDao.insertDeadlineDate(deadlineDate)
        uploadDeadlineDateToFirestore(deadlineDate)
    }

    suspend fun deleteDeadlineDate(dateMillis: Long) {
        eventDao.deleteDeadlineDate(dateMillis)
        deleteDeadlineDateFromFirestore(dateMillis)
    }

    private fun uploadDeadlineDateToFirestore(deadlineDate: DeadlineDate) {
        val roomCode = userPreferences.getLastRoomCode()
        if (roomCode.isEmpty()) return

        val docData = hashMapOf(
            "dateMillis" to deadlineDate.dateMillis,
            "createdBy" to userPreferences.getDeviceUUID()
        )

        firestore.collection("rooms")
            .document(roomCode)
            .collection("deadline_dates")
            .document(deadlineDate.dateMillis.toString())
            .set(docData, com.google.firebase.firestore.SetOptions.merge())
    }

    private fun deleteDeadlineDateFromFirestore(dateMillis: Long) {
        val roomCode = userPreferences.getLastRoomCode()
        if (roomCode.isEmpty()) return

        val docData = hashMapOf(
            "dateMillis" to dateMillis,
            "status" to "DELETED",
            "deletedBy" to userPreferences.getDeviceUUID()
        )

        firestore.collection("rooms")
            .document(roomCode)
            .collection("deadline_dates")
            .document(dateMillis.toString())
            .set(docData, com.google.firebase.firestore.SetOptions.merge())
    }

    suspend fun syncDeadlineDatesFromFirestore() {
        val roomCode = userPreferences.getLastRoomCode()
        if (roomCode.isEmpty()) return

        return suspendCancellableCoroutine<Unit> { continuation ->
            firestore.collection("rooms")
                .document(roomCode)
                .collection("deadline_dates")
                .get()
                .addOnSuccessListener { snapshot ->
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val remoteDeadlineDates = snapshot.documents.mapNotNull { doc ->
                                val status = doc.getString("status") ?: ""
                                if (status == "DELETED") return@mapNotNull null
                                val dateMillis = doc.getLong("dateMillis") ?: return@mapNotNull null
                                DeadlineDate(dateMillis)
                            }

                            // Remote dates를 local DB에 동기화
                            remoteDeadlineDates.forEach { remote ->
                                eventDao.insertDeadlineDate(remote)
                            }

                            // Local에만 있는 dates 삭제 (remote에 없는 경우)
                            val localDates = eventDao.getAllDeadlineDatesList()
                            val remoteDateMillis = remoteDeadlineDates.map { it.dateMillis }.toSet()
                            localDates.forEach { local ->
                                if (local.dateMillis !in remoteDateMillis) {
                                    eventDao.deleteDeadlineDate(local.dateMillis)
                                }
                            }

                            if (continuation.isActive) continuation.resume(Unit)
                        } catch (e: Exception) {
                            android.util.Log.e("SyncError", "Failed to sync deadline dates", e)
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
        }
    }

    fun startDeadlineRealtimeSync(roomCode: String) = callbackFlow<Unit> {
        if (roomCode.isEmpty()) {
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("rooms")
            .document(roomCode)
            .collection("deadline_dates")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val remoteDeadlineDates = snapshot.documents.mapNotNull { doc ->
                                val status = doc.getString("status") ?: ""
                                if (status == "DELETED") return@mapNotNull null
                                val dateMillis = doc.getLong("dateMillis") ?: return@mapNotNull null
                                DeadlineDate(dateMillis)
                            }

                            // 1. Remote dates를 local DB에 동기화
                            remoteDeadlineDates.forEach { remote ->
                                eventDao.insertDeadlineDate(remote)
                            }

                            // 2. Local에만 있는 dates 삭제 (remote에 없는 경우)
                            val localDates = eventDao.getAllDeadlineDatesList()
                            val remoteDateMillis = remoteDeadlineDates.map { it.dateMillis }.toSet()
                            localDates.forEach { local ->
                                if (local.dateMillis !in remoteDateMillis) {
                                    eventDao.deleteDeadlineDate(local.dateMillis)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SyncError", "Failed to sync remote deadline dates", e)
                        }
                    }
                    trySend(Unit)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun deleteAllEvents() = eventDao.deleteAllEvents()

    // Firestore Bidirectional Sync
    fun startRealtimeSync(roomCode: String, sharedCategoryId: Int) = callbackFlow<Unit> {
        if (roomCode.isEmpty()) {
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("rooms")
            .document(roomCode)
            .collection("events")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val remoteEvents = snapshot.documents.mapNotNull { doc ->
                                val title = doc.getString("title") ?: ""
                                val startMillis = doc.getLong("startMillis") ?: 0L
                                val endMillis = doc.getLong("endMillis") ?: 0L
                                val isAllDay = doc.getBoolean("isAllDay") ?: false
                                val location = doc.getString("location") ?: ""
                                val notes = doc.getString("notes") ?: ""
                                val repeatType = doc.getString("repeatType") ?: "NONE"
                                val reminderMinutes = doc.getLong("reminderMinutes")?.toInt() ?: -1
                                val syncId = doc.getString("syncId") ?: doc.id
                                val colorHex = doc.getString("colorHex")
                                val isCompleted = doc.getBoolean("isCompleted") ?: false
                                val linkedEstimateId = doc.getString("linkedEstimateId")
                                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                val teamId = doc.getLong("teamId")?.toInt()
                                val slotPosition = doc.getString("slotPosition")
                                
                                Event(
                                    title = title,
                                    startMillis = startMillis,
                                    endMillis = endMillis,
                                    isAllDay = isAllDay,
                                    location = location,
                                    notes = notes,
                                    repeatType = repeatType,
                                    reminderMinutes = reminderMinutes,
                                    calendarId = sharedCategoryId,
                                    syncId = syncId,
                                    isSynced = true,
                                    colorHex = colorHex,
                                    isCompleted = isCompleted,
                                    createdAt = createdAt,
                                    updatedAt = updatedAt,
                                    linkedEstimateId = linkedEstimateId,
                                    teamId = teamId,
                                    slotPosition = slotPosition
                                )
                            }
                            
                            // 1. Update/insert remote events to local DB
                            remoteEvents.forEach { remote ->
                                val existing = eventDao.getEventBySyncId(remote.syncId ?: "")
                                if (existing != null) {
                                    val updated = remote.copy(id = existing.id)
                                    if (existing.title != remote.title ||
                                        existing.startMillis != remote.startMillis ||
                                        existing.endMillis != remote.endMillis ||
                                        existing.isAllDay != remote.isAllDay ||
                                        existing.location != remote.location ||
                                        existing.notes != remote.notes ||
                                        existing.colorHex != remote.colorHex ||
                                        existing.isCompleted != remote.isCompleted ||
                                        existing.createdAt != remote.createdAt ||
                                        existing.updatedAt != remote.updatedAt ||
                                        existing.linkedEstimateId != remote.linkedEstimateId ||
                                        existing.teamId != remote.teamId ||
                                        existing.slotPosition != remote.slotPosition
                                    ) {
                                        eventDao.updateEvent(updated)
                                    }
                                } else {
                                    eventDao.insertEvent(remote)
                                }
                            }
                            
                            // 2. Delete local synced events that are NOT in remote collection
                            val localSynced = eventDao.getSyncedEvents()
                            val remoteSyncIds = remoteEvents.map { it.syncId }.toSet()
                            localSynced.forEach { local ->
                                if (local.syncId !in remoteSyncIds) {
                                    eventDao.deleteEvent(local)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SyncError", "Failed to sync remote events", e)
                        }
                    }
                    trySend(Unit)
                }
            }
        awaitClose { listener.remove() }
    }

    private fun uploadEventToFirestore(event: Event) {
        val roomCode = userPreferences.getLastRoomCode()
        if (roomCode.isEmpty() || event.syncId == null) return
        
        val docData = hashMapOf(
            "title" to event.title,
            "startMillis" to event.startMillis,
            "endMillis" to event.endMillis,
            "isAllDay" to event.isAllDay,
            "location" to event.location,
            "notes" to event.notes,
            "repeatType" to event.repeatType,
            "reminderMinutes" to event.reminderMinutes,
            "syncId" to event.syncId,
            "colorHex" to event.colorHex,
            "isCompleted" to event.isCompleted,
            "linkedEstimateId" to event.linkedEstimateId,
            "lastUpdatedBy" to userPreferences.getDeviceUUID(),
            "createdBy" to userPreferences.getDeviceUUID(),
            "createdAt" to event.createdAt,
            "updatedAt" to event.updatedAt,
            "teamId" to event.teamId,
            "slotPosition" to event.slotPosition
        )
        
        firestore.collection("rooms")
            .document(roomCode)
            .collection("events")
            .document(event.syncId)
            .set(docData, com.google.firebase.firestore.SetOptions.merge())
    }

    private fun deleteEventFromFirestore(syncId: String) {
        val roomCode = userPreferences.getLastRoomCode()
        if (roomCode.isEmpty()) return
        
        firestore.collection("rooms")
            .document(roomCode)
            .collection("events")
            .document(syncId)
            .delete()
    }
}
