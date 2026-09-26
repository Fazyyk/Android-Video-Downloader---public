package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_media")
data class DownloadedMedia(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val sourceUrl: String,
    val localUri: String = "",
    val mimeType: String = "video/mp4",
    val fileSizeBytes: Long = 0L,
    val downloadProgress: Int = 0, // 0 to 100
    val status: String = "DOWNLOADING", // DOWNLOADING, PAUSED, COMPLETED, FAILED
    val downloadManagerId: Long = -1L,
    val category: String = "VIDEO", // VIDEO, AUDIO, IMAGE, OTHER
    val createdAt: Long = System.currentTimeMillis(),
    val isSyncedToCloud: Boolean = false,
    val cloudProvider: String = "", // "Google Drive", "Dropbox", etc.
    val downloadSpeedText: String = "",
    val durationSeconds: Long = 0L
)
