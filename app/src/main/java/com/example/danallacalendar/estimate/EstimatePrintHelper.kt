package com.example.danallacalendar.estimate

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlin.coroutines.resume

object EstimatePrintHelper {

    fun printEstimate(context: Context, htmlContent: String, estimate: Estimate) {
        Toast.makeText(context, "인쇄를 시작합니다...", Toast.LENGTH_SHORT).show()
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            val jpgPath = renderHtmlToJpg(context, htmlContent, estimate)
            if (jpgPath != null) {
                try {
                    val printHelper = androidx.print.PrintHelper(context).apply {
                        scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT
                        orientation = androidx.print.PrintHelper.ORIENTATION_PORTRAIT
                    }
                    val bitmap = android.graphics.BitmapFactory.decodeFile(jpgPath)
                    if (bitmap != null) {
                        val jobName = "이사 견적서 - ${estimate.customerName}"
                        printHelper.printBitmap(jobName, bitmap)
                    } else {
                        Toast.makeText(context, "인쇄 이미지 디코딩에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("WebViewPdf", "PrintHelper failed", e)
                    Toast.makeText(context, "인쇄 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "인쇄 이미지 생성에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareEstimateAsJpg(context: Context, htmlContent: String, estimate: Estimate) {
        Toast.makeText(context, "공유용 이미지 생성 중...", Toast.LENGTH_SHORT).show()
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            val jpgPath = renderHtmlToJpg(context, htmlContent, estimate)
            if (jpgPath != null) {
                launchShareIntent(context, File(jpgPath), estimate)
            } else {
                Toast.makeText(context, "이미지 생성 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun renderHtmlToJpg(context: Context, htmlContent: String, estimate: Estimate): String? {
        android.util.Log.d("WebViewPdf", "[LOG] renderHtmlToJpg called. Switching to Main thread...")
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String?> { continuation ->
                try {
                    android.util.Log.d("WebViewPdf", "[LOG] suspendCancellableCoroutine started on Main thread. Creating WebView...")
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    val webView = WebView(context)
                    webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                    webView.layout(0, 0, 794, 1123)
                    
                    var hasResumed = false
                    
                    fun doRender() {
                        if (hasResumed) return
                        hasResumed = true
                        try {
                            android.util.Log.d("WebViewPdf", "[LOG] doRender triggered. Creating directories and files...")
                            val tempDir = File(context.cacheDir, "danalla_temp")
                            if (!tempDir.exists()) {
                                tempDir.mkdirs()
                            }
                            tempDir.listFiles()?.forEach { it.delete() }

                            val dateStr = estimate.estimateDate.ifBlank { estimate.moveDate }
                            val dateParts = dateStr.split("-")
                            val monthDay = if (dateParts.size >= 3) "${dateParts[1]}-${dateParts[2]}" else "00-00"
                            val rawPhone = estimate.phoneNumber.replace(Regex("[^0-9]"), "")
                            val last4 = if (rawPhone.length >= 4) rawPhone.takeLast(4) else "0000"
                            val fileName = "${monthDay}_$last4.jpg"

                            val jpgFile = File(tempDir, fileName)

                            // Generate High-Res Bitmap
                            android.util.Log.d("WebViewPdf", "[LOG] Generating Bitmap (scale=2.5)...")
                            val scale = 2.5f
                            val width = (794 * scale).toInt()
                            val height = (1123 * scale).toInt()
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.scale(scale, scale)
                            webView.draw(canvas)

                            // Save JPG
                            android.util.Log.d("WebViewPdf", "[LOG] Saving JPG to ${jpgFile.absolutePath}...")
                            java.io.FileOutputStream(jpgFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            bitmap.recycle()

                            android.util.Log.d("WebViewPdf", "[LOG] Rendering process completed successfully!")
                            if (continuation.isActive) {
                                continuation.resume(jpgFile.absolutePath)
                            }
                        } catch (e: Throwable) {
                            android.util.Log.e("WebViewPdf", "PDF to JPG failed", e)
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }

                    // 5-second safety timeout
                    val timeoutRunnable = Runnable {
                        if (!hasResumed) {
                            android.util.Log.w("WebViewPdf", "[LOG] WebView load timed out (5s). Forcing rendering...")
                            doRender()
                        }
                    }
                    handler.postDelayed(timeoutRunnable, 5000)

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            android.util.Log.d("WebViewPdf", "[LOG] onPageFinished callback triggered! Posting render task...")
                            handler.removeCallbacks(timeoutRunnable)
                            handler.postDelayed({
                                doRender()
                            }, 500)
                        }
                    }
                    android.util.Log.d("WebViewPdf", "[LOG] WebView.loadDataWithBaseURL loading html content (size: ${htmlContent.length})...")
                    webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                } catch (t: Throwable) {
                    android.util.Log.e("WebViewPdf", "Failed to initialize WebView or setup render", t)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }

    private fun launchShareIntent(context: Context, jpgFile: File, estimate: Estimate) {
        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            jpgFile
        )
        val defaultSmsPackage = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        val smsBody = "위와 같이 견적 합니다. 검토해 보시고 연락주세요. 감사합니다."

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra("address", estimate.phoneNumber)
            putExtra("recipient", estimate.phoneNumber)
            putExtra(Intent.EXTRA_TEXT, smsBody)
            putExtra("sms_body", smsBody)
            if (defaultSmsPackage != null) {
                setPackage(defaultSmsPackage)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "견적서 전송"))
    }
}
