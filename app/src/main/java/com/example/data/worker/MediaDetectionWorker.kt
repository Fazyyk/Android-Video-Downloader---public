package com.example.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.model.DetectedMedia
import com.example.data.model.MediaType
import com.example.data.util.MediaSnifferEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MediaDetectionWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_PAGE_URL = "page_url"
        const val KEY_PAGE_TITLE = "page_title"
        const val KEY_USER_AGENT = "user_agent"
        const val KEY_CANDIDATE_URL = "candidate_url"
        const val KEY_TRIGGER_SOURCE = "trigger_source"

        const val CHANNEL_ID = "media_detection_channel"
        const val NOTIFICATION_ID_BASE = 8000
        private const val TAG = "MediaDetectionWorker"

        private val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        MediaDetectionHub.setWorkerRunning(true)
        createNotificationChannel()

        val candidateUrl = inputData.getString(KEY_CANDIDATE_URL)
        val pageUrl = inputData.getString(KEY_PAGE_URL)
        val pageTitle = inputData.getString(KEY_PAGE_TITLE) ?: "Web Page"
        val customUserAgent = inputData.getString(KEY_USER_AGENT)?.ifBlank { null } ?: DEFAULT_USER_AGENT
        val triggerSource = inputData.getString(KEY_TRIGGER_SOURCE) ?: "BROWSER_NAVIGATION"

        Log.d(TAG, "MediaDetectionWorker started for candidate: $candidateUrl, page: $pageUrl, source: $triggerSource")

        var foundCount = 0

        try {
            // Case 1: Intercepted a direct download or media candidate link
            if (!candidateUrl.isNullOrBlank()) {
                val detected = probeDirectCandidateUrl(candidateUrl, pageTitle, customUserAgent)
                if (detected != null) {
                    foundCount++
                    MediaDetectionHub.postDetectedMedia(detected)
                    showNotification(
                        title = "Media Download Intercepted",
                        message = "${detected.title} (${detected.estimatedSize})"
                    )
                }
            }

            // Case 2: User navigated to a page; scan page HTML in background for embedded media and download links
            if (!pageUrl.isNullOrBlank() && !pageUrl.startsWith("data:") && !pageUrl.startsWith("blob:")) {
                val pageDetectedItems = scanWebPageForMedia(pageUrl, pageTitle, customUserAgent)
                for (item in pageDetectedItems) {
                    foundCount++
                    MediaDetectionHub.postDetectedMedia(item)
                }

                if (pageDetectedItems.isNotEmpty()) {
                    showNotification(
                        title = "Media Detected on Page",
                        message = "Found ${pageDetectedItems.size} downloadable stream(s) on $pageTitle"
                    )
                }
            }

            Log.d(TAG, "MediaDetectionWorker finished successfully. Found $foundCount media links.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "MediaDetectionWorker error: ${e.message}", e)
            Result.failure()
        } finally {
            MediaDetectionHub.setWorkerRunning(false)
        }
    }

    /**
     * Probes an individual candidate URL via HTTP HEAD / Range GET to determine MIME type, Content-Length, and filename
     */
    private fun probeDirectCandidateUrl(
        urlStr: String,
        pageTitle: String,
        userAgent: String
    ): DetectedMedia? {
        // Fast pre-check with MediaSnifferEngine
        val precheck = MediaSnifferEngine.sniffUrl(urlStr, pageTitle)

        return try {
            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-1024")
                .head()
                .build()

            val response: Response = okHttpClient.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            val contentType = response.header("Content-Type")?.lowercase(Locale.ROOT) ?: ""
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
            val contentDisposition = response.header("Content-Disposition") ?: ""

            response.close()

            val isMedia = isMediaContentType(contentType) || precheck != null

            if (isMedia) {
                val filename = extractFilename(finalUrl, contentDisposition, pageTitle)
                val ext = filename.substringAfterLast('.', precheck?.extension ?: "mp4")
                val estimatedSize = formatFileSize(contentLength)
                val category = determineCategory(contentType, ext)

                DetectedMedia(
                    url = finalUrl,
                    title = filename,
                    mimeType = if (contentType.isNotBlank()) contentType else (precheck?.mimeType ?: "video/mp4"),
                    extension = ext,
                    estimatedSize = estimatedSize,
                    mediaType = category,
                    quality = if (category == MediaType.VIDEO) "HD 1080p/720p" else "High Quality",
                    sourcePageTitle = pageTitle
                )
            } else {
                precheck
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error probing direct URL $urlStr: ${e.message}")
            // Fallback to offline regex check if network probe fails
            precheck
        }
    }

    /**
     * Scans a web page URL by downloading HTML and parsing video/audio/source/meta/anchor tags
     */
    private fun scanWebPageForMedia(
        pageUrl: String,
        pageTitle: String,
        userAgent: String
    ): List<DetectedMedia> {
        val detectedList = mutableListOf<DetectedMedia>()
        val seenUrls = mutableSetOf<String>()

        try {
            val request = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return emptyList()
            }

            // Read up to 2MB of HTML content
            val html = response.body?.charStream()?.buffered()?.readText() ?: ""
            response.close()

            if (html.isBlank()) return emptyList()

            // 1. Extract <video src="...">, <audio src="...">, <source src="...">
            extractUrlsWithPattern(
                html = html,
                pattern = Pattern.compile("""<(?:video|audio|source)[^>]+src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE),
                baseUrl = pageUrl,
                seenUrls = seenUrls,
                pageTitle = pageTitle,
                detectedList = detectedList
            )

            // 2. Extract OpenGraph & Twitter video tags
            extractUrlsWithPattern(
                html = html,
                pattern = Pattern.compile("""<meta[^>]+property=["']og:video(?::url|:secure_url)?["'][^>]+content=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE),
                baseUrl = pageUrl,
                seenUrls = seenUrls,
                pageTitle = pageTitle,
                detectedList = detectedList
            )
            extractUrlsWithPattern(
                html = html,
                pattern = Pattern.compile("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:video(?::url|:secure_url)?["']""", Pattern.CASE_INSENSITIVE),
                baseUrl = pageUrl,
                seenUrls = seenUrls,
                pageTitle = pageTitle,
                detectedList = detectedList
            )
            extractUrlsWithPattern(
                html = html,
                pattern = Pattern.compile("""<meta[^>]+name=["']twitter:player:stream["'][^>]+content=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE),
                baseUrl = pageUrl,
                seenUrls = seenUrls,
                pageTitle = pageTitle,
                detectedList = detectedList
            )

            // 3. Extract direct download anchor links (<a href="...mp4|m3u8|webm|mp3">)
            extractUrlsWithPattern(
                html = html,
                pattern = Pattern.compile("""<a[^>]+href=["']([^"']+\.(?:mp4|webm|mkv|mov|m4v|m3u8|mpd|mp3|m4a|aac|flac|wav)(?:\?[^"']*)?)["']""", Pattern.CASE_INSENSITIVE),
                baseUrl = pageUrl,
                seenUrls = seenUrls,
                pageTitle = pageTitle,
                detectedList = detectedList
            )

            // 4. Extract embedded stream manifest URLs inside JavaScript configurations (e.g. file: "https://...m3u8")
            extractUrlsWithPattern(
                html = html,
                pattern = Pattern.compile("""["'](https?://[^\s"'<>\\]+?\.(?:m3u8|mp4|webm|mp3)(?:[^\s"'<>\\]*)?)["']""", Pattern.CASE_INSENSITIVE),
                baseUrl = pageUrl,
                seenUrls = seenUrls,
                pageTitle = pageTitle,
                detectedList = detectedList
            )

        } catch (e: Exception) {
            Log.w(TAG, "Error scanning page HTML: ${e.message}")
        }

        return detectedList
    }

    private fun extractUrlsWithPattern(
        html: String,
        pattern: Pattern,
        baseUrl: String,
        seenUrls: MutableSet<String>,
        pageTitle: String,
        detectedList: MutableList<DetectedMedia>
    ) {
        val matcher = pattern.matcher(html)
        var count = 0
        while (matcher.find() && count < 15) {
            val raw = matcher.group(1)?.trim() ?: continue
            val resolved = resolveAbsoluteUrl(baseUrl, raw) ?: continue

            val cleanUrl = resolved.substringBefore('#')
            if (seenUrls.add(cleanUrl)) {
                val media = MediaSnifferEngine.sniffUrl(resolved, pageTitle)
                if (media != null) {
                    detectedList.add(media)
                    count++
                }
            }
        }
    }

    private fun resolveAbsoluteUrl(baseUrl: String, relativeOrAbsolute: String): String? {
        if (relativeOrAbsolute.startsWith("data:") || relativeOrAbsolute.startsWith("javascript:")) {
            return null
        }
        return try {
            if (relativeOrAbsolute.startsWith("http://") || relativeOrAbsolute.startsWith("https://")) {
                relativeOrAbsolute
            } else if (relativeOrAbsolute.startsWith("//")) {
                "https:$relativeOrAbsolute"
            } else {
                val base = URI(baseUrl)
                base.resolve(relativeOrAbsolute).toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isMediaContentType(contentType: String): Boolean {
        return contentType.startsWith("video/") ||
                contentType.startsWith("audio/") ||
                contentType.contains("application/x-mpegurl") ||
                contentType.contains("application/vnd.apple.mpegurl") ||
                contentType.contains("application/dash+xml") ||
                contentType.contains("application/octet-stream")
    }

    private fun determineCategory(contentType: String, extension: String): MediaType {
        return when {
            contentType.startsWith("audio/") || extension in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg") -> MediaType.AUDIO
            contentType.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "webp", "gif") -> MediaType.IMAGE
            else -> MediaType.VIDEO
        }
    }

    private fun extractFilename(url: String, contentDisposition: String, defaultTitle: String): String {
        // 1. Try Content-Disposition header
        if (contentDisposition.isNotBlank()) {
            val filenamePattern = Pattern.compile("""filename\*?=['"]?(?:UTF-8'')?([^'";\n]+)['"]?""", Pattern.CASE_INSENSITIVE)
            val matcher = filenamePattern.matcher(contentDisposition)
            if (matcher.find()) {
                val name = matcher.group(1)?.trim()
                if (!name.isNullOrBlank()) return name
            }
        }

        // 2. Try URL path
        try {
            val uri = Uri.parse(url)
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrBlank() && lastSegment.contains(".")) {
                return lastSegment
            }
        } catch (_: Exception) {}

        // 3. Fallback to default page title
        val safeTitle = defaultTitle.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(40).trim()
        return if (safeTitle.isNotBlank()) "$safeTitle.mp4" else "media_${System.currentTimeMillis()}.mp4"
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "Direct Stream"
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1.0) {
            "%.1f MB".format(Locale.US, mb)
        } else {
            val kb = bytes.toDouble() / 1024
            "%.0f KB".format(Locale.US, kb)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Link Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies when downloadable media streams or links are intercepted"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, message: String) {
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()

            val notificationId = NOTIFICATION_ID_BASE + (title.hashCode() % 1000)
            notificationManager.notify(notificationId, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Notification could not be shown: ${e.message}")
        }
    }
}
