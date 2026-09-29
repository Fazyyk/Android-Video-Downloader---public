package com.example.data.media3

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.example.R

/**
 * Foreground service for Media3 background media downloads.
 */
@OptIn(UnstableApi::class)
class Media3DownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description
) {
    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "media3_download_channel"
        private const val JOB_ID = 1001
    }

    override fun getDownloadManager(): DownloadManager {
        return Media3DownloadQueueManager.getInstance(applicationContext).downloadManager
    }

    override fun getScheduler(): Scheduler? {
        return try {
            PlatformScheduler(this, JOB_ID)
        } catch (_: Exception) {
            null
        }
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val helper = DownloadNotificationHelper(this, CHANNEL_ID)
        return helper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }
}
