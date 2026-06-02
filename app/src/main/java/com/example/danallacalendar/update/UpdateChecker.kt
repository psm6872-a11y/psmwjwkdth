package com.example.danallacalendar.update

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GithubReleaseMetadata(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val body: String?
)

data class UpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String,
    val assetId: Long,
    val releaseNotes: String?
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
    private const val RELEASE_METADATA_URL = "https://raw.githubusercontent.com/psm6872-a11y/psmwjwkdth/main/release.json"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
        
    private val gson = Gson()

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASE_METADATA_URL)
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
                    android.util.Log.e("UpdateChecker", "Metadata fetch error (status $responseCode): $errBody")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw e
        }

        val bodyString = responseBody ?: throw IOException("Metadata request failed with status: $responseCode")
        val release = gson.fromJson(bodyString, GithubReleaseMetadata::class.java)
        val currentVersion = getCurrentVersion(context)
        val latestVersion = release.tagName.removePrefix("v").removePrefix("V")

        if (isNewerVersion(currentVersion, latestVersion)) {
            val downloadUrl = "https://github.com/psm6872-a11y/psmwjwkdth/releases/download/v$latestVersion/app-release.apk"
            UpdateInfo(
                latestVersion = latestVersion,
                currentVersion = currentVersion,
                downloadUrl = downloadUrl,
                assetId = 0L,
                releaseNotes = release.body
            )
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
