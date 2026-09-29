package com.example.data.media3

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.example.data.db.MediaDao
import com.example.data.model.DownloadedMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

data class Media3QueueItem(
    val id: String,
    val uri: Uri,
    val title: String,
    val mimeType: String,
    val category: String,
    val state: Int,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val percentDownloaded: Float = 0f,
    val failureReason: Int = 0,
    val queuedAt: Long = System.currentTimeMillis()
) {
    val stateLabel: String
        get() = when (state) {
            Download.STATE_QUEUED -> "Queued"
            Download.STATE_STOPPED -> "Paused"
            Download.STATE_DOWNLOADING -> "Downloading (Media3)"
            Download.STATE_COMPLETED -> "Completed"
            Download.STATE_FAILED -> "Failed"
            Download.STATE_REMOVING -> "Removing"
            Download.STATE_RESTARTING -> "Restarting"
            else -> "Processing"
        }
}

/**
 * Manages background media downloading via AndroidX Media3 DownloadManager and Cache.
 */
@OptIn(UnstableApi::class)
class Media3DownloadQueueManager private constructor(
    private val context: Context,
    private val mediaDao: MediaDao? = null
) {
    companion object {
        private const val TAG = "Media3QueueManager"
        private const val DOWNLOAD_CONTENT_DIRECTORY = "media3_downloads"

        @Volatile
        private var INSTANCE: Media3DownloadQueueManager? = null

        fun getInstance(context: Context, mediaDao: MediaDao? = null): Media3DownloadQueueManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Media3DownloadQueueManager(context.applicationContext, mediaDao).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val databaseProvider: DatabaseProvider by lazy {
        StandaloneDatabaseProvider(context)
    }

    private val downloadCache: Cache by lazy {
        val downloadDirectory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            DOWNLOAD_CONTENT_DIRECTORY
        )
        if (!downloadDirectory.exists()) {
            downloadDirectory.mkdirs()
        }
        SimpleCache(downloadDirectory, NoOpCacheEvictor(), databaseProvider)
    }

    private val httpDataSourceFactory: DataSource.Factory by lazy {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; MediaFetch) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setAllowCrossProtocolRedirects(true)
    }

    val downloadManager: DownloadManager by lazy {
        val manager = DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            httpDataSourceFactory,
            Executors.newFixedThreadPool(3)
        )
        manager.maxParallelDownloads = 3
        manager.addListener(DownloadManagerListener())
        manager
    }

    private val _queueItems = MutableStateFlow<List<Media3QueueItem>>(emptyList())
    val queueItems: StateFlow<List<Media3QueueItem>> = _queueItems.asStateFlow()

    private val _isAutoQueueEnabled = MutableStateFlow(false)
    val isAutoQueueEnabled: StateFlow<Boolean> = _isAutoQueueEnabled.asStateFlow()

    init {
        // Initialize download manager and load existing downloads
        try {
            downloadManager.resumeDownloads()
            loadCurrentDownloads()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Media3 DownloadManager: ${e.message}")
        }
    }

    fun setAutoQueueEnabled(enabled: Boolean) {
        _isAutoQueueEnabled.value = enabled
        Log.d(TAG, "Auto-queue enabled: $enabled")
    }

    /**
     * Enqueues a detected media URL into the Media3 background download queue.
     */
    fun enqueueDownload(
        url: String,
        title: String,
        mimeType: String? = null,
        category: String = "VIDEO"
    ): String {
        val uri = Uri.parse(url)
        val id = UUID.nameUUIDFromBytes(url.toByteArray()).toString()
        val resolvedMime = mimeType ?: if (category == "AUDIO") "audio/mpeg" else "video/mp4"

        // Check if already queued
        val existing = _queueItems.value.find { it.id == id || it.uri.toString() == url }
        if (existing != null && (existing.state == Download.STATE_QUEUED || existing.state == Download.STATE_DOWNLOADING)) {
            Log.d(TAG, "Download already queued in Media3: $url")
            return existing.id
        }

        val request = DownloadRequest.Builder(id, uri)
            .setMimeType(resolvedMime)
            .setData(title.toByteArray(Charsets.UTF_8))
            .build()

        // Create optimistic queue item
        val newItem = Media3QueueItem(
            id = id,
            uri = uri,
            title = title,
            mimeType = resolvedMime,
            category = category,
            state = Download.STATE_QUEUED,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            percentDownloaded = 0f
        )

        _queueItems.value = listOf(newItem) + _queueItems.value.filter { it.id != id }

        try {
            // Dispatch download via DownloadService or direct DownloadManager
            DownloadService.sendAddDownload(
                context,
                Media3DownloadService::class.java,
                request,
                /* foreground = */ false
            )
            Log.d(TAG, "Successfully queued download in Media3: $title ($url)")
        } catch (e: Exception) {
            Log.w(TAG, "DownloadService invocation failed, adding directly to DownloadManager: ${e.message}")
            try {
                downloadManager.addDownload(request)
            } catch (inner: Exception) {
                Log.e(TAG, "Failed to add download directly: ${inner.message}")
            }
        }

        return id
    }

    fun pauseDownload(id: String) {
        try {
            DownloadService.sendSetStopReason(
                context,
                Media3DownloadService::class.java,
                id,
                Download.STOP_REASON_NONE + 1,
                false
            )
        } catch (_: Exception) {
            downloadManager.setStopReason(id, Download.STOP_REASON_NONE + 1)
        }
    }

    fun resumeDownload(id: String) {
        try {
            DownloadService.sendSetStopReason(
                context,
                Media3DownloadService::class.java,
                id,
                Download.STOP_REASON_NONE,
                false
            )
        } catch (_: Exception) {
            downloadManager.setStopReason(id, Download.STOP_REASON_NONE)
        }
    }

    fun removeDownload(id: String) {
        try {
            DownloadService.sendRemoveDownload(
                context,
                Media3DownloadService::class.java,
                id,
                false
            )
        } catch (_: Exception) {
            downloadManager.removeDownload(id)
        }
        _queueItems.value = _queueItems.value.filter { it.id != id }
    }

    private fun loadCurrentDownloads() {
        try {
            val cursor = downloadManager.downloadIndex.getDownloads()
            val list = mutableListOf<Media3QueueItem>()
            while (cursor.moveToNext()) {
                val download = cursor.download
                list.add(download.toQueueItem())
            }
            cursor.close()
            _queueItems.value = list
        } catch (e: Exception) {
            Log.w(TAG, "Could not load current downloads: ${e.message}")
        }
    }

    private fun Download.toQueueItem(): Media3QueueItem {
        val titleStr = try {
            if (request.data.isNotEmpty()) String(request.data, Charsets.UTF_8) else request.uri.lastPathSegment ?: "Media"
        } catch (_: Exception) {
            request.uri.lastPathSegment ?: "Media"
        }

        val category = MediaNetworkInterceptor.detectCategory(request.uri.toString(), request.mimeType)

        return Media3QueueItem(
            id = request.id,
            uri = request.uri,
            title = titleStr,
            mimeType = request.mimeType ?: (if (category == "AUDIO") "audio/mpeg" else "video/mp4"),
            category = category,
            state = state,
            bytesDownloaded = bytesDownloaded,
            totalBytes = contentLength,
            percentDownloaded = percentDownloaded.coerceAtLeast(0f),
            failureReason = failureReason
        )
    }

    private inner class DownloadManagerListener : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            val item = download.toQueueItem()
            val current = _queueItems.value.toMutableList()
            val index = current.indexOfFirst { it.id == item.id }
            if (index != -1) {
                current[index] = item
            } else {
                current.add(0, item)
            }
            _queueItems.value = current

            Log.d(TAG, "Media3 Download status: ${item.title} -> ${item.stateLabel} (${item.percentDownloaded}%)")

            if (download.state == Download.STATE_COMPLETED && mediaDao != null) {
                scope.launch {
                    try {
                        val existingMedia = mediaDao.getMediaBySourceUrl(item.uri.toString())
                        if (existingMedia == null) {
                            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                            val fileName = "${item.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.${if (item.category == "AUDIO") "mp3" else "mp4"}"
                            val file = File(targetDir, fileName)

                            val mediaItem = DownloadedMedia(
                                title = item.title,
                                sourceUrl = item.uri.toString(),
                                localUri = Uri.fromFile(file).toString(),
                                mimeType = item.mimeType,
                                fileSizeBytes = if (item.bytesDownloaded > 0) item.bytesDownloaded else item.totalBytes,
                                downloadProgress = 100,
                                status = "COMPLETED",
                                category = item.category,
                                createdAt = System.currentTimeMillis()
                            )
                            mediaDao.insert(mediaItem)
                            Log.d(TAG, "Saved completed Media3 download to Room DB: ${item.title}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to record completed Media3 download in Room: ${e.message}")
                    }
                }
            }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            _queueItems.value = _queueItems.value.filter { it.id != download.request.id }
        }
    }
}
