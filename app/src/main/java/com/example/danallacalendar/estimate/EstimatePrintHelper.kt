package com.example.danallacalendar.estimate

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
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
     * 모든 기기에서 동일한 렌더링을 보장하는 160dpi 고정 WebView 컨텍스트를 생성합니다.
     *
     * 문제: WebView의 layout()은 물리 픽셀(physical pixels)을 받습니다.
     *   - 1x(160dpi) 기기: 794 물리px = 794 CSS px → HTML 뷰포트(794px)와 일치 ✅
     *   - 2x(320dpi) 기기: 794 물리px = 397 CSS px → HTML이 절반만 보임 ❌
     *   - 3x(480dpi) 기기: 794 물리px = 265 CSS px → HTML이 1/3만 보임 ❌
     *
     * 해결: WebView가 항상 160dpi(1x)라고 인식하게 강제하면
     *   모든 기기에서 794 물리px = 794 CSS px로 통일됩니다.
     */
    private fun createFixedDpiContext(context: Context): Context {
        val config = context.resources.configuration
        config.densityDpi = 160 // mdpi(1x) 고정 → 794 CSS px = 794 물리 px
        return context.createConfigurationContext(config)
    }

    /**
     * HTML 콘텐츠를 WebView의 createPrintDocumentAdapter()로 직접 인쇄합니다.
     * 모든 기기에서 동일한 A4 출력 미리보기를 보장합니다.
     */
    fun printEstimate(context: Context, htmlContent: String, estimate: Estimate) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            try {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager

                // 160dpi 고정 컨텍스트: 모든 기기에서 794 CSS px = 794 물리 px
                val fixedContext = createFixedDpiContext(context)
                val pageWidthPx = 794   // A4 at 96dpi = 794px (160dpi 기준으로 통일)
                val pageHeightPx = 20000

                val webView = WebView(fixedContext).apply {
                    settings.javaScriptEnabled = true
                    settings.useWideViewPort = true       // HTML viewport 설정 활성화
                    settings.loadWithOverviewMode = false // 자동 축소 방지 (뷰포트 그대로 사용)
                    settings.textZoom = 100               // 시스템 글자 크기 설정 무시
                    settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                    settings.domStorageEnabled = true
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

                    // 기기 원래의 density 적용: WebView 내부의 CSS px 너비를 항상 794px로 맞추기 위함
                    val density = context.resources.displayMetrics.density
                    val pageWidth = 794
                    val layoutWidth = (pageWidth * density).toInt()

                    val webView = WebView(context).apply {
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = false // 자동 축소 방지
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.textZoom = 100              // 시스템 글자 크기 설정 무시
                        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                    }
                    webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

                    fun doRenderWithHeight(contentHeight: Int) {
                        if (hasResumed) return
                        hasResumed = true
                        try {
                            android.util.Log.d("WebViewPdf", "[LOG] doRender: contentHeight=$contentHeight, density=$density")

                            val finalHeight = maxOf(contentHeight, 1123)
                            val layoutHeight = (finalHeight * density).toInt()
                            webView.layout(0, 0, layoutWidth, layoutHeight)

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
                            
                            // WebView 물리 크기(density 곱해짐)를 타겟 해상도(scale)로 맵핑하기 위한 스케일 비율 계산
                            val canvasScale = scale / density
                            canvas.scale(canvasScale, canvasScale)
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

                    // JavaScript 인터페이스로 높이 측정
                    val bridge = HeightBridge { height ->
                        handler.post {
                            android.util.Log.d("WebViewPdf", "[LOG] JS reported height: $height")
                            handler.removeCallbacksAndMessages(null)
                            doRenderWithHeight(height)
                        }
                    }

                    webView.addJavascriptInterface(bridge, "AndroidBridge")

                    // 안전 타임아웃: 10초 후 현재 contentHeight로 강제 렌더링
                    val timeoutRunnable = Runnable {
                        if (!hasResumed) {
                            val fallbackHeight = webView.contentHeight
                            android.util.Log.w("WebViewPdf", "[LOG] Timeout! Using fallback contentHeight=$fallbackHeight")
                            doRenderWithHeight(if (fallbackHeight > 100) fallbackHeight else 1123)
                        }
                    }
                    handler.postDelayed(timeoutRunnable, 10000)

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            android.util.Log.d("WebViewPdf", "[LOG] onPageFinished. Measuring content height via JS...")
                            handler.postDelayed({
                                webView.evaluateJavascript(
                                    "(function() { " +
                                    "  document.body.style.overflow = 'visible'; " +
                                    "  var page = document.querySelector('.page'); " +
                                    "  var h = page ? page.offsetHeight : document.body.offsetHeight; " +
                                    "  if (!h || h < 100) { " +
                                    "    h = Math.max(document.documentElement.scrollHeight, document.body.scrollHeight); " +
                                    "  } " +
                                    "  return h; " +
                                    "})()"
                                ) { value ->
                                    val height = value?.toIntOrNull() ?: webView.contentHeight
                                    android.util.Log.d("WebViewPdf", "[LOG] JS calculatedHeight=$height, contentHeight=${webView.contentHeight}")
                                    handler.removeCallbacks(timeoutRunnable)
                                    val finalH = if (height > 100) height else maxOf(webView.contentHeight, 1123)
                                    doRenderWithHeight(finalH)
                                }
                            }, 1500)
                        }
                    }

                    // 초기 레이아웃: 충분히 높은 임시 높이로 설정 (density 곱한 물리 픽셀 크기 적용)
                    webView.layout(0, 0, layoutWidth, (20000 * density).toInt())
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
