package com.example.data.util

import android.net.Uri
import com.example.data.model.DetectedMedia
import com.example.data.model.MediaType
import java.util.Locale

object MediaSnifferEngine {

    // Supported Video Formats & Extensions
    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "webm", "m3u8", "mpd", "mkv", "mov",
        "avi", "flv", "wmv", "ts", "3gp", "ogv", "f4v"
    )

    // Supported Audio Formats & Extensions
    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "mka"
    )

    // Supported Image Formats & Extensions
    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "svg", "bmp"
    )

    /**
     * Inspects a candidate URL and headers to determine if it is a media stream or file.
     * Returns a structured DetectedMedia object or null if not media.
     */
    fun sniffUrl(
        rawUrl: String,
        pageTitle: String? = null,
        requestHeaders: Map<String, String>? = null
    ): DetectedMedia? {
        if (rawUrl.isBlank() || rawUrl.length > 2500) return null
        if (rawUrl.startsWith("data:") || rawUrl.startsWith("blob:") || rawUrl.startsWith("javascript:")) {
            return null
        }

        val urlLower = rawUrl.lowercase(Locale.ROOT)

        // Ignore common web tracking, analytics, and static web scripts
        if (isIgnoredUrl(urlLower)) return null

        val uri = try {
            Uri.parse(rawUrl)
        } catch (_: Exception) {
            return null
        }

        val path = uri.path?.lowercase(Locale.ROOT) ?: ""
        val extensionFromPath = path.substringAfterLast('.', "")
        val query = uri.query?.lowercase(Locale.ROOT) ?: ""

        // Check content-type from headers if provided
        val contentTypeHeader = requestHeaders?.entries?.firstOrNull {
            it.key.equals("Content-Type", ignoreCase = true) || it.key.equals("Accept", ignoreCase = true)
        }?.value?.lowercase(Locale.ROOT) ?: ""

        val isVideo: Boolean
        val isAudio: Boolean
        val isImage: Boolean
        var detectedExt = ""
        var detectedMime = ""

        when {
            // Explicit video extension in path
            VIDEO_EXTENSIONS.contains(extensionFromPath) -> {
                isVideo = true
                isAudio = false
                isImage = false
                detectedExt = extensionFromPath
                detectedMime = getMimeForExtension(detectedExt, "video")
            }
            // Explicit audio extension in path
            AUDIO_EXTENSIONS.contains(extensionFromPath) -> {
                isVideo = false
                isAudio = true
                isImage = false
                detectedExt = extensionFromPath
                detectedMime = getMimeForExtension(detectedExt, "audio")
            }
            // Explicit image extension in path
            IMAGE_EXTENSIONS.contains(extensionFromPath) -> {
                isVideo = false
                isAudio = false
                isImage = true
                detectedExt = extensionFromPath
                detectedMime = getMimeForExtension(detectedExt, "image")
            }
            // Content-Type header indication
            contentTypeHeader.startsWith("video/") -> {
                isVideo = true
                isAudio = false
                isImage = false
                detectedExt = contentTypeHeader.substringAfter("video/").substringBefore(";").trim()
                if (detectedExt.isBlank() || detectedExt.length > 5) detectedExt = "mp4"
                detectedMime = "video/$detectedExt"
            }
            contentTypeHeader.startsWith("audio/") -> {
                isVideo = false
                isAudio = true
                isImage = false
                detectedExt = contentTypeHeader.substringAfter("audio/").substringBefore(";").trim()
                if (detectedExt.isBlank() || detectedExt.length > 5) detectedExt = "mp3"
                detectedMime = "audio/$detectedExt"
            }
            // Common video stream query parameters and paths
            urlLower.contains(".m3u8") || urlLower.contains("format=m3u8") || urlLower.contains("mime=application%2fundo.apple.mpegurl") -> {
                isVideo = true
                isAudio = false
                isImage = false
                detectedExt = "m3u8"
                detectedMime = "application/x-mpegURL"
            }
            urlLower.contains(".mpd") || urlLower.contains("manifest=mpd") -> {
                isVideo = true
                isAudio = false
                isImage = false
                detectedExt = "mpd"
                detectedMime = "application/dash+xml"
            }
            urlLower.contains("videoplayback") || urlLower.contains("mime=video") || urlLower.contains("type=video") -> {
                isVideo = true
                isAudio = false
                isImage = false
                detectedExt = when {
                    urlLower.contains("webm") -> "webm"
                    urlLower.contains("m3u8") -> "m3u8"
                    else -> "mp4"
                }
                detectedMime = "video/$detectedExt"
            }
            urlLower.contains("mime=audio") || urlLower.contains("type=audio") -> {
                isVideo = false
                isAudio = true
                isImage = false
                detectedExt = "mp3"
                detectedMime = "audio/mpeg"
            }
            else -> {
                return null
            }
        }

        val mediaType = when {
            isVideo -> MediaType.VIDEO
            isAudio -> MediaType.AUDIO
            isImage -> MediaType.IMAGE
            else -> MediaType.OTHER
        }

        val quality = detectQuality(urlLower, mediaType)
        val title = generateCleanTitle(rawUrl, path, pageTitle, mediaType, detectedExt)
        val estimatedSize = estimateSizeDescription(mediaType, quality, detectedExt)

        return DetectedMedia(
            url = rawUrl,
            title = title,
            mimeType = detectedMime,
            extension = detectedExt,
            estimatedSize = estimatedSize,
            mediaType = mediaType,
            quality = quality,
            sourcePageTitle = pageTitle ?: ""
        )
    }

    private fun isIgnoredUrl(url: String): Boolean {
        val cleanUrl = url.substringBefore('?')
        return url.contains("google-analytics") ||
                url.contains("doubleclick.net") ||
                url.contains("facebook.com/tr") ||
                url.contains("analytics") ||
                url.contains("tracking") ||
                url.contains("favicon.ico") ||
                cleanUrl.endsWith(".css") ||
                (cleanUrl.endsWith(".js") && !url.contains(".m3u8") && !url.contains(".mp4") && !url.contains(".mp3") && !url.contains("videoplayback")) ||
                url.contains("adservice")
    }

    private fun getMimeForExtension(ext: String, category: String): String {
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "m3u8" -> "application/x-mpegURL"
            "mpd" -> "application/dash+xml"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "ts" -> "video/mp2t"
            "flv" -> "video/x-flv"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg", "opus" -> "audio/ogg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            else -> "$category/$ext"
        }
    }

    /**
     * Extracts quality hints (1080p, 720p, 4K, 320kbps, etc.) from the URL.
     */
    private fun detectQuality(urlLower: String, mediaType: MediaType): String {
        if (mediaType == MediaType.VIDEO) {
            return when {
                urlLower.contains("2160p") || urlLower.contains("4k") || urlLower.contains("uhd") -> "4K Ultra HD"
                urlLower.contains("1440p") || urlLower.contains("2k") || urlLower.contains("qhd") -> "2K QHD"
                urlLower.contains("1080p") || urlLower.contains("fhd") || urlLower.contains("1920x1080") -> "1080p Full HD"
                urlLower.contains("720p") || urlLower.contains("1280x720") || urlLower.contains("hd") -> "720p HD"
                urlLower.contains("480p") || urlLower.contains("854x480") -> "480p SD"
                urlLower.contains("360p") || urlLower.contains("640x360") -> "360p"
                urlLower.contains("240p") -> "240p"
                urlLower.contains("m3u8") || urlLower.contains("playlist") -> "Adaptive HLS Stream"
                urlLower.contains("mpd") -> "DASH Stream"
                else -> "Standard Quality"
            }
        } else if (mediaType == MediaType.AUDIO) {
            return when {
                urlLower.contains("flac") || urlLower.contains("lossless") -> "Hi-Res Lossless"
                urlLower.contains("320k") || urlLower.contains("320kbps") -> "320 kbps (High)"
                urlLower.contains("256k") || urlLower.contains("256kbps") -> "256 kbps"
                urlLower.contains("192k") || urlLower.contains("192kbps") -> "192 kbps"
                urlLower.contains("128k") || urlLower.contains("128kbps") -> "128 kbps (Standard)"
                else -> "Stereo Audio"
            }
        }
        return "Original"
    }

    private fun estimateSizeDescription(mediaType: MediaType, quality: String, ext: String): String {
        return when (mediaType) {
            MediaType.VIDEO -> when {
                quality.contains("4K") -> "~150 - 500 MB"
                quality.contains("1080p") -> "~40 - 120 MB"
                quality.contains("720p") -> "~20 - 60 MB"
                quality.contains("480p") -> "~10 - 30 MB"
                ext == "m3u8" || ext == "mpd" -> "Live Stream"
                else -> "Direct Stream"
            }
            MediaType.AUDIO -> when {
                quality.contains("Hi-Res") -> "~25 - 50 MB"
                quality.contains("320") -> "~8 - 15 MB"
                else -> "~3 - 8 MB"
            }
            MediaType.IMAGE -> "High-Res Image"
            MediaType.OTHER -> "Media Stream"
        }
    }

    private fun generateCleanTitle(
        url: String,
        path: String,
        pageTitle: String?,
        mediaType: MediaType,
        ext: String
    ): String {
        val lastSegment = path.substringAfterLast('/').substringBeforeLast('.')
        val isGenericSegment = lastSegment.isBlank() ||
                lastSegment.length <= 3 ||
                lastSegment.all { it.isDigit() } ||
                lastSegment.matches(Regex("[0-9a-fA-F-]{8,}")) ||
                lastSegment.contains("videoplayback") ||
                lastSegment.contains("stream") ||
                lastSegment.contains("index") ||
                lastSegment.contains("master") ||
                lastSegment.contains("playlist") ||
                lastSegment.contains("manifest")

        if (!pageTitle.isNullOrBlank() && (isGenericSegment || lastSegment.length < 5)) {
            val cleanPageTitle = pageTitle
                .replace(Regex("[|\\-–—•].*$"), "")
                .trim()
                .replace(Regex("[^a-zA-Z0-9 _-]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleanPageTitle.isNotBlank()) {
                val suffix = when (mediaType) {
                    MediaType.VIDEO -> "Video"
                    MediaType.AUDIO -> "Audio"
                    MediaType.IMAGE -> "Image"
                    MediaType.OTHER -> "Media"
                }
                return "$cleanPageTitle $suffix"
            }
        }

        if (!isGenericSegment) {
            val cleaned = lastSegment
                .replace(Regex("[_\\-+]"), " ")
                .replace(Regex("[^a-zA-Z0-9 ]"), "")
                .trim()
                .split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

            if (cleaned.length in 4..50) {
                return cleaned
            }
        }

        val typeName = when (mediaType) {
            MediaType.VIDEO -> "Stream Video"
            MediaType.AUDIO -> "Audio Track"
            MediaType.IMAGE -> "Captured Image"
            MediaType.OTHER -> "Media File"
        }
        val timestampShort = (System.currentTimeMillis() % 10000)
        return "$typeName ($timestampShort).$ext"
    }

    /**
     * Generates a comprehensive JavaScript injection script that runs inside the WebView.
     * Hooks HTML5 media elements, intercepts dynamic mutations, monitors network fetch/XHR,
     * and reports detected media to the Android MediaSnifferBridge.
     */
    fun generateSnifferScript(sniffImages: Boolean = true): String {
        return """
        (function() {
            if (window.__mediaSnifferInstalled) {
                if (typeof window.__mediaSnifferScan === 'function') {
                    window.__mediaSnifferScan();
                }
                return;
            }
            window.__mediaSnifferInstalled = true;

            var reportedUrls = new Set();

            function reportSingle(src, type, title, quality) {
                if (!src || typeof src !== 'string') return;
                src = src.trim();
                if (!src.startsWith('http://') && !src.startsWith('https://')) {
                    if (src.startsWith('//')) {
                        src = window.location.protocol + src;
                    } else if (src.startsWith('/')) {
                        src = window.location.origin + src;
                    } else {
                        return;
                    }
                }
                if (reportedUrls.has(src)) return;
                reportedUrls.add(src);

                var item = {
                    src: src,
                    type: type || 'video',
                    title: title || document.title || 'Page Media',
                    quality: quality || 'HD'
                };

                if (window.MediaSnifferBridge && typeof window.MediaSnifferBridge.reportMedia === 'function') {
                    window.MediaSnifferBridge.reportMedia(item.src, item.type, item.title, item.quality);
                } else if (window.MediaBridge && typeof window.MediaBridge.reportMedia === 'function') {
                    window.MediaBridge.reportMedia(item.src, item.type, item.title);
                }
            }

            function scanDOM() {
                try {
                    var pageTitle = document.title || 'Media';
                    var ogVideo = document.querySelector('meta[property="og:video"], meta[property="og:video:url"]');
                    if (ogVideo && ogVideo.content) {
                        reportSingle(ogVideo.content, 'video', pageTitle + ' (Featured)', '1080p');
                    }
                    var ogAudio = document.querySelector('meta[property="og:audio"], meta[property="og:audio:url"]');
                    if (ogAudio && ogAudio.content) {
                        reportSingle(ogAudio.content, 'audio', pageTitle + ' (Audio)', '320kbps');
                    }

                    // 1. Scan Video Elements
                    var videos = document.getElementsByTagName('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        var src = v.currentSrc || v.src;
                        var title = v.getAttribute('title') || v.getAttribute('aria-label') || pageTitle + ' Video ' + (i + 1);
                        if (src) reportSingle(src, 'video', title, 'HD');

                        var sources = v.getElementsByTagName('source');
                        for (var j = 0; j < sources.length; j++) {
                            var s = sources[j];
                            if (s.src) {
                                var sTitle = s.getAttribute('title') || title + ' Source ' + (j + 1);
                                reportSingle(s.src, 'video', sTitle, 'HD');
                            }
                        }
                    }

                    // 2. Scan Audio Elements
                    var audios = document.getElementsByTagName('audio');
                    for (var i = 0; i < audios.length; i++) {
                        var a = audios[i];
                        var src = a.currentSrc || a.src;
                        var title = a.getAttribute('title') || pageTitle + ' Audio ' + (i + 1);
                        if (src) reportSingle(src, 'audio', title, 'Stereo');

                        var sources = a.getElementsByTagName('source');
                        for (var j = 0; j < sources.length; j++) {
                            if (sources[j].src) reportSingle(sources[j].src, 'audio', title, 'Stereo');
                        }
                    }

                    // 3. Scan Anchors with direct media links
                    var links = document.getElementsByTagName('a');
                    for (var i = 0; i < links.length; i++) {
                        var href = links[i].href;
                        if (href && /\.(mp4|webm|m3u8|mp3|m4a|flac|wav)($|\?)/i.test(href)) {
                            var isAud = /\.(mp3|m4a|flac|wav)($|\?)/i.test(href);
                            var linkText = links[i].innerText.trim() || pageTitle;
                            reportSingle(href, isAud ? 'audio' : 'video', linkText, 'Direct');
                        }
                    }

                    // 4. Scan High-Res Images if enabled
                    ${if (sniffImages) """
                    var imgs = document.getElementsByTagName('img');
                    for (var i = 0; i < imgs.length; i++) {
                        var img = imgs[i];
                        var src = img.currentSrc || img.src;
                        if (src && (img.naturalWidth > 300 || img.width > 300 || img.hasAttribute('srcset'))) {
                            if (/\.(jpg|jpeg|png|webp|gif)($|\?)/i.test(src) || src.includes('image')) {
                                var alt = img.alt || pageTitle + ' Image ' + (i + 1);
                                reportSingle(src, 'image', alt, 'High-Res');
                            }
                        }
                    }
                    """ else ""}

                } catch (e) {
                    console.error('DOM media scan error:', e);
                }
            }

            window.__mediaSnifferScan = scanDOM;

            // Hook HTMLMediaElement play & load
            try {
                var origPlay = HTMLMediaElement.prototype.play;
                HTMLMediaElement.prototype.play = function() {
                    var src = this.currentSrc || this.src;
                    var type = (this.tagName.toLowerCase() === 'audio') ? 'audio' : 'video';
                    reportSingle(src, type, document.title + ' Playing', 'Active Stream');
                    return origPlay.apply(this, arguments);
                };

                var origLoad = HTMLMediaElement.prototype.load;
                HTMLMediaElement.prototype.load = function() {
                    var src = this.currentSrc || this.src;
                    var type = (this.tagName.toLowerCase() === 'audio') ? 'audio' : 'video';
                    reportSingle(src, type, document.title, 'Loaded');
                    return origLoad.apply(this, arguments);
                };
            } catch (e) {}

            // Hook fetch for media manifests & video requests
            try {
                var origFetch = window.fetch;
                window.fetch = function() {
                    var url = arguments[0];
                    if (typeof url === 'string') {
                        if (/\.(m3u8|mpd|mp4|webm|mp3|m4a)($|\?)/i.test(url) || url.includes('videoplayback')) {
                            var isAud = /\.(mp3|m4a)($|\?)/i.test(url);
                            reportSingle(url, isAud ? 'audio' : 'video', document.title, 'Network Stream');
                        }
                    }
                    return origFetch.apply(this, arguments);
                };
            } catch (e) {}

            // Hook XMLHttpRequest for media requests
            try {
                var origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    if (typeof url === 'string') {
                        if (/\.(m3u8|mpd|mp4|webm|mp3|m4a)($|\?)/i.test(url) || url.includes('videoplayback')) {
                            var isAud = /\.(mp3|m4a)($|\?)/i.test(url);
                            reportSingle(url, isAud ? 'audio' : 'video', document.title, 'XHR Stream');
                        }
                    }
                    return origOpen.apply(this, arguments);
                };
            } catch (e) {}

            // MutationObserver to auto-detect elements added after initial load
            try {
                var observer = new MutationObserver(function(mutations) {
                    var shouldScan = false;
                    for (var i = 0; i < mutations.length; i++) {
                        var added = mutations[i].addedNodes;
                        for (var j = 0; j < added.length; j++) {
                            var node = added[j];
                            if (node.nodeType === 1) { // ELEMENT_NODE
                                var tag = node.tagName.toLowerCase();
                                if (tag === 'video' || tag === 'audio' || tag === 'source' || tag === 'iframe') {
                                    shouldScan = true;
                                    break;
                                }
                            }
                        }
                        if (shouldScan) break;
                    }
                    if (shouldScan) {
                        scanDOM();
                    }
                });
                observer.observe(document.documentElement || document.body, { childList: true, subtree: true });
            } catch (e) {}

            // Initial DOM scan
            scanDOM();
            setTimeout(scanDOM, 1000);
            setTimeout(scanDOM, 3000);
        })();
        """.trimIndent()
    }
}
