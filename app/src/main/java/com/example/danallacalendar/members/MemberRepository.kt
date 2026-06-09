package com.example.danallacalendar.members

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class Member(
    val deviceUUID: String = "",
    val nickname: String = "",
    val joinedAt: Timestamp? = null,
    val lastSeen: Timestamp? = null
)

@Singleton
class MemberRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun registerOrUpdateMember(roomCode: String, deviceUUID: String, nickname: String) {
        if (roomCode.isBlank() || deviceUUID.isBlank()) return
        
        val docRef = firestore.collection("rooms")
            .document(roomCode)
            .collection("members")
            .document(deviceUUID)
            
        docRef.get().addOnSuccessListener { document ->
            val data = hashMapOf<String, Any>(
                "nickname" to nickname,
                "lastSeen" to Timestamp.now()
            )
            if (!document.exists() || document.get("joinedAt") == null) {
                data["joinedAt"] = Timestamp.now()
            }
            docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
        }.addOnFailureListener {
            // Fallback: set directly if fetch fails
            val data = hashMapOf<String, Any>(
                "nickname" to nickname,
                "joinedAt" to Timestamp.now(),
                "lastSeen" to Timestamp.now()
            )
            docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
        }
    }

    fun getMembersFlow(roomCode: String): Flow<List<Member>> = callbackFlow {
        if (roomCode.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("rooms")
            .document(roomCode)
            .collection("members")
            .orderBy("joinedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val members = snapshot.documents.mapNotNull { doc ->
                        val nickname = doc.getString("nickname") ?: return@mapNotNull null
                        val joinedAt = doc.getTimestamp("joinedAt")
                        val lastSeen = doc.getTimestamp("lastSeen")
                        Member(
                            deviceUUID = doc.id,
                            nickname = nickname,
                            joinedAt = joinedAt,
                            lastSeen = lastSeen
                        )
                    }
                    trySend(members)
                }
            }
        awaitClose { listener.remove() }
    }

    fun getRoomCreatorFlow(roomCode: String): Flow<String?> = callbackFlow {
        if (roomCode.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("rooms")
            .document(roomCode)
            .collection("info")
            .document("details")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.getString("createdBy"))
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun removeMember(roomCode: String, deviceUUID: String) {
        firestore.collection("rooms")
            .document(roomCode)
            .collection("members")
            .document(deviceUUID)
            .delete()
            .await()
    }
}
