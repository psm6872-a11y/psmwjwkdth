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
    private const val GITHUB_API_URL = "https://api.github.com/repos/psm6872-a11y/psmwjwkdth/releases/latest"
    private const val TOKEN = ""
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
        
    private val gson = Gson()

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GITHUB_API_URL)
            .header("Authorization", "token $TOKEN")
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "DanallaCalendar-Updater")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub API request failed with status: ${response.code}")
            }
            val bodyString = response.body?.string() ?: ""
            val release = gson.fromJson(bodyString, GithubRelease::class.java)
            val currentVersion = getCurrentVersion(context)
            val latestVersion = release.tagName.removePrefix("v").removePrefix("V")

            if (isNewerVersion(currentVersion, latestVersion)) {
                // Find app-release.apk, fallback to app-debug.apk, then any .apk
                val asset = release.assets.find { it.name == "app-release.apk" }
                    ?: release.assets.find { it.name == "app-debug.apk" }
                    ?: release.assets.find { it.name.endsWith(".apk") }

                if (asset != null) {
                    UpdateInfo(
                        latestVersion = latestVersion,
                        currentVersion = currentVersion,
                        downloadUrl = asset.url, // api asset url
                        assetId = asset.id,
                        releaseNotes = release.body
                    )
                } else {
                    null
                }
            } else {
                null
            }
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
