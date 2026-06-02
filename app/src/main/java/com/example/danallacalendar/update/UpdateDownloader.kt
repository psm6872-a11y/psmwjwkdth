package com.example.danallacalendar.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object UpdateDownloader {
    // browser_download_url은 공개 CDN 주소이므로 토큰 불필요
    private val client = OkHttpClient.Builder()
        .followRedirects(true)       // OkHttp가 리다이렉트를 자동 처리
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var isCancelled = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun hasInstallPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun downloadApk(
        context: Context,
        assetUrl: String,
        token: String? = null,
        onProgress: (progress: Float) -> Unit,
        onComplete: (file: File) -> Unit,
        onError: (e: Exception) -> Unit
    ) {
        cancelDownload()
        isCancelled = false

        val thread = Thread {
            try {
                val requestBuilder = Request.Builder()
                    .url(assetUrl)
                    .header("User-Agent", "DanallaCalendar-Updater")

                if (!token.isNullOrEmpty() && (assetUrl.contains("api.github.com") || assetUrl.contains("github.com"))) {
                    requestBuilder.header("Authorization", "token $token")
                    requestBuilder.header("Accept", "application/octet-stream")
                }

                val request = requestBuilder.build()
                val response: Response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    response.close()
                    throw IOException("Server returned code ${response.code}")
                }

                val body = response.body ?: throw IOException("Empty response body")
                val totalBytes = body.contentLength()

                // 실제 기기에서 접근 가능한 외부 저장소 우선 사용, 없으면 cache 디렉토리
                val apkDir: File = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.cacheDir.also { it.mkdirs() }

                if (!apkDir.exists()) apkDir.mkdirs()

                val apkFile = File(apkDir, "update.apk")
                if (apkFile.exists()) apkFile.delete()

                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (isCancelled) throw IOException("Download cancelled")
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (totalBytes > 0) {
                                val progress = bytesRead.toFloat() / totalBytes
                                mainHandler.post { onProgress(progress) }
                            }
                        }
                    }
                }
                response.close()

                if (isCancelled) {
                    apkFile.delete()
                    return@Thread
                }

                mainHandler.post { onComplete(apkFile) }
            } catch (e: Exception) {
                if (isCancelled) return@Thread
                mainHandler.post { onError(e) }
            }
        }
        thread.start()
    }

    fun cancelDownload() {
        isCancelled = true
    }

    fun triggerInstall(context: Context, apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            android.util.Log.e("UpdateDownloader", "triggerInstall failed: ${e.message}", e)
        }
    }
}
