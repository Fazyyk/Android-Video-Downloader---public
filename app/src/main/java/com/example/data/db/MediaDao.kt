package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DownloadedMedia
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM downloaded_media ORDER BY createdAt DESC")
    fun getAllMedia(): Flow<List<DownloadedMedia>>

    @Query("SELECT * FROM downloaded_media WHERE status = 'COMPLETED' ORDER BY createdAt DESC")
    fun getCompletedMedia(): Flow<List<DownloadedMedia>>

    @Query("SELECT * FROM downloaded_media WHERE status != 'COMPLETED' ORDER BY createdAt DESC")
    fun getActiveDownloads(): Flow<List<DownloadedMedia>>

    @Query("SELECT * FROM downloaded_media WHERE id = :id")
    suspend fun getMediaById(id: Long): DownloadedMedia?

    @Query("SELECT * FROM downloaded_media WHERE downloadManagerId = :dmId")
    suspend fun getMediaByDownloadManagerId(dmId: Long): DownloadedMedia?

    @Query("SELECT * FROM downloaded_media WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun getMediaBySourceUrl(sourceUrl: String): DownloadedMedia?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: DownloadedMedia): Long

    @Update
    suspend fun update(media: DownloadedMedia)

    @Query("UPDATE downloaded_media SET downloadProgress = :progress, status = :status, downloadSpeedText = :speed, etaText = :eta WHERE id = :id")
    suspend fun updateProgressWithEta(id: Long, progress: Int, status: String, speed: String, eta: String)

    @Query("UPDATE downloaded_media SET downloadProgress = :progress, status = :status, downloadSpeedText = :speed WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int, status: String, speed: String)

    @Query("UPDATE downloaded_media SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE downloaded_media SET title = :newTitle WHERE id = :id")
    suspend fun renameMedia(id: Long, newTitle: String)

    @Query("UPDATE downloaded_media SET isSyncedToCloud = :synced, cloudProvider = :provider WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, synced: Boolean, provider: String)

    @Query("DELETE FROM downloaded_media WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloaded_media")
    suspend fun deleteAll()
}
