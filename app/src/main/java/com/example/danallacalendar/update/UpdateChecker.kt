package com.example.danallacalendar.update

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.FirebaseFirestore

data class GithubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val body: String?,
    @SerializedName("assets") val assets: List<GithubAsset>
)

data class GithubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("id") val id: Long,
    @SerializedName("url") val url: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String
)

data class UpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String,
    val assetId: Long,
    val releaseNotes: String?,
    val token: String? = null
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()  // 확인중
    object NoNetwork : UpdateState() // 인터넷 없음
    object UpToDate : UpdateState()  // 최신버전
    object Error : UpdateState()     // 오류
    data class UpdateAvailable(
        val version: String,
        val downloadUrl: String,
        val updateInfo: UpdateInfo
    ) : UpdateState()                // 업데이트 있음
}

object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/psm6872-a11y/psmwjwkdth/releases/latest"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
        
    private val gson = Gson()

    private fun getGithubToken(): String? {
        return try {
            val task = FirebaseFirestore.getInstance()
                .collection("config")
                .document("app")
                .get()
            val document = Tasks.await(task, 10, TimeUnit.SECONDS)
            val token = document.getString("github_token")
            if (token.isNullOrEmpty()) {
                android.util.Log.e("UpdateChecker", "Firestore: github_token 필드가 비어있음")
            }
            token
        } catch (e: Exception) {
            android.util.Log.e("UpdateChecker", "Firestore token fetch 실패: ${e.message}", e)
            null
        }
    }

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        // Tasks.await()를 IO 스레드에서 호출 → Main 스레드 의존 없이 안전하게 동작
        val token = getGithubToken()
        if (token.isNullOrEmpty()) {
            throw IOException("GitHub Access Token not found in Firestore. Please register token under config/app.")
        }

        val request = Request.Builder()
            .url(GITHUB_API_URL)
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "DanallaCalendar-Updater")
            .build()

        var responseBody: String? = null
        var responseCode = 0

        try {
            client.newCall(request).execute().use { response ->
                responseCode = response.code
                if (response.isSuccessful) {
                    responseBody = response.body?.string()
                } else {
                    val errBody = response.body?.string() ?: ""
                    android.util.Log.e("UpdateChecker", "GitHub API fetch error (status $responseCode): $errBody")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw e
        }

        val bodyString = responseBody ?: throw IOException("GitHub API request failed with status: $responseCode")
        val release = gson.fromJson(bodyString, GithubRelease::class.java)
        val currentVersion = getCurrentVersion(context)
        val latestVersion = release.tagName.removePrefix("v").removePrefix("V")

        if (isNewerVersion(currentVersion, latestVersion)) {
            val asset = release.assets.find { it.name == "app-release.apk" }
                ?: release.assets.find { it.name == "app-debug.apk" }
                ?: release.assets.find { it.name.endsWith(".apk") }

            if (asset != null) {
                UpdateInfo(
                    latestVersion = latestVersion,
                    currentVersion = currentVersion,
                    downloadUrl = asset.url, // api asset url (헤더 인증 다운로드용)
                    assetId = asset.id,
                    releaseNotes = release.body,
                    token = token
                )
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurrent = current.removePrefix("v").removePrefix("V")
        val cleanLatest = latest.removePrefix("v").removePrefix("V")

        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val currPart = currentParts.getOrNull(i) ?: 0
            val latePart = latestParts.getOrNull(i) ?: 0
            if (latePart > currPart) return true
            if (currPart > latePart) return false
        }
        return false
    }
}
