package com.example.danallacalendar.estimate

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.JavascriptInterface
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
import kotlin.coroutines.resume

object EstimatePrintHelper {

    /**
     * HTML 콘텐츠를 WebView의 createPrintDocumentAdapter()로 직접 인쇄합니다.
     * 이 방식은 Android PrintManager가 HTML을 A4 페이지에 맞게 렌더링하므로
     * 이미지 축소 문제가 없고 실제 용지에 맞는 출력이 가능합니다.
     */
    fun printEstimate(context: Context, htmlContent: String, estimate: Estimate) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            try {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager

                // A4 96dpi 기준 폭 = 794px
                // WebView에 명시적으로 크기를 잡아줘야 인쇄 시 견적서가 A4 폭에 맞게 렌더링됨
                val pageWidthPx = 794
                val pageHeightPx = 10000

                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.useWideViewPort = false   // false: HTML 고정 폭 그대로 사용
                    settings.loadWithOverviewMode = false
                    // 명시적으로 A4 폭(794px)으로 레이아웃 크기 지정
                    layout(0, 0, pageWidthPx, pageHeightPx)
                }
                webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

                var adapter: android.print.PrintDocumentAdapter? = null

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        handler.postDelayed({
                            try {
                                adapter = webView.createPrintDocumentAdapter("이사 견적서 - ${estimate.customerName}")
                                val printAttributes = android.print.PrintAttributes.Builder()
                                    .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                                    .setResolution(android.print.PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                                    .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                                    .build()
                                printManager.print(
                                    "이사 견적서 - ${estimate.customerName}",
                                    adapter!!,
                                    printAttributes
                                )
                            } catch (e: Throwable) {
                                android.util.Log.e("WebViewPdf", "Print failed", e)
                                Toast.makeText(context, "인쇄 중 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }, 600)
                    }
                }

                webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                Toast.makeText(context, "인쇄 준비 중...", Toast.LENGTH_SHORT).show()

            } catch (e: Throwable) {
                android.util.Log.e("WebViewPdf", "printEstimate failed", e)
                Toast.makeText(context, "인쇄 오류: ${e.message}", Toast.LENGTH_SHORT).show()
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

    // JavaScript 인터페이스: HTML 전체 높이를 콜백으로 받아옴
    private class HeightBridge(val onHeight: (Int) -> Unit) {
        @JavascriptInterface
        fun reportHeight(height: Int) {
            onHeight(height)
        }
    }

    suspend fun renderHtmlToJpg(context: Context, htmlContent: String, estimate: Estimate): String? {
        android.util.Log.d("WebViewPdf", "[LOG] renderHtmlToJpg called. Switching to Main thread...")
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String?> { continuation ->
                try {
                    android.util.Log.d("WebViewPdf", "[LOG] Creating WebView on Main thread...")
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    var hasResumed = false

                    // 페이지 폭 (A4 96dpi = 794px)
                    val pageWidth = 794

                    val webView = WebView(context).apply {
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = false
                        settings.javaScriptEnabled = true
                    }
                    webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

                    fun doRenderWithHeight(contentHeight: Int) {
                        if (hasResumed) return
                        hasResumed = true
                        try {
                            android.util.Log.d("WebViewPdf", "[LOG] doRender: contentHeight=$contentHeight")

                            // 실제 높이로 WebView 레이아웃
                            val finalHeight = maxOf(contentHeight, 1123)
                            webView.layout(0, 0, pageWidth, finalHeight)

                            val tempDir = File(context.cacheDir, "danalla_temp")
                            if (!tempDir.exists()) tempDir.mkdirs()
                            tempDir.listFiles()?.forEach { it.delete() }

                            val dateStr = estimate.estimateDate.ifBlank { estimate.moveDate }
                            val dateParts = dateStr.split("-")
                            val monthDay = if (dateParts.size >= 3) "${dateParts[1]}-${dateParts[2]}" else "00-00"
                            val rawPhone = estimate.phoneNumber.replace(Regex("[^0-9]"), "")
                            val last4 = if (rawPhone.length >= 4) rawPhone.takeLast(4) else "0000"
                            val fileName = "${monthDay}_$last4.jpg"
                            val jpgFile = File(tempDir, fileName)

                            // 고해상도 비트맵 생성 (scale=2.5)
                            val scale = 2.5f
                            val bmpWidth = (pageWidth * scale).toInt()
                            val bmpHeight = (finalHeight * scale).toInt()
                            android.util.Log.d("WebViewPdf", "[LOG] Bitmap size: ${bmpWidth}x${bmpHeight}")

                            val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            canvas.scale(scale, scale)
                            webView.draw(canvas)

                            java.io.FileOutputStream(jpgFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            bitmap.recycle()

                            android.util.Log.d("WebViewPdf", "[LOG] JPG saved: ${jpgFile.absolutePath}")
                            if (continuation.isActive) {
                                continuation.resume(jpgFile.absolutePath)
                            }
                        } catch (e: Throwable) {
                            android.util.Log.e("WebViewPdf", "doRender failed", e)
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }

                    // JS 인터페이스로 높이 측정
                    val bridge = HeightBridge { height ->
                        handler.post {
                            android.util.Log.d("WebViewPdf", "[LOG] JS reported height: $height")
                            handler.removeCallbacksAndMessages(null)
                            doRenderWithHeight(height)
                        }
                    }

                    webView.addJavascriptInterface(bridge, "AndroidBridge")

                    // 안전 타임아웃: 8초 후 현재 contentHeight로 강제 렌더링
                    val timeoutRunnable = Runnable {
                        if (!hasResumed) {
                            val fallbackHeight = webView.contentHeight
                            android.util.Log.w("WebViewPdf", "[LOG] Timeout! Using fallback contentHeight=$fallbackHeight")
                            doRenderWithHeight(if (fallbackHeight > 100) fallbackHeight else 1123)
                        }
                    }
                    handler.postDelayed(timeoutRunnable, 8000)

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            android.util.Log.d("WebViewPdf", "[LOG] onPageFinished. Measuring content height via JS...")
                            // 렌더링이 완전히 끝날 때까지 약간 대기 후 JS로 높이 측정
                            handler.postDelayed({
                                webView.evaluateJavascript(
                                    "document.documentElement.scrollHeight"
                                ) { value ->
                                    val height = value?.toIntOrNull() ?: webView.contentHeight
                                    android.util.Log.d("WebViewPdf", "[LOG] JS scrollHeight=$height, contentHeight=${webView.contentHeight}")
                                    handler.removeCallbacks(timeoutRunnable)
                                    val finalH = if (height > 100) height else maxOf(webView.contentHeight, 1123)
                                    doRenderWithHeight(finalH)
                                }
                            }, 800)
                        }
                    }

                    // 초기 레이아웃: 충분히 높은 임시 높이로 설정
                    webView.layout(0, 0, pageWidth, 10000)
                    android.util.Log.d("WebViewPdf", "[LOG] Loading HTML (size=${htmlContent.length})...")
                    webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)

                } catch (t: Throwable) {
                    android.util.Log.e("WebViewPdf", "Failed to initialize WebView", t)
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
