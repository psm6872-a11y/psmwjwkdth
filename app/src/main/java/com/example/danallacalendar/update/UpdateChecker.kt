package com.example.danallacalendar.update

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String,
    val assetId: Long = 0L,
    val releaseNotes: String? = null,
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
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            
            // Set settings (fetch interval of 0 seconds to force immediate fetch)
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0)
                .build()
            Tasks.await(remoteConfig.setConfigSettingsAsync(configSettings), 5, TimeUnit.SECONDS)

            // Fetch and activate
            val fetchTask = remoteConfig.fetchAndActivate()
            
            // Await completion with 10 seconds timeout
            val success = Tasks.await(fetchTask, 10, TimeUnit.SECONDS)
            if (!success) {
                android.util.Log.w("UpdateChecker", "Remote Config fetch completed but activate returned false or failed")
            }

            val latestVersion = remoteConfig.getString("latest_version")
            val apkDownloadUrl = remoteConfig.getString("apk_download_url")

            if (latestVersion.isEmpty() || apkDownloadUrl.isEmpty()) {
                throw IOException("Remote Config values for latest_version or apk_download_url are empty.")
            }

            val currentVersion = getCurrentVersion(context)

            if (isNewerVersion(currentVersion, latestVersion)) {
                UpdateInfo(
                    latestVersion = latestVersion,
                    currentVersion = currentVersion,
                    downloadUrl = apkDownloadUrl,
                    assetId = 0L,
                    releaseNotes = "New version available: $latestVersion"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateChecker", "Remote Config 업데이트 확인 실패: ${e.message}", e)
            throw IOException("업데이트 확인 실패, 나중에 다시 시도해주세요", e)
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
