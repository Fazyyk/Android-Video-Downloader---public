package com.example.data.media3

import android.content.Context
import android.util.Log
import com.example.data.db.MediaDao
import com.example.data.model.DetectedMedia
import com.example.data.model.MediaType
import com.example.data.worker.MediaDetectionHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Browser service coordinating real-time media URL detection and automated or manual
 * queuing into the Media3 background download engine.
 */
class MediaBrowserInterceptorService(
    private val context: Context,
    private val mediaDao: MediaDao? = null
) {
    companion object {
        private const val TAG = "MediaBrowserService"

        @Volatile
        private var INSTANCE: MediaBrowserInterceptorService? = null

        fun getInstance(context: Context, mediaDao: MediaDao? = null): MediaBrowserInterceptorService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaBrowserInterceptorService(context.applicationContext, mediaDao).also {
                    INSTANCE = it
                }
            }
        }
    }

    val queueManager: Media3DownloadQueueManager = Media3DownloadQueueManager.getInstance(context, mediaDao)
    val media3Queue: StateFlow<List<Media3QueueItem>> = queueManager.queueItems
    val isAutoQueueEnabled: StateFlow<Boolean> = queueManager.isAutoQueueEnabled

    private val _detectedMediaFlow = MutableSharedFlow<DetectedMedia>(extraBufferCapacity = 50)
    val detectedMediaFlow: SharedFlow<DetectedMedia> = _detectedMediaFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Inspects a browser URL and headers, detecting video/audio and optionally
     * enqueueing it directly to Media3 background downloading.
     */
    fun processUrl(
        url: String,
        headers: Map<String, String>? = null,
        pageTitle: String? = null,
        mimeType: String? = null,
        forceQueue: Boolean = false
    ): DetectedMedia? {
        if (!MediaNetworkInterceptor.isMediaUrl(url, headers)) {
            return null
        }

        val category = MediaNetworkInterceptor.detectCategory(url, mimeType)
        val title = MediaNetworkInterceptor.inferTitle(url, pageTitle)
        val cleanExt = url.substringBefore('?').substringAfterLast('.', if (category == "AUDIO") "mp3" else "mp4")
        val resolvedMime = mimeType ?: (if (category == "AUDIO") "audio/mpeg" else "video/mp4")

        val detected = DetectedMedia(
            url = url,
            title = title,
            mimeType = resolvedMime,
            extension = cleanExt,
            estimatedSize = "Direct Stream",
            mediaType = if (category == "AUDIO") MediaType.AUDIO else MediaType.VIDEO,
            quality = "HD",
            sourcePageTitle = pageTitle ?: ""
        )

        Log.d(TAG, "Media detected in browser service: $title ($category) from $url")

        // Broadcast to detection hubs
        MediaDetectionHub.postDetectedMedia(detected)
        _detectedMediaFlow.tryEmit(detected)

        // Queue to Media3 background downloader if auto-queue is enabled or forceQueue is requested
        if (forceQueue || queueManager.isAutoQueueEnabled.value) {
            queueMediaForDownload(detected)
        }

        return detected
    }

    /**
     * Enqueues a detected media item into the Media3 background download queue.
     */
    fun queueMediaForDownload(media: DetectedMedia): String {
        Log.i(TAG, "Enqueuing media into Media3 background queue: ${media.title} (${media.url})")
        val category = if (media.mediaType == MediaType.AUDIO) "AUDIO" else "VIDEO"
        return queueManager.enqueueDownload(
            url = media.url,
            title = media.title,
            mimeType = media.mimeType,
            category = category
        )
    }

    /**
     * Enqueues by raw parameters
     */
    fun queueUrlForDownload(
        url: String,
        title: String,
        mimeType: String? = null,
        category: String = "VIDEO"
    ): String {
        return queueManager.enqueueDownload(url, title, mimeType, category)
    }

    fun setAutoQueue(enabled: Boolean) {
        queueManager.setAutoQueueEnabled(enabled)
    }

    fun pauseQueueItem(id: String) {
        queueManager.pauseDownload(id)
    }

    fun resumeQueueItem(id: String) {
        queueManager.resumeDownload(id)
    }

    fun removeQueueItem(id: String) {
        queueManager.removeDownload(id)
    }
}
