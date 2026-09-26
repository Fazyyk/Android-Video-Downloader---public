package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import java.io.File
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.DetectedMedia
import com.example.data.model.MediaType
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserView(
    initialUrl: String,
    detectedMediaList: List<DetectedMedia>,
    isDesktopMode: Boolean,
    onUrlChanged: (String, String, Int) -> Unit,
    onMediaDetected: (DetectedMedia) -> Unit,
    onOpenMediaSniffer: () -> Unit,
    onQuickDirectDownload: (String, String) -> Unit,
    onToggleDesktopMode: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var urlInput by remember { mutableStateOf(initialUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showDirectDownloadDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialUrl) {
        if (initialUrl != urlInput && initialUrl.isNotBlank()) {
            urlInput = initialUrl
            try {
                webViewRef?.loadUrl(initialUrl)
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

        // Web Content & Floating Media Badge
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    try {
                        val webviewCache = File(ctx.cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
                        if (!webviewCache.exists()) {
                            webviewCache.mkdirs()
                        }
                    } catch (_: Exception) {}

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

                        addJavascriptInterface(
                            MediaJsBridge { url, type, title ->
                                val ext = url.substringBefore('?').substringAfterLast('.', "mp4")
                                val mediaType = when (type.lowercase()) {
                                    "video" -> MediaType.VIDEO
                                    "audio" -> MediaType.AUDIO
                                    "image" -> MediaType.IMAGE
                                    else -> MediaType.OTHER
                                }
                                onMediaDetected(
                                    DetectedMedia(
                                        url = url,
                                        title = title.ifBlank { "Media_${System.currentTimeMillis()}" },
                                        mimeType = if (type == "audio") "audio/mpeg" else "video/mp4",
                                        extension = ext,
                                        mediaType = mediaType
                                    )
                                )
                            },
                            "MediaBridge"
                        )

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress
                                onUrlChanged(view?.url ?: "", view?.title ?: "", newProgress)
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                try {
                                    view?.let {
                                        (it.parent as? ViewGroup)?.removeView(it)
                                        it.destroy()
                                    }
                                    webViewRef = null
                                } catch (_: Exception) {}
                                return true
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                url?.let { urlInput = it }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                injectMediaDetectionScript(view)
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                request?.url?.let { reqUri ->
                                    sniffMediaUrl(reqUri.toString(), onMediaDetected)
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

            // Floating "Media Detected" Glow Pill
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp)
            ) {
                androidx.compose.animation.AnimatedVisibility(
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Quick Direct URL download button at bottom left
            FilledTonalButton(
                onClick = { showDirectDownloadDialog = true },
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 24.dp)
                    .testTag("quick_direct_download_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Direct URL download",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Direct URL", fontSize = 12.sp)
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
                    text = "Enter direct link to video, audio or media file:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Media Stream URL") },
                    singleLine = true,
                    placeholder = { Text("https://example.com/video.mp4") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("File Name / Title (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                enabled = urlText.isNotBlank()
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

private class MediaJsBridge(val callback: (String, String, String) -> Unit) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun reportMedia(url: String, type: String, title: String) {
        mainHandler.post { callback(url, type, title) }
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
                    if (src.isNotEmpty()) {
                        callback(src, type, title)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}

private fun injectMediaDetectionScript(webView: WebView?) {
    val js = """
        (function() {
            try {
                var found = [];
                var title = document.title || 'Page Video';
                
                // Check HTML5 video elements
                var videos = document.getElementsByTagName('video');
                for (var i = 0; i < videos.length; i++) {
                    var v = videos[i];
                    var src = v.currentSrc || v.src;
                    if (src && src.startsWith('http')) {
                        found.push({src: src, type: 'video', title: title + ' ' + (i+1)});
                    }
                    var sources = v.getElementsByTagName('source');
                    for (var j = 0; j < sources.length; j++) {
                        var s = sources[j].src;
                        if (s && s.startsWith('http')) {
                            found.push({src: s, type: 'video', title: title + ' Source ' + (j+1)});
                        }
                    }
                }
                
                // Check HTML5 audio elements
                var audios = document.getElementsByTagName('audio');
                for (var i = 0; i < audios.length; i++) {
                    var a = audios[i];
                    var src = a.currentSrc || a.src;
                    if (src && src.startsWith('http')) {
                        found.push({src: src, type: 'audio', title: title + ' Audio ' + (i+1)});
                    }
                }
                
                if (found.length > 0 && window.MediaBridge) {
                    window.MediaBridge.reportMediaBatch(JSON.stringify(found));
                }
            } catch (e) {}
        })();
    """.trimIndent()
    webView?.evaluateJavascript(js, null)
}

private fun sniffMediaUrl(url: String, onMediaDetected: (DetectedMedia) -> Unit) {
    if (url.startsWith("data:") || url.startsWith("blob:") || url.length > 1000) return
    val clean = url.substringBefore('?').substringBefore('#').lowercase()
    val isVideo = clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".m3u8") ||
            clean.endsWith(".mkv") || clean.endsWith(".mov") || clean.endsWith(".ts")
    val isAudio = clean.endsWith(".mp3") || clean.endsWith(".m4a") || clean.endsWith(".aac") ||
            clean.endsWith(".wav") || clean.endsWith(".flac") || clean.endsWith(".ogg")

    if (isVideo || isAudio) {
        val ext = clean.substringAfterLast('.', if (isAudio) "mp3" else "mp4")
        val fileName = clean.substringAfterLast('/').substringBeforeLast('.')
        val title = fileName.replace(Regex("[^a-zA-Z0-9_-]"), " ").capitalizeWords().ifBlank {
            if (isVideo) "Detected Video Stream" else "Detected Audio Track"
        }
        Handler(Looper.getMainLooper()).post {
            onMediaDetected(
                DetectedMedia(
                    url = url,
                    title = title,
                    mimeType = if (isAudio) "audio/$ext" else "video/$ext",
                    extension = ext,
                    mediaType = if (isAudio) MediaType.AUDIO else MediaType.VIDEO
                )
            )
        }
    }
}

private fun formatUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    if (trimmed.contains(".") && !trimmed.contains(" ")) return "https://$trimmed"
    return "https://duckduckgo.com/?q=${trimmed.replace(" ", "+")}"
}

private fun String.capitalizeWords(): String =
    split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
