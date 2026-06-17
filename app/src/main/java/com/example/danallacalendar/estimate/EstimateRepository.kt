package com.example.danallacalendar.estimate

import com.example.danallacalendar.data.local.UserPreferences
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EstimateRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userPreferences: UserPreferences
) {

    suspend fun saveToFirestore(estimate: Estimate): String {
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

    suspend fun deleteFromFirestore(estimateId: String) {
        val roomCode = userPreferences.getLastRoomCode()
        val docRef = if (roomCode.isNotEmpty()) {
            firestore.collection("rooms").document(roomCode).collection("estimates").document(estimateId)
        } else {
            firestore.collection("estimates").document(estimateId)
        }
        docRef.delete().await()
    }

    fun getEstimatesFlow(): Flow<List<Estimate>> = callbackFlow {
        val roomCode = userPreferences.getLastRoomCode()
        if (roomCode.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("rooms")
            .document(roomCode)
            .collection("estimates")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("EstimateRepository", "Listen failed", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(Estimate::class.java)
                        } catch (e: Exception) {
                            android.util.Log.e("EstimateRepository", "Failed to deserialize Estimate", e)
                            null
                        }
                    }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun getEstimateFromFirestore(estimateId: String): Estimate? {
        val roomCode = userPreferences.getLastRoomCode()
        val docRef = if (roomCode.isNotEmpty()) {
            firestore.collection("rooms").document(roomCode).collection("estimates").document(estimateId)
        } else {
            firestore.collection("estimates").document(estimateId)
        }
        return try {
            val snapshot = docRef.get().await()
            snapshot.toObject(Estimate::class.java)
        } catch (e: Exception) {
            android.util.Log.e("EstimateRepository", "Failed to get estimate from Firestore", e)
            null
        }
    }

    suspend fun getEstimateByScheduleId(scheduleId: String): Estimate? {
        val roomCode = userPreferences.getLastRoomCode()
        val collectionRef = if (roomCode.isNotEmpty()) {
            firestore.collection("rooms").document(roomCode).collection("estimates")
        } else {
            firestore.collection("estimates")
        }
        return try {
            val snapshot = collectionRef.whereEqualTo("scheduleId", scheduleId).limit(1).get().await()
            val doc = snapshot.documents.firstOrNull()
            doc?.toObject(Estimate::class.java)
        } catch (e: Exception) {
            android.util.Log.e("EstimateRepository", "Failed to query estimate by scheduleId", e)
            null
        }
    }

}
