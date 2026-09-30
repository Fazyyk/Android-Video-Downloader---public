package com.example.data.worker

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.db.MediaDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Data model representing real-time progress of a media download initiated by the media sniffer,
 * fed directly from Android WorkManager progress updates and database records.
 */
data class SnifferDownloadProgress(
    val workId: String,
    val mediaId: Long,
    val title: String,
    val sourceUrl: String,
    val progress: Int, // 0..100
    val speed: String, // e.g. "2.4 MB/s"
    val eta: String, // e.g. "4s remaining"
    val state: WorkInfo.State,
    val category: String = "VIDEO",
    val isRunning: Boolean = state == WorkInfo.State.RUNNING,
    val isEnqueued: Boolean = state == WorkInfo.State.ENQUEUED
) {
    val stateLabel: String
        get() = when (state) {
            WorkInfo.State.RUNNING -> "Downloading"
            WorkInfo.State.ENQUEUED -> "Queued"
            WorkInfo.State.SUCCEEDED -> "Completed"
            WorkInfo.State.FAILED -> "Failed"
            WorkInfo.State.BLOCKED -> "Waiting"
            WorkInfo.State.CANCELLED -> "Cancelled"
        }
}

/**
 * Tracks active downloads initiated by the media sniffer using WorkManager progress updates.
 */
class WorkManagerDownloadTracker(
    private val context: Context,
    private val mediaDao: MediaDao
) {
    private val workManager: WorkManager = WorkManager.getInstance(context)

    /**
     * Emits a reactive list of active sniffer downloads by combining WorkManager's
     * getWorkInfosByTagFlow("media_download") stream with local Room database states.
     */
    fun getActiveSnifferDownloadsFlow(): Flow<List<SnifferDownloadProgress>> {
        return workManager.getWorkInfosByTagFlow("media_download")
            .combine(mediaDao.getActiveDownloads()) { workInfos, activeMediaList ->
                val mediaMap = activeMediaList.associateBy { it.id }

                // Map active WorkManager tasks
                val fromWorkManager = workInfos.filter { info ->
                    info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
                }.mapNotNull { info ->
                    // Extract mediaId from tags e.g. "download_123"
                    val mediaIdTag = info.tags.firstOrNull { it.startsWith("download_") && it != "download_work" }
                    val mediaId = mediaIdTag?.removePrefix("download_")?.toLongOrNull() ?: -1L
                    val media = if (mediaId != -1L) mediaMap[mediaId] else null

                    // Progress Data emitted by MediaDownloadWorker.setProgress()
                    val wmProgress = info.progress.getInt(
                        MediaDownloadWorker.KEY_PROGRESS,
                        media?.downloadProgress ?: 0
                    )
                    val wmSpeed = info.progress.getString(MediaDownloadWorker.KEY_SPEED)
                        ?: media?.downloadSpeedText
                        ?: if (info.state == WorkInfo.State.RUNNING) "Connecting..." else "Queued in WorkManager"

                    val wmEta = info.progress.getString(MediaDownloadWorker.KEY_ETA)
                        ?: media?.etaText
                        ?: if (info.state == WorkInfo.State.RUNNING) "Estimating ETA..." else "Waiting in queue"

                    SnifferDownloadProgress(
                        workId = info.id.toString(),
                        mediaId = mediaId,
                        title = media?.title ?: "Sniffed Media Stream",
                        sourceUrl = media?.sourceUrl ?: "",
                        progress = wmProgress.coerceIn(0, 100),
                        speed = wmSpeed,
                        eta = wmEta,
                        state = info.state,
                        category = media?.category ?: "VIDEO"
                    )
                }

                // Merge active media entries that are marked DOWNLOADING in Room to avoid empty flickers
                val coveredMediaIds = fromWorkManager.map { it.mediaId }.toSet()
                val additionalActive = activeMediaList
                    .filter { it.status == "DOWNLOADING" && it.id !in coveredMediaIds }
                    .map { media ->
                        SnifferDownloadProgress(
                            workId = "local_${media.id}",
                            mediaId = media.id,
                            title = media.title,
                            sourceUrl = media.sourceUrl,
                            progress = media.downloadProgress.coerceIn(0, 100),
                            speed = media.downloadSpeedText.ifBlank { "Initializing..." },
                            eta = media.etaText.ifBlank { "Starting WorkManager..." },
                            state = WorkInfo.State.RUNNING,
                            category = media.category
                        )
                    }

                fromWorkManager + additionalActive
            }
    }
}
