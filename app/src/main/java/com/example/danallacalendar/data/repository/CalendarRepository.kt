package com.example.danallacalendar.data.repository

import com.example.danallacalendar.data.local.UserPreferences
import com.example.danallacalendar.data.model.CalendarEvent
import com.example.danallacalendar.data.model.Room
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class CalendarRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userPreferences: UserPreferences
) {
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
                userPreferences.setLastRoomCode(roomCode)
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
                    onSuccess()
                } else {
                    onFailure("존재하지 않는 방 코드입니다.")
                }
            }
            .addOnFailureListener { e ->
                onFailure(e.localizedMessage ?: "방 확인 실패")
            }
    }

    // Fetch room events flow with real-time updates
    fun getEventsFlow(roomCode: String): Flow<List<CalendarEvent>> = callbackFlow {
        val listener = firestore.collection("rooms")
            .document(roomCode)
            .collection("events")
            .orderBy("date", Query.Direction.ASCENDING)
            .orderBy("time", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val events = snapshot.documents.mapNotNull { doc ->
                        val id = doc.getString("id") ?: ""
                        val title = doc.getString("title") ?: ""
                        val date = doc.getString("date") ?: ""
                        val time = doc.getString("time") ?: ""
                        val description = doc.getString("description") ?: ""
                        val createdBy = doc.getString("createdBy") ?: ""
                        val createdByName = doc.getString("createdByName") ?: ""
                        val updatedAt = doc.getTimestamp("updatedAt")
                        CalendarEvent(id, title, date, time, description, createdBy, createdByName, updatedAt)
                    }
                    trySend(events)
                }
            }
        awaitClose { listener.remove() }
    }

    // Add a new event
    fun addEvent(
        roomCode: String,
        title: String,
        date: String,
        time: String,
        description: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val eventRef = firestore.collection("rooms")
            .document(roomCode)
            .collection("events")
            .document() // Auto-generate event ID

        val event = CalendarEvent(
            id = eventRef.id,
            title = title,
            date = date,
            time = time,
            description = description,
            createdBy = userPreferences.getDeviceUUID(),
            createdByName = userPreferences.getNickname(),
            updatedAt = Timestamp.now()
        )

        eventRef.set(event)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Update an event
    fun updateEvent(
        roomCode: String,
        event: CalendarEvent,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val updatedEvent = event.copy(updatedAt = Timestamp.now())
        firestore.collection("rooms")
            .document(roomCode)
            .collection("events")
            .document(event.id)
            .set(updatedEvent)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // Delete an event
    fun deleteEvent(
        roomCode: String,
        eventId: String,
        createdBy: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentDeviceUUID = userPreferences.getDeviceUUID()
        if (createdBy != currentDeviceUUID) {
            onFailure("본인이 작성한 일정만 삭제할 수 있습니다.")
            return
        }

        firestore.collection("rooms")
            .document(roomCode)
            .collection("events")
            .document(eventId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.localizedMessage ?: "삭제 실패") }
    }
}
