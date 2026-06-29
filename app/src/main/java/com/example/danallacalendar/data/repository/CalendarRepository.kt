package com.example.danallacalendar.data.repository

import com.example.danallacalendar.data.CalendarCategory
import com.example.danallacalendar.data.Event
import com.example.danallacalendar.data.DeadlineDate
import com.example.danallacalendar.data.EventDao
import com.example.danallacalendar.data.TrashItem
import com.example.danallacalendar.data.TrashDao
import com.example.danallacalendar.data.EstimatePdf
import com.example.danallacalendar.estimate.Estimate
import com.google.gson.Gson
import com.example.danallacalendar.data.local.UserPreferences
import com.example.danallacalendar.data.model.Room
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.example.danallacalendar.data.EstimatePdfDao
import com.example.danallacalendar.data.BlacklistItem
import javax.inject.Inject

class CalendarRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userPreferences: UserPreferences,
    val eventDao: EventDao,
    val trashDao: TrashDao,
    val estimatePdfDao: EstimatePdfDao,
    val blacklistDao: com.example.danallacalendar.data.BlacklistDao
) {
    private val syncMutex = Mutex()
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
        event.linkedEstimateId?.let { estimateId ->
            if (estimateId.isNotEmpty()) {
                linkEstimateToSchedule(estimateId, id.toString())
            }
        }
    }
    
    suspend fun updateEvent(event: Event) {
        eventDao.updateEvent(event)
        if (event.isSynced) {
            uploadEventToFirestore(event)
        }
        event.linkedEstimateId?.let { estimateId ->
            if (estimateId.isNotEmpty()) {
                linkEstimateToSchedule(estimateId, event.id.toString())
            }
        }
    }

    suspend fun linkEstimateToSchedule(estimateId: String, scheduleId: String) {
        try {
            // 1. Local update
            val pdf = estimatePdfDao.getPdfByEstimateId(estimateId)
            if (pdf != null) {
                val gson = Gson()
                val est = gson.fromJson(pdf.estimateJson, Estimate::class.java)
                val updatedEst = est.copy(scheduleId = scheduleId)
                val updatedJson = gson.toJson(updatedEst)
                val updatedPdf = pdf.copy(estimateJson = updatedJson)
                estimatePdfDao.insertPdf(updatedPdf)
            }
            
            // 2. Firestore update
            val roomCode = userPreferences.getLastRoomCode()
            val docRef = if (roomCode.isNotEmpty()) {
                firestore.collection("rooms").document(roomCode).collection("estimates").document(estimateId)
            } else {
                firestore.collection("estimates").document(estimateId)
            }
            docRef.update("scheduleId", scheduleId).await()
        } catch (e: Exception) {
            android.util.Log.e("CalendarRepository", "Failed to link estimate to schedule", e)
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

    suspend fun uploadBlacklistItem(roomCode: String, item: BlacklistItem) {
        if (roomCode.isEmpty() || item.syncId.isNullOrEmpty()) return
        val doc = hashMapOf(
            "syncId" to item.syncId,
            "phoneNumber" to item.phoneNumber,
            "reason" to item.reason,
            "createdAt" to item.createdAt
        )
        try {
            firestore.collection("rooms")
                .document(roomCode)
                .collection("blacklist")
                .document(item.syncId)
                .set(doc)
                .await()
            // Mark as synced locally using the correct ID to prevent duplicates
            val localId = if (item.id == 0) {
                blacklistDao.getBlacklistItemsBySyncId(item.syncId).firstOrNull()?.id ?: 0
            } else {
                item.id
            }
            if (localId != 0) {
                blacklistDao.insert(item.copy(id = localId, isSynced = true))
            } else {
                blacklistDao.insert(item.copy(isSynced = true))
            }
        } catch (e: Exception) {
            android.util.Log.e("CalendarRepository", "Failed to upload blacklist item", e)
        }
    }

    suspend fun deleteBlacklistItemFromFirestore(roomCode: String, syncId: String) {
        if (roomCode.isEmpty() || syncId.isEmpty()) return
        try {
            firestore.collection("rooms")
                .document(roomCode)
                .collection("blacklist")
                .document(syncId)
                .delete()
                .await()
        } catch (e: Exception) {
            android.util.Log.e("CalendarRepository", "Failed to delete blacklist item from Firestore", e)
        }
    }

    fun startBlacklistRealtimeSync(roomCode: String) = callbackFlow<Unit> {
        if (roomCode.isEmpty()) {
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("rooms")
            .document(roomCode)
            .collection("blacklist")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        syncMutex.withLock {
                            try {
                                val remoteItems = snapshot.documents.mapNotNull { doc ->
                                    val phoneNumber = doc.getString("phoneNumber") ?: return@mapNotNull null
                                    val reason = doc.getString("reason") ?: ""
                                    val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                                    val syncId = doc.getString("syncId") ?: doc.id
                                    BlacklistItem(
                                        phoneNumber = phoneNumber,
                                        reason = reason,
                                        createdAt = createdAt,
                                        syncId = syncId,
                                        isSynced = true
                                    )
                                }

                                // 1. Sync remote items to local
                                remoteItems.forEach { remote ->
                                    val existing = blacklistDao.getBlacklistItemsBySyncId(remote.syncId ?: "")
                                    if (existing.isNotEmpty()) {
                                        val mainExisting = existing.first()
                                        val updated = remote.copy(id = mainExisting.id)
                                        if (mainExisting.phoneNumber != remote.phoneNumber ||
                                            mainExisting.reason != remote.reason ||
                                            mainExisting.createdAt != remote.createdAt
                                        ) {
                                            blacklistDao.insert(updated)
                                        }
                                    } else {
                                        blacklistDao.insert(remote)
                                    }
                                }

                                // 2. Delete local synced items that are no longer on remote
                                val localSynced = blacklistDao.getSyncedBlacklistItems()
                                val remoteSyncIds = remoteItems.map { it.syncId }.toSet()
                                localSynced.forEach { local ->
                                    if (local.syncId !in remoteSyncIds) {
                                        blacklistDao.delete(local)
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SyncError", "Failed to sync remote blacklist", e)
                            }
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
                        syncMutex.withLock {
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
                                    val existingList = eventDao.getEventsListBySyncId(remote.syncId ?: "")
                                    if (existingList.isNotEmpty()) {
                                        val mainExisting = existingList.first()
                                        val updated = remote.copy(id = mainExisting.id)
                                        if (mainExisting.title != remote.title ||
                                            mainExisting.startMillis != remote.startMillis ||
                                            mainExisting.endMillis != remote.endMillis ||
                                            mainExisting.isAllDay != remote.isAllDay ||
                                            mainExisting.location != remote.location ||
                                            mainExisting.notes != remote.notes ||
                                            mainExisting.colorHex != remote.colorHex ||
                                            mainExisting.isCompleted != remote.isCompleted ||
                                            mainExisting.createdAt != remote.createdAt ||
                                            mainExisting.updatedAt != remote.updatedAt ||
                                            mainExisting.linkedEstimateId != remote.linkedEstimateId ||
                                            mainExisting.teamId != remote.teamId ||
                                            mainExisting.slotPosition != remote.slotPosition
                                        ) {
                                            eventDao.updateEvent(updated)
                                        }
                                        // Clean up any extra duplicates
                                        if (existingList.size > 1) {
                                            existingList.drop(1).forEach { duplicate ->
                                                eventDao.deleteEvent(duplicate)
                                            }
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

    fun getTrashItemsFlow(): Flow<List<TrashItem>> = trashDao.getAllTrashItemsFlow()

    suspend fun deleteTrashItemPermanently(item: TrashItem) {
        trashDao.deleteTrashItem(item)
    }

    suspend fun clearTrash() {
        trashDao.clearAllTrashItems()
    }

    suspend fun pruneTrash() {
        val threshold = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        trashDao.deleteExpiredItems(threshold)
    }

    suspend fun moveToTrash(event: Event) {
        val gson = Gson()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREAN)
        val dateStr = dateFormat.format(Date(event.startMillis))
        val timeStr = if (event.isAllDay) "하루종일" else timeFormat.format(Date(event.startMillis))
        val detailText = "일시: $dateStr ($timeStr) ${if (event.location.isNotEmpty()) "/ 위치: " + event.location.split("|||").firstOrNull() else ""}"
        
        val trashItem = TrashItem(
            itemType = "EVENT",
            originalId = event.id.toString(),
            title = event.title,
            detailText = detailText,
            serializedJson = gson.toJson(event),
            deletedAt = System.currentTimeMillis()
        )
        trashDao.insertTrashItem(trashItem)
        deleteEvent(event)
    }

    suspend fun restoreEvent(event: Event) {
        insertEvent(event.copy(id = 0))
    }

    suspend fun moveToTrash(estimate: Estimate) {
        val gson = Gson()
        val detailText = "전화번호: ${estimate.phoneNumber} / 이사날짜: ${estimate.moveDate} / 구분: ${estimate.moveType}"
        
        val trashItem = TrashItem(
            itemType = "ESTIMATE",
            originalId = estimate.id,
            title = "${estimate.customerName} 고객님",
            detailText = detailText,
            serializedJson = gson.toJson(estimate),
            deletedAt = System.currentTimeMillis()
        )
        trashDao.insertTrashItem(trashItem)
        
        // Delete locally (EstimatePdf) and Firestore
        deleteFromFirestore(estimate.id)
        estimatePdfDao.deleteByEstimateId(estimate.id)
    }

    suspend fun restoreEstimate(estimate: Estimate) {
        val gson = Gson()
        val docId = saveToFirestore(estimate)
        
        val moveDateParts = estimate.moveDate.split("-")
        val monthDay = if (moveDateParts.size >= 3) "${moveDateParts[1]}-${moveDateParts[2]}" else "01-01"
        val fileName = estimate.localFilePath?.substringAfterLast("/") ?: "${monthDay}_restored.jpg"
        val filePath = estimate.localFilePath ?: ""
        
        val pdfEntity = EstimatePdf(
            date = monthDay,
            fileName = fileName,
            filePath = filePath,
            createdAt = estimate.createdAt,
            estimateId = docId,
            customerName = estimate.customerName,
            phoneNumber = estimate.phoneNumber,
            moveDate = estimate.moveDate,
            departure = estimate.departure,
            estimateJson = gson.toJson(estimate.copy(id = docId)),
            isSynced = true
        )
        estimatePdfDao.insertPdf(pdfEntity)
    }

    // Suspending wrapper for saveToFirestore with fallback
    private suspend fun saveToFirestore(estimate: Estimate): String {
        val roomCode = userPreferences.getLastRoomCode()
        val docRef = if (roomCode.isNotEmpty()) {
            if (estimate.id.isEmpty()) {
                firestore.collection("rooms").document(roomCode).collection("estimates").document()
            } else {
                firestore.collection("rooms").document(roomCode).collection("estimates").document(estimate.id)
            }
        } else {
            if (estimate.id.isEmpty()) {
                firestore.collection("estimates").document()
            } else {
                firestore.collection("estimates").document(estimate.id)
            }
        }
        val estimateWithId = estimate.copy(id = docRef.id)
        docRef.set(estimateWithId).await()
        return docRef.id
    }

    private suspend fun deleteFromFirestore(estimateId: String) {
        val roomCode = userPreferences.getLastRoomCode()
        val docRef = if (roomCode.isNotEmpty()) {
            firestore.collection("rooms").document(roomCode).collection("estimates").document(estimateId)
        } else {
            firestore.collection("estimates").document(estimateId)
        }
        docRef.delete().await()
    }
}
