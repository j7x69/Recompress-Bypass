package com.recompress.bypass

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

object WebViewCleanupHelper {
    /**
     * Dọn dẹp an toàn toàn bộ dữ liệu tạm, bộ nhớ đệm, video blobs của WebView,
     * nhưng BẢO TỒN 100% cookie phiên đăng nhập (CookieManager).
     */
    fun purgeWebViewGarbage(context: Context, webView: WebView?) {
        try {
            // 1. Lưu toàn bộ cookie xuống đĩa trước tiên để đảm bảo phiên đăng nhập không bao giờ mất
            CookieManager.getInstance().flush()

            // 2. Dọn sạch cache WebView (RAM + Disk) và giải phóng WebView
            webView?.let { wv ->
                wv.clearCache(true)
                wv.clearHistory()
                wv.stopLoading()
                wv.loadUrl("about:blank")
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }

            // 3. Xóa toàn bộ WebStorage (IndexedDB, WebSQL, AppCache, Blob storage rác của TikTok)
            WebStorage.getInstance().deleteAllData()

            // 4. Quét dọn các thư mục blob/cache rác trên ổ cứng (ngoại trừ file Cookies)
            cleanDiskGarbage(context)
        } catch (_: Exception) {}
    }

    /**
     * Quét và xóa các thư mục rác do Chromium/WebView tạo ra trong app_webview và cacheDir.
     * TUYỆT ĐỐI KHÔNG chạm vào các file Cookies / Cookies-journal.
     */
    fun cleanDiskGarbage(context: Context) {
        try {
            // Dọn cacheDir
            context.cacheDir?.listFiles()?.forEach { file ->
                if (file.name.contains("webview", ignoreCase = true) ||
                    file.name.contains("chromium", ignoreCase = true) ||
                    file.name.endsWith(".tmp") ||
                    file.name.startsWith("temp_")
                ) {
                    file.deleteRecursively()
                }
            }

            // Dọn các thư mục blob, cache trong dataDir/app_webview
            val appWebviewDir = File(context.dataDir, "app_webview")
            if (appWebviewDir.exists() && appWebviewDir.isDirectory) {
                deleteSpecificGarbageDirs(appWebviewDir)
            }
        } catch (_: Exception) {}
    }

    private fun deleteSpecificGarbageDirs(dir: File) {
        val garbageDirNames = setOf(
            "blob_storage",
            "CacheStorage",
            "GPUCache",
            "VideoDecodeStats",
            "Code Cache",
            "WebStorage",
            "Cache",
            "IndexedDB",
            "Service Worker"
        )

        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (garbageDirNames.contains(file.name)) {
                    file.deleteRecursively()
                } else {
                    deleteSpecificGarbageDirs(file)
                }
            } else {
                val lowerName = file.name.lowercase()
                if (!lowerName.contains("cookie") &&
                    (lowerName.endsWith(".tmp") || lowerName.endsWith(".log") || lowerName.startsWith("blob_") || lowerName.contains("trash"))
                ) {
                    file.delete()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TikTokStudioScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var currentTitle by remember { mutableStateOf("TikTok Studio") }
    var isDesktopMode by remember { mutableStateOf(true) }

    // Callback chọn file từ WebView cho việc upload video
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            filePathCallback?.onReceiveValue(uris.toTypedArray())
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    val defaultUserAgent = remember { WebSettings.getDefaultUserAgent(context) }

    val handleClose = {
        val wv = webViewInstance
        webViewInstance = null
        WebViewCleanupHelper.purgeWebViewGarbage(context, wv)
        onClose()
    }

    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            handleClose()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val wv = webViewInstance
            webViewInstance = null
            WebViewCleanupHelper.purgeWebViewGarbage(context, wv)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = Color.White
                        )
                        Text(
                            text = if (isDesktopMode) "Chế độ Desktop • Đã lưu phiên đăng nhập" else "Chế độ Mobile • Đã lưu phiên",
                            fontSize = 11.sp,
                            color = Color(0xFF69F0AE)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = handleClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Chuyển đổi Desktop / Mobile UA
                    IconButton(onClick = {
                        isDesktopMode = !isDesktopMode
                        webViewInstance?.settings?.userAgentString = if (isDesktopMode) desktopUserAgent else defaultUserAgent
                        webViewInstance?.reload()
                    }) {
                        Icon(
                            imageVector = if (isDesktopMode) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
                            contentDescription = "Đổi User-Agent",
                            tint = Color.White
                        )
                    }

                    // Tải lại trang
                    IconButton(onClick = {
                        CookieManager.getInstance().flush()
                        webViewInstance?.reload()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Tải lại",
                            tint = Color.White
                        )
                    }

                    // Mở trình duyệt ngoài
                    IconButton(onClick = {
                        val currentUrl = webViewInstance?.url ?: "https://www.tiktok.com/tiktokstudio"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                        context.startActivity(intent)
                    }) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Mở ngoài",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF000000)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF000000))
        ) {
            // Thanh tiến trình tải trang mỏng
            if (pageProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { pageProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color(0xFF00E5FF),
                    trackColor = Color.Transparent
                )
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Cấu hình lưu trữ đăng nhập vĩnh viễn (Cookie & Storage)
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = false
                            @Suppress("DEPRECATION")
                            allowFileAccessFromFileURLs = false
                            @Suppress("DEPRECATION")
                            allowUniversalAccessFromFileURLs = false
                            allowContentAccess = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = if (isDesktopMode) desktopUserAgent else defaultUserAgent
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) {
                                    currentTitle = title
                                }
                            }

                            // Cho phép TikTok Studio mở trình chọn file để tải video lên
                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallbackParam: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = filePathCallbackParam
                                fileChooserLauncher.launch("video/*")
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // Lưu cookie ngay lập tức xuống bộ nhớ máy
                                CookieManager.getInstance().flush()
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val reqUri = request?.url ?: return false
                                val scheme = reqUri.scheme?.lowercase() ?: ""
                                val host = reqUri.host?.lowercase() ?: ""

                                // Chỉ cho phép giao thức bảo mật HTTPS
                                if (scheme != "https") {
                                    return true // Chặn các scheme nguy hiểm (javascript:, intent:, file:, v.v.)
                                }

                                // Danh sách domain an toàn của TikTok & các cổng đăng nhập OAuth chính thức
                                val isAllowed = host.endsWith("tiktok.com") ||
                                        host.endsWith("tiktokv.com") ||
                                        host.endsWith("byteoversea.com") ||
                                        host.endsWith("ibytedtos.com") ||
                                        host.endsWith("sgsnssdk.com") ||
                                        host == "accounts.google.com" ||
                                        host.endsWith("apple.com") ||
                                        host.endsWith("facebook.com") ||
                                        host.endsWith("twitter.com") ||
                                        host.endsWith("x.com")

                                return if (isAllowed) {
                                    false // Cho phép duyệt trực tiếp trong WebView
                                } else {
                                    // Mở ra trình duyệt hệ thống thay vì load ngầm trong app
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, reqUri)
                                        ctx.startActivity(intent)
                                    } catch (_: Exception) {}
                                    true
                                }
                            }
                        }

                        loadUrl("https://www.tiktok.com/tiktokstudio")
                        webViewInstance = this
                    }
                },
                update = { webView ->
                    webViewInstance = webView
                }
            )
        }
    }
}
