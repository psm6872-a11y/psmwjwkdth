package com.example.danallacalendar.estimate

import com.example.danallacalendar.BuildConfig
import com.example.danallacalendar.data.local.UserPreferences
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EstimateRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userPreferences: UserPreferences
) {
    private val client = OkHttpClient()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

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

    suspend fun saveToGoogleSheets(webAppUrl: String, estimate: Estimate) {
        val targetUrl = webAppUrl.ifBlank { BuildConfig.SPREADSHEET_WEB_APP_URL }
        if (targetUrl.isBlank()) return

        val json = JSONObject().apply {
            put("id", estimate.id)
            put("customerName", estimate.customerName)
            put("phoneNumber", estimate.phoneNumber)
            put("departure", estimate.departure)
            put("destination", estimate.destination)
            put("moveDate", estimate.moveDate)
            put("moveType", estimate.moveType)
            put("cargoSize", estimate.cargoSize)
            put("amount", estimate.amount)
            put("memo", estimate.memo)
            put("estimateDate", estimate.estimateDate)
            put("startTime", estimate.startTime)
            put("createdAt", estimate.createdAt)
        }

        val body = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(webAppUrl)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("스프레드시트 전송 실패: ${response.code}")
            }
        }
    }
}
