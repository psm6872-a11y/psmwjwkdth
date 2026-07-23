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
    val lastSeen: Timestamp? = null,
    val hasWritePermission: Boolean = false
)

@Singleton
class MemberRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userPreferences: com.example.danallacalendar.data.local.UserPreferences
) {
    fun registerOrUpdateMember(roomCode: String, deviceUUID: String, nickname: String) {
        if (roomCode.isBlank() || deviceUUID.isBlank() || nickname.isBlank()) return
        
        firestore.collection("rooms")
            .document(roomCode)
            .collection("info")
            .document("details")
            .get()
            .addOnSuccessListener { detailsDoc ->
                val creatorUUID = detailsDoc.getString("createdBy") ?: ""
                val isCreator = creatorUUID.isNotEmpty() && creatorUUID == deviceUUID

                firestore.collection("rooms")
                    .document(roomCode)
                    .collection("members")
                    .whereEqualTo("nickname", nickname)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        var targetUUID = deviceUUID
                        if (querySnapshot != null && !querySnapshot.isEmpty) {
                            val existingDoc = querySnapshot.documents[0]
                            val existingUUID = existingDoc.id
                            if (existingUUID != deviceUUID) {
                                userPreferences.setDeviceUUID(existingUUID)
                                targetUUID = existingUUID
                                android.util.Log.d("MemberRepository", "Restored existing deviceUUID ($existingUUID) for nickname: $nickname")
                            }
                        }
                        
                        val docRef = firestore.collection("rooms")
                            .document(roomCode)
                            .collection("members")
                            .document(targetUUID)
                            
                        docRef.get().addOnSuccessListener { document ->
                            val data = hashMapOf<String, Any>(
                                "nickname" to nickname,
                                "lastSeen" to Timestamp.now()
                            )
                            if (!document.exists() || document.get("joinedAt") == null) {
                                data["joinedAt"] = Timestamp.now()
                            }
                            if (!document.exists() || document.get("hasWritePermission") == null) {
                                data["hasWritePermission"] = isCreator
                            } else {
                                if (isCreator) {
                                    data["hasWritePermission"] = true
                                }
                            }
                            docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                        }.addOnFailureListener {
                            val data = hashMapOf<String, Any>(
                                "nickname" to nickname,
                                "joinedAt" to Timestamp.now(),
                                "lastSeen" to Timestamp.now(),
                                "hasWritePermission" to isCreator
                            )
                            docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                        }
                    }
                    .addOnFailureListener {
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
                            if (!document.exists() || document.get("hasWritePermission") == null) {
                                data["hasWritePermission"] = isCreator
                            } else {
                                if (isCreator) {
                                    data["hasWritePermission"] = true
                                }
                            }
                            docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                        }.addOnFailureListener {
                            val data = hashMapOf<String, Any>(
                                "nickname" to nickname,
                                "joinedAt" to Timestamp.now(),
                                "lastSeen" to Timestamp.now(),
                                "hasWritePermission" to isCreator
                            )
                            docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                        }
                    }
            }
            .addOnFailureListener {
                // detailsDoc load failure, fallback to default registration without creator UUID check
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
                    if (!document.exists() || document.get("hasWritePermission") == null) {
                        data["hasWritePermission"] = false
                    }
                    docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                }.addOnFailureListener {
                    val data = hashMapOf<String, Any>(
                        "nickname" to nickname,
                        "joinedAt" to Timestamp.now(),
                        "lastSeen" to Timestamp.now(),
                        "hasWritePermission" to false
                    )
                    docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                }
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
                        val hasWritePermission = doc.getBoolean("hasWritePermission") ?: false
                        Member(
                            deviceUUID = doc.id,
                            nickname = nickname,
                            joinedAt = joinedAt,
                            lastSeen = lastSeen,
                            hasWritePermission = hasWritePermission
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

    suspend fun transferHost(roomCode: String, newHostUUID: String) {
        firestore.collection("rooms")
            .document(roomCode)
            .collection("info")
            .document("details")
            .update("createdBy", newHostUUID)
            .await()
    }

    suspend fun updateWritePermission(roomCode: String, deviceUUID: String, hasWrite: Boolean) {
        firestore.collection("rooms")
            .document(roomCode)
            .collection("members")
            .document(deviceUUID)
            .update("hasWritePermission", hasWrite)
            .await()
    }
}
