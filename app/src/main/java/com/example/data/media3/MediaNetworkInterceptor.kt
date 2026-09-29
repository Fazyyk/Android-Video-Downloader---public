package com.example.data.media3

import android.net.Uri
import android.util.Log
import com.example.data.model.DetectedMedia
import com.example.data.model.MediaType
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale
import java.util.UUID

/**
 * Network interceptor that detects media URLs (video/audio) from HTTP requests & responses,
 * extracting metadata and forwarding to background queue handlers.
 */
class MediaNetworkInterceptor(
    private val onMediaDetected: ((DetectedMedia) -> Unit)? = null,
    private val autoQueueCallback: ((url: String, title: String, mimeType: String, category: String) -> Unit)? = null
) : Interceptor {

    companion object {
        private const val TAG = "MediaNetworkInterceptor"

        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "m4v", "mkv", "webm", "mov", "avi", "flv", "wmv", "3gp", "ts", "m3u8", "mpd"
        )

        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "opus", "wma"
        )

        /**
         * Checks if a URL matches known video/audio extensions or query parameters.
         */
        fun isMediaUrl(url: String, headers: Map<String, String>? = null): Boolean {
            if (url.isBlank() || url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("about:")) {
                return false
            }

            // Check Content-Type header if provided
            val contentType = headers?.entries?.find { it.key.equals("Content-Type", ignoreCase = true) }?.value?.lowercase(Locale.ROOT)
            if (contentType != null) {
                if (contentType.startsWith("video/") || contentType.startsWith("audio/") ||
                    contentType.contains("application/x-mpegurl") || contentType.contains("application/vnd.apple.mpegurl") ||
                    contentType.contains("application/dash+xml")
                ) {
                    return true
                }
            }

            val cleanUrl = url.substringBefore('?').substringBefore('#')
            val extension = cleanUrl.substringAfterLast('.', "").lowercase(Locale.ROOT)

            if (VIDEO_EXTENSIONS.contains(extension) || AUDIO_EXTENSIONS.contains(extension)) {
                return true
            }

            // Query parameter inspection (e.g. ?format=mp4, &type=audio, etc.)
            val lowerUrl = url.lowercase(Locale.ROOT)
            if (lowerUrl.contains(".mp4?") || lowerUrl.contains(".m3u8?") || lowerUrl.contains(".mpd?") ||
                lowerUrl.contains(".mp3?") || lowerUrl.contains(".m4a?") || lowerUrl.contains("mime=video") ||
                lowerUrl.contains("mime=audio") || lowerUrl.contains("video/mp4")
            ) {
                return true
            }

            return false
        }

        /**
         * Determines category ("VIDEO" or "AUDIO") from url or mime type.
         */
        fun detectCategory(url: String, mimeType: String?): String {
            val lowerMime = mimeType?.lowercase(Locale.ROOT) ?: ""
            if (lowerMime.startsWith("audio/")) return "AUDIO"
            if (lowerMime.startsWith("video/") || lowerMime.contains("mpegurl") || lowerMime.contains("dash+xml")) return "VIDEO"

            val cleanUrl = url.substringBefore('?').substringBefore('#')
            val extension = cleanUrl.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return if (AUDIO_EXTENSIONS.contains(extension)) "AUDIO" else "VIDEO"
        }

        /**
         * Infers a user-friendly title from a URL and page title.
         */
        fun inferTitle(url: String, pageTitle: String? = null): String {
            if (!pageTitle.isNullOrBlank() && !pageTitle.startsWith("http", ignoreCase = true) && pageTitle.length > 2) {
                val cleanTitle = pageTitle.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                if (cleanTitle.length <= 60) return cleanTitle
            }

            try {
                val uri = Uri.parse(url)
                val pathSegment = uri.lastPathSegment
                if (!pathSegment.isNullOrBlank()) {
                    val decoded = Uri.decode(pathSegment).substringBefore('?')
                    if (decoded.length in 3..60) {
                        return decoded.substringBeforeLast('.')
                    }
                }
            } catch (_: Exception) {}

            return "Media_" + UUID.randomUUID().toString().take(6)
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestUrl = request.url.toString()

        val response = chain.proceed(request)

        val contentType = response.header("Content-Type") ?: ""
        val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L

        if (isMediaUrl(requestUrl) || isMediaContentType(contentType)) {
            val category = detectCategory(requestUrl, contentType)
            val title = inferTitle(requestUrl)
            val cleanExt = requestUrl.substringBefore('?').substringAfterLast('.', if (category == "AUDIO") "mp3" else "mp4")

            val detected = DetectedMedia(
                url = requestUrl,
                title = title,
                mimeType = contentType.ifBlank { if (category == "AUDIO") "audio/mpeg" else "video/mp4" },
                extension = cleanExt,
                estimatedSize = if (contentLength > 0) formatBytes(contentLength) else "Direct Stream",
                mediaType = if (category == "AUDIO") MediaType.AUDIO else MediaType.VIDEO,
                quality = "HD",
                sourcePageTitle = ""
            )

            Log.d(TAG, "Intercepted network media stream: ${detected.title} ($category) - $requestUrl")
            onMediaDetected?.invoke(detected)
            autoQueueCallback?.invoke(detected.url, detected.title, detected.mimeType, category)
        }

        return response
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) "%.1f MB".format(mb) else "${bytes / 1024} KB"
    }

    private fun isMediaContentType(contentType: String): Boolean {
        val lower = contentType.lowercase(Locale.ROOT)
        return lower.startsWith("video/") || lower.startsWith("audio/") ||
                lower.contains("application/x-mpegurl") || lower.contains("application/vnd.apple.mpegurl") ||
                lower.contains("application/dash+xml")
    }
}
