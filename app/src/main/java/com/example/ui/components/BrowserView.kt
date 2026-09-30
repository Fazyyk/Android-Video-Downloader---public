package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.Toast
import com.example.data.media3.MediaBrowserInterceptorService
import com.example.data.model.DetectedMedia
import com.example.data.model.MediaType
import com.example.data.model.WebBookmark
import com.example.data.util.MediaSnifferEngine
import com.example.data.worker.MediaDetectionHub
import java.io.File
import kotlinx.coroutines.delay
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserView(
    initialUrl: String,
    detectedMediaList: List<DetectedMedia>,
    isDesktopMode: Boolean,
    isSniffingActive: Boolean = false,
    deepScanTrigger: Long = 0L,
    onUrlChanged: (String, String, Int) -> Unit,
    onMediaDetected: (DetectedMedia) -> Unit,
    onOpenMediaSniffer: () -> Unit,
    onRequestPageScan: () -> Unit = {},
    onQuickDirectDownload: (String, String) -> Unit,
    onToggleDesktopMode: () -> Unit,
    onDirectDownloadMedia: ((DetectedMedia) -> Unit)? = null,
    onPreviewDetectedMedia: ((DetectedMedia) -> Unit)? = null,
    bookmarks: List<WebBookmark> = emptyList(),
    onSaveBookmark: (title: String, url: String, category: String, isPinned: Boolean) -> Unit = { _, _, _, _ -> },
    onDeleteBookmark: (Long) -> Unit = {},
    onDeleteBookmarkByUrl: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var urlInput by remember { mutableStateOf(initialUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showDirectDownloadDialog by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }
    var latestSniffedMedia by remember { mutableStateOf<DetectedMedia?>(null) }
    val isWorkerScanning by MediaDetectionHub.isWorkerRunning.collectAsState()

    val isCurrentBookmarked = remember(urlInput, bookmarks) {
        val clean = urlInput.trim().removeSuffix("/").lowercase()
        clean.isNotBlank() && bookmarks.any { it.url.trim().removeSuffix("/").lowercase() == clean }
    }
    val currentBookmarkItem = remember(urlInput, bookmarks) {
        val clean = urlInput.trim().removeSuffix("/").lowercase()
        bookmarks.firstOrNull { it.url.trim().removeSuffix("/").lowercase() == clean }
    }

    val handleDetectedMedia: (DetectedMedia) -> Unit = { media ->
        latestSniffedMedia = media
        onMediaDetected(media)
    }

    LaunchedEffect(latestSniffedMedia) {
        if (latestSniffedMedia != null) {
            delay(7000)
            latestSniffedMedia = null
        }
    }

    LaunchedEffect(initialUrl) {
        if (initialUrl != urlInput && initialUrl.isNotBlank()) {
            urlInput = initialUrl
            try {
                webViewRef?.loadUrl(initialUrl)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(deepScanTrigger) {
        if (deepScanTrigger > 0L) {
            try {
                webViewRef?.evaluateJavascript(MediaSnifferEngine.generateSnifferScript(), null)
            } catch (_: Exception) {}
        }
    }

    BackHandler(enabled = canGoBack) {
        try {
            if (webViewRef?.canGoBack() == true) {
                webViewRef?.goBack()
            }
        } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("browser_view")
    ) {
        // Top Browser Address Bar & Actions
        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.size(36.dp).testTag("browser_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { if (webViewRef?.canGoForward() == true) webViewRef?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.size(36.dp).testTag("browser_forward_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            val homeUrl = "https://duckduckgo.com"
                            urlInput = homeUrl
                            webViewRef?.loadUrl(homeUrl)
                        },
                        modifier = Modifier.size(36.dp).testTag("browser_home_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // URL Input Box
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("browser_url_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = if (urlInput.startsWith("https")) Icons.Default.Lock else Icons.Default.Search,
                                contentDescription = null,
                                tint = if (urlInput.startsWith("https")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            if (urlInput.isNotEmpty()) {
                                IconButton(onClick = { urlInput = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                focusManager.clearFocus()
                                val formatted = formatUrl(urlInput)
                                urlInput = formatted
                                webViewRef?.loadUrl(formatted)
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            webViewRef?.reload()
                        },
                        modifier = Modifier.size(36.dp).testTag("browser_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { onToggleDesktopMode() },
                        modifier = Modifier.size(36.dp).testTag("browser_desktop_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DesktopWindows,
                            contentDescription = "Desktop View",
                            tint = if (isDesktopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Dedicated Media Sniffer Action Button with Live Counter Badge
                    IconButton(
                        onClick = onOpenMediaSniffer,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("browser_sniffer_action_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (detectedMediaList.isNotEmpty()) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text(
                                            text = "${detectedMediaList.size}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartDisplay,
                                contentDescription = "Media Sniffer",
                                tint = if (detectedMediaList.isNotEmpty()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Bookmark Page Action Button
                    IconButton(
                        onClick = { showBookmarkDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("browser_bookmark_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isCurrentBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isCurrentBookmarked) "Edit Bookmark" else "Bookmark Page",
                            tint = if (isCurrentBookmarked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Open Saved Bookmarks Hub Sheet
                    IconButton(
                        onClick = { showBookmarksSheet = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("browser_bookmarks_hub_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Saved Bookmarks",
                            tint = if (bookmarks.isNotEmpty()) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Quick Media Stream & Saved Bookmarks Bar
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Quick "Saved Bookmarks" button chip
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .clickable { showBookmarksSheet = true }
                                .testTag("quick_bar_bookmarks_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Saved (${bookmarks.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // User's Real Saved Bookmarks from Room (pinned first)
                    items(bookmarks.sortedByDescending { it.isPinned }.take(8), key = { it.id }) { bm ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (bm.isPinned) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clickable {
                                    focusManager.clearFocus()
                                    urlInput = bm.url
                                    webViewRef?.loadUrl(bm.url)
                                }
                                .testTag("browser_quick_bookmark_${bm.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (bm.isPinned) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                                Text(
                                    text = bm.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    val presets = listOf(
                        Triple("🎬 Big Buck Bunny (MP4)", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", MediaType.VIDEO),
                        Triple("📡 Tears of Steel (HLS)", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8", MediaType.VIDEO),
                        Triple("🎵 Sample Music (MP3)", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", MediaType.AUDIO),
                        Triple("🏛️ Archive.org Movies", "https://archive.org/details/movies", MediaType.VIDEO),
                        Triple("🖼️ Wikimedia Commons", "https://commons.wikimedia.org/wiki/Main_Page", MediaType.IMAGE),
                        Triple("🔍 DuckDuckGo", "https://duckduckgo.com", MediaType.OTHER)
                    )
                    items(presets) { (label, url, _) ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clickable {
                                    focusManager.clearFocus()
                                    urlInput = url
                                    webViewRef?.loadUrl(url)
                                }
                                .testTag("preset_${label.take(8).trim()}")
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Loading Progress Bar
        if (pageProgress in 1..99) {
            LinearProgressIndicator(
                progress = { pageProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }

        // Web Content & Floating Actions
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    com.example.data.util.ChromiumCacheHelper.prepareDirectories(ctx)

                    WebView(ctx).apply {
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            settings.safeBrowsingEnabled = false
                        }

                        if (isDesktopMode) {
                            settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                        }

                        // Register Media Sniffer JavaScript Bridges
                        val bridge = MediaJsBridge { media ->
                            handleDetectedMedia(media)
                        }
                        addJavascriptInterface(bridge, "MediaSnifferBridge")
                        addJavascriptInterface(bridge, "MediaBridge")

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress
                                val t = view?.title ?: ""
                                if (t.isNotBlank()) pageTitle = t
                                onUrlChanged(view?.url ?: "", t, newProgress)
                                if (newProgress > 60) {
                                    view?.evaluateJavascript(MediaSnifferEngine.generateSnifferScript(), null)
                                }
                            }
                        }

                        setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, contentLength ->
                            MediaBrowserInterceptorService.getInstance(ctx).processUrl(
                                url = downloadUrl,
                                pageTitle = title ?: "Download",
                                mimeType = mimetype,
                                forceQueue = true
                            )
                            MediaDetectionHub.scheduleLinkIntercept(
                                context = ctx,
                                linkUrl = downloadUrl,
                                pageTitle = title ?: "Download",
                                userAgent = userAgent
                            )
                            Toast.makeText(ctx, "Media3 interceptor queued background download...", Toast.LENGTH_SHORT).show()
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                try {
                                    com.example.data.util.ChromiumCacheHelper.prepareDirectories(ctx)
                                    view?.let {
                                        (it.parent as? ViewGroup)?.removeView(it)
                                        it.destroy()
                                    }
                                    webViewRef = null
                                } catch (_: Exception) {}
                                return true
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val reqUrl = request?.url?.toString() ?: return false
                                if (isDirectDownloadOrMediaLink(reqUrl)) {
                                    MediaBrowserInterceptorService.getInstance(ctx).processUrl(
                                        url = reqUrl,
                                        pageTitle = view?.title ?: "Download",
                                        forceQueue = true
                                    )
                                    MediaDetectionHub.scheduleLinkIntercept(
                                        context = ctx,
                                        linkUrl = reqUrl,
                                        pageTitle = view?.title ?: "Download",
                                        userAgent = view?.settings?.userAgentString
                                    )
                                    Toast.makeText(ctx, "Media3 intercepting media stream...", Toast.LENGTH_SHORT).show()
                                    return true
                                }
                                return super.shouldOverrideUrlLoading(view, request)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                url?.let {
                                    urlInput = it
                                    if (!it.startsWith("data:") && !it.startsWith("blob:") && !it.startsWith("about:")) {
                                        MediaDetectionHub.schedulePageScan(
                                            context = ctx,
                                            pageUrl = it,
                                            pageTitle = view?.title ?: "",
                                            userAgent = view?.settings?.userAgentString
                                        )
                                    }
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                // Inject media sniffer script into the newly finished page
                                view?.evaluateJavascript(MediaSnifferEngine.generateSnifferScript(), null)
                                url?.let {
                                    if (!it.startsWith("data:") && !it.startsWith("blob:") && !it.startsWith("about:")) {
                                        MediaDetectionHub.schedulePageScan(
                                            context = ctx,
                                            pageUrl = it,
                                            pageTitle = view?.title ?: "",
                                            userAgent = view?.settings?.userAgentString
                                        )
                                    }
                                }
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                request?.let { req ->
                                    val reqUrl = req.url.toString()
                                    // Process via MediaBrowserInterceptorService (detects video/audio & auto-queues to Media3)
                                    val intercepted = MediaBrowserInterceptorService.getInstance(ctx).processUrl(
                                        url = reqUrl,
                                        headers = req.requestHeaders,
                                        pageTitle = view?.title
                                    )
                                    if (intercepted != null) {
                                        Handler(Looper.getMainLooper()).post {
                                            handleDetectedMedia(intercepted)
                                        }
                                    } else {
                                        val sniffed = MediaSnifferEngine.sniffUrl(
                                            rawUrl = reqUrl,
                                            pageTitle = view?.title,
                                            requestHeaders = req.requestHeaders
                                        )
                                        sniffed?.let { media ->
                                            Handler(Looper.getMainLooper()).post {
                                                handleDetectedMedia(media)
                                            }
                                        }
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        loadUrl(initialUrl)
                        webViewRef = this
                    }
                },
                update = { webView ->
                    webViewRef = webView
                    if (isDesktopMode) {
                        webView.settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                    } else {
                        webView.settings.userAgentString = null
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Real-time Sniffed Media Live Banner with 1-Tap Download
            androidx.compose.animation.AnimatedVisibility(
                visible = latestSniffedMedia != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                latestSniffedMedia?.let { sniffed ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                            .testTag("sniffed_media_toast")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (sniffed.mediaType) {
                                    MediaType.VIDEO -> Icons.Default.Movie
                                    MediaType.AUDIO -> Icons.Default.Audiotrack
                                    MediaType.IMAGE -> Icons.Default.Image
                                    else -> Icons.Default.SmartDisplay
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sniffed: ${sniffed.title}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${sniffed.quality} • ${sniffed.extension.uppercase()} • ${sniffed.estimatedSize}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }

                            // 1-Tap Instant Download
                            FilledTonalButton(
                                onClick = {
                                    if (onDirectDownloadMedia != null) {
                                        onDirectDownloadMedia(sniffed)
                                    } else {
                                        onQuickDirectDownload(sniffed.url, sniffed.title)
                                    }
                                    latestSniffedMedia = null
                                    Toast.makeText(context, "Started downloading ${sniffed.title}", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("toast_download_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Dismiss Button
                            IconButton(
                                onClick = { latestSniffedMedia = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Floating "Media Detected" Pill at bottom right
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 20.dp)
            ) {
                AnimatedVisibility(
                    visible = detectedMediaList.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    ElevatedButton(
                        onClick = onOpenMediaSniffer,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .shadow(8.dp, RoundedCornerShape(28.dp))
                            .testTag("floating_sniffer_badge")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartDisplay,
                            contentDescription = "Detected Media",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${detectedMediaList.size} Media Found",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Quick Actions at bottom left: "Scan Page" & "Direct URL"
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Scan Page Now button
                FilledTonalButton(
                    onClick = {
                        onRequestPageScan()
                        try {
                            webViewRef?.evaluateJavascript(MediaSnifferEngine.generateSnifferScript(), null)
                        } catch (_: Exception) {}
                    },
                    shape = CircleShape,
                    modifier = Modifier.testTag("quick_scan_page_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan Page",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Scan Page", fontSize = 12.sp)
                }

                // Direct URL download button
                FilledTonalButton(
                    onClick = { showDirectDownloadDialog = true },
                    shape = CircleShape,
                    modifier = Modifier.testTag("quick_direct_download_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Direct URL download",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Direct URL", fontSize = 12.sp)
                }

                if (isWorkerScanning) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "WorkManager Sniffing",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDirectDownloadDialog) {
        DirectDownloadDialog(
            onDismiss = { showDirectDownloadDialog = false },
            onConfirm = { url, title ->
                showDirectDownloadDialog = false
                onQuickDirectDownload(url, title)
            }
        )
    }

    if (showBookmarkDialog) {
        AddEditBookmarkDialog(
            initialTitle = currentBookmarkItem?.title ?: pageTitle.ifBlank { "Media Site" },
            initialUrl = urlInput,
            isAlreadyBookmarked = isCurrentBookmarked,
            initialCategory = currentBookmarkItem?.category ?: "Video Portals",
            initialIsPinned = currentBookmarkItem?.isPinned ?: true,
            onDismiss = { showBookmarkDialog = false },
            onSave = { title, url, category, isPinned ->
                onSaveBookmark(title, url, category, isPinned)
                showBookmarkDialog = false
                Toast.makeText(context, "Saved bookmark: $title", Toast.LENGTH_SHORT).show()
            },
            onDelete = if (isCurrentBookmarked) {
                {
                    if (currentBookmarkItem != null) {
                        onDeleteBookmark(currentBookmarkItem.id)
                    } else {
                        onDeleteBookmarkByUrl(urlInput)
                    }
                    showBookmarkDialog = false
                    Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
                }
            } else null
        )
    }

    if (showBookmarksSheet) {
        BrowserBookmarksBottomSheet(
            bookmarks = bookmarks,
            currentUrl = urlInput,
            currentPageTitle = pageTitle,
            onDismiss = { showBookmarksSheet = false },
            onSelectBookmark = { bm ->
                focusManager.clearFocus()
                urlInput = bm.url
                webViewRef?.loadUrl(bm.url)
            },
            onDeleteBookmark = onDeleteBookmark,
            onAddCurrentPage = {
                showBookmarksSheet = false
                showBookmarkDialog = true
            }
        )
    }
}

@Composable
fun DirectDownloadDialog(
    onDismiss: () -> Unit,
    onConfirm: (url: String, title: String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download Media from URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter direct link to video, audio or media stream:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Media Stream URL") },
                    singleLine = true,
                    placeholder = { Text("https://example.com/video.mp4") },
                    modifier = Modifier.fillMaxWidth().testTag("direct_url_input")
                )
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("File Name / Title (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("direct_title_input")
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    if (urlText.isNotBlank()) {
                        onConfirm(urlText.trim(), titleText.trim())
                    }
                },
                enabled = urlText.isNotBlank(),
                modifier = Modifier.testTag("btn_direct_download_confirm")
            ) {
                Text("Download Now")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private class MediaJsBridge(val callback: (DetectedMedia) -> Unit) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun reportMedia(url: String, type: String, title: String, quality: String = "HD") {
        mainHandler.post {
            val sniffed = MediaSnifferEngine.sniffUrl(url, title)
            val media = sniffed ?: DetectedMedia(
                url = url,
                title = title.ifBlank { "Media_${System.currentTimeMillis() % 10000}" },
                mimeType = if (type == "audio") "audio/mpeg" else "video/mp4",
                extension = url.substringBefore('?').substringAfterLast('.', if (type == "audio") "mp3" else "mp4"),
                mediaType = when (type.lowercase()) {
                    "audio" -> MediaType.AUDIO
                    "image" -> MediaType.IMAGE
                    else -> MediaType.VIDEO
                },
                quality = quality
            )
            callback(media)
        }
    }

    @JavascriptInterface
    fun reportMediaBatch(json: String) {
        mainHandler.post {
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val src = obj.optString("src", "")
                    val type = obj.optString("type", "video")
                    val title = obj.optString("title", "Media Clip")
                    val quality = obj.optString("quality", "HD")
                    if (src.isNotEmpty()) {
                        reportMedia(src, type, title, quality)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}

private fun formatUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    if (trimmed.contains(".") && !trimmed.contains(" ")) return "https://$trimmed"
    return "https://duckduckgo.com/?q=${trimmed.replace(" ", "+")}"
}

private fun isDirectDownloadOrMediaLink(url: String): Boolean {
    val clean = url.lowercase(java.util.Locale.ROOT).substringBefore('?').substringBefore('#')
    val ext = clean.substringAfterLast('.', "")
    val downloadExtensions = setOf(
        "mp4", "webm", "mkv", "mov", "m4v", "m3u8", "mpd", "ts", "3gp",
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus",
        "zip", "rar", "7z", "apk", "pdf", "tar", "gz"
    )
    return downloadExtensions.contains(ext)
}
