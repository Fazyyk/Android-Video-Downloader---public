package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.db.MediaDao
import com.example.data.model.DownloadedMedia
import com.example.data.worker.MediaDownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File

class DownloadServiceManager(
    private val context: Context,
    private val mediaDao: MediaDao,
    private val coroutineScope: CoroutineScope
) {
    private val workManager: WorkManager = WorkManager.getInstance(context)

    init {
        syncUnfinishedDownloadsOnStartup()
    }

    fun startDownload(
        sourceUrl: String,
        title: String,
        mimeType: String,
        category: String
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            enqueueDownload(sourceUrl, title, mimeType, category)
        }
    }

    suspend fun enqueueDownload(
        sourceUrl: String,
        title: String,
        mimeType: String,
        category: String
    ): Long {
        val sanitizedTitle = sanitizeFilename(title)
        val extension = getExtensionFromMimeOrUrl(mimeType, sourceUrl)
        val fileName = if (sanitizedTitle.endsWith(".$extension", ignoreCase = true)) {
            sanitizedTitle
        } else {
            "$sanitizedTitle.$extension"
        }

        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val targetFile = File(targetDir, fileName)

        val mediaItem = DownloadedMedia(
            title = sanitizedTitle,
            sourceUrl = sourceUrl,
            localUri = Uri.fromFile(targetFile).toString(),
            mimeType = mimeType,
            fileSizeBytes = 0L,
            downloadProgress = 0,
            status = "DOWNLOADING",
            downloadManagerId = -1L,
            category = category,
            createdAt = System.currentTimeMillis(),
            downloadSpeedText = "Queued..."
        )
        val mediaId = mediaDao.insert(mediaItem)

        enqueueDownloadWorker(mediaId, sourceUrl, fileName, sanitizedTitle)
        return mediaId
    }

    private fun enqueueDownloadWorker(
        mediaId: Long,
        sourceUrl: String,
        fileName: String,
        title: String
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadData = workDataOf(
            MediaDownloadWorker.KEY_MEDIA_ID to mediaId,
            MediaDownloadWorker.KEY_SOURCE_URL to sourceUrl,
            MediaDownloadWorker.KEY_FILE_NAME to fileName,
            MediaDownloadWorker.KEY_TITLE to title
        )

        val workRequest = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(downloadData)
            .addTag("download_$mediaId")
            .addTag("media_download")
            .build()

        workManager.enqueueUniqueWork(
            "download_work_$mediaId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun pauseDownload(mediaId: Long) {
        coroutineScope.launch(Dispatchers.IO) {
            workManager.cancelUniqueWork("download_work_$mediaId")
            mediaDao.updateStatus(mediaId, "PAUSED")
        }
    }

    fun resumeDownload(mediaId: Long) {
        coroutineScope.launch(Dispatchers.IO) {
            val item = mediaDao.getMediaById(mediaId) ?: return@launch
            if (item.status == "COMPLETED") return@launch

            val extension = getExtensionFromMimeOrUrl(item.mimeType, item.sourceUrl)
            val sanitizedTitle = sanitizeFilename(item.title)
            val fileName = if (sanitizedTitle.endsWith(".$extension", ignoreCase = true)) {
                sanitizedTitle
            } else {
                "$sanitizedTitle.$extension"
            }

            mediaDao.updateProgress(item.id, item.downloadProgress, "DOWNLOADING", "Resuming...")
            enqueueDownloadWorker(item.id, item.sourceUrl, fileName, sanitizedTitle)
        }
    }

    fun cancelDownload(mediaId: Long) {
        coroutineScope.launch(Dispatchers.IO) {
            workManager.cancelUniqueWork("download_work_$mediaId")
            val item = mediaDao.getMediaById(mediaId)
            if (item != null && item.localUri.isNotEmpty()) {
                try {
                    val file = Uri.parse(item.localUri).path?.let { File(it) }
                    if (file?.exists() == true) {
                        file.delete()
                    }
                } catch (_: Exception) {}
            }
            mediaDao.deleteById(mediaId)
        }
    }

    private fun syncUnfinishedDownloadsOnStartup() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val activeList = mediaDao.getActiveDownloads().firstOrNull() ?: emptyList()
                for (item in activeList) {
                    if (item.status == "DOWNLOADING") {
                        val extension = getExtensionFromMimeOrUrl(item.mimeType, item.sourceUrl)
                        val sanitizedTitle = sanitizeFilename(item.title)
                        val fileName = if (sanitizedTitle.endsWith(".$extension", ignoreCase = true)) {
                            sanitizedTitle
                        } else {
                            "$sanitizedTitle.$extension"
                        }
                        enqueueDownloadWorker(item.id, item.sourceUrl, fileName, sanitizedTitle)
                    }
                }
            } catch (_: Throwable) {
                // Ignore transient db lock on launch
            }
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(50).ifEmpty { "download_${System.currentTimeMillis()}" }
    }

    private fun getExtensionFromMimeOrUrl(mimeType: String, url: String): String {
        val extFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (!extFromMime.isNullOrEmpty()) return extFromMime

        val cleanUrl = url.substringBefore('?').substringBefore('#')
        val ext = cleanUrl.substringAfterLast('.', "")
        return if (ext.isNotEmpty() && ext.length <= 5) ext else "mp4"
    }
}
