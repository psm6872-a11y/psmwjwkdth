package com.example.danallacalendar.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException

object UpdateDownloader {
    private const val TOKEN = ""
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private var activeCall: Call? = null
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
        onProgress: (progress: Float) -> Unit,
        onComplete: (file: File) -> Unit,
        onError: (e: Exception) -> Unit
    ) {
        cancelDownload() // Cancel any ongoing download first

        val thread = Thread {
            try {
                var currentUrl = assetUrl
                var response: Response? = null
                var redirectCount = 0
                val maxRedirects = 5

                while (redirectCount < maxRedirects) {
                    val requestBuilder = Request.Builder()
                        .url(currentUrl)
                        .header("User-Agent", "DanallaCalendar-Updater")

                    if (currentUrl.contains("api.github.com")) {
                        requestBuilder.header("Authorization", "token $TOKEN")
                        requestBuilder.header("Accept", "application/octet-stream")
                    }

                    val request = requestBuilder.build()
                    val call = client.newCall(request)
                    activeCall = call

                    val resp = call.execute()
                    response = resp

                    val code = resp.code
                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        val location = resp.header("Location")
                        resp.close()
                        if (location == null) {
                            throw IOException("Redirect location is null")
                        }
                        currentUrl = location
                        redirectCount++
                    } else {
                        break
                    }
                }

                val finalResponse = response ?: throw IOException("Failed to get response")
                if (finalResponse.code != 200) {
                    finalResponse.close()
                    throw IOException("Server returned code ${finalResponse.code}")
                }

                val body = finalResponse.body ?: throw IOException("Empty response body")
                val totalBytes = body.contentLength()
                val apkFile = File(context.cacheDir, "update.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (activeCall?.isCanceled() == true) {
                                throw IOException("Download cancelled")
                            }
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (totalBytes > 0) {
                                val progress = bytesRead.toFloat() / totalBytes
                                mainHandler.post { onProgress(progress) }
                            }
                        }
                    }
                }
                finalResponse.close()

                if (activeCall?.isCanceled() == true) {
                    apkFile.delete()
                    throw IOException("Download cancelled")
                }

                mainHandler.post { onComplete(apkFile) }
            } catch (e: Exception) {
                if (activeCall?.isCanceled() == true) {
                    // Ignore error if it was cancelled
                    return@Thread
                }
                mainHandler.post { onError(e) }
            } finally {
                activeCall = null
            }
        }
        thread.start()
    }

    fun cancelDownload() {
        try {
            activeCall?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activeCall = null
    }

    fun triggerInstall(context: Context, apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "com.example.danallacalendar.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
