package com.example.data.repository

import com.example.data.db.BookmarkDao
import com.example.data.db.MediaDao
import com.example.data.model.DownloadedMedia
import com.example.data.model.WebBookmark
import kotlinx.coroutines.flow.Flow

class MediaRepository(
    private val mediaDao: MediaDao,
    private val bookmarkDao: BookmarkDao,
    val downloadManager: DownloadServiceManager,
    val cloudSyncManager: CloudSyncManager,
    val securityPrefs: SecurityPreferences
) {
    val allMedia: Flow<List<DownloadedMedia>> = mediaDao.getAllMedia()
    val completedMedia: Flow<List<DownloadedMedia>> = mediaDao.getCompletedMedia()
    val activeDownloads: Flow<List<DownloadedMedia>> = mediaDao.getActiveDownloads()
    val allBookmarks: Flow<List<WebBookmark>> = bookmarkDao.getAllBookmarks()

    suspend fun initDefaultBookmarksIfEmpty() {
        if (bookmarkDao.getCount() == 0) {
            val defaults = listOf(
                WebBookmark(
                    title = "Archive.org Open Movies",
                    url = "https://archive.org/details/movies",
                    category = "Video Portals",
                    description = "Thousands of historic films, open movies, and public domain clips",
                    iconName = "movie",
                    isPinned = true
                ),
                WebBookmark(
                    title = "Wikimedia Commons Media",
                    url = "https://commons.wikimedia.org/wiki/Category:Videos",
                    category = "Creative Commons",
                    description = "Freely usable educational and public domain video files",
                    iconName = "video",
                    isPinned = true
                ),
                WebBookmark(
                    title = "Pexels Free 4K Stock",
                    url = "https://www.pexels.com/videos/",
                    category = "Video Portals",
                    description = "High definition and 4K royalty-free videos for download",
                    iconName = "videocam",
                    isPinned = true
                ),
                WebBookmark(
                    title = "Vimeo Watch Hub",
                    url = "https://vimeo.com/watch",
                    category = "Video Portals",
                    description = "Independent films, creative videos and artistic short reels",
                    iconName = "play",
                    isPinned = false
                ),
                WebBookmark(
                    title = "Free Music Archive",
                    url = "https://freemusicarchive.org",
                    category = "Audio & Music",
                    description = "High quality legal audio downloads and royalty-free music tracks",
                    iconName = "music",
                    isPinned = true
                ),
                WebBookmark(
                    title = "LibriVox Free Audiobooks",
                    url = "https://librivox.org",
                    category = "Audio & Music",
                    description = "Public domain audiobooks narrated by volunteers worldwide",
                    iconName = "audiobook",
                    isPinned = false
                ),
                WebBookmark(
                    title = "Freesound Effects Vault",
                    url = "https://freesound.org",
                    category = "Audio & Music",
                    description = "Collaborative database of Creative Commons audio snippets",
                    iconName = "sound",
                    isPinned = false
                ),
                WebBookmark(
                    title = "W3Schools HTML5 Video Lab",
                    url = "https://www.w3schools.com/html/html5_video.asp",
                    category = "Media Test Labs",
                    description = "Direct MP4/WebM video tags ideal for testing browser sniffer",
                    iconName = "code",
                    isPinned = true
                ),
                WebBookmark(
                    title = "DuckDuckGo Private Search",
                    url = "https://duckduckgo.com",
                    category = "Search & Discovery",
                    description = "Private tracker-free web search for finding video and media",
                    iconName = "search",
                    isPinned = true
                )
            )
            bookmarkDao.insertAll(defaults)
        }
    }

    suspend fun addBookmark(bookmark: WebBookmark): Long {
        return bookmarkDao.insert(bookmark)
    }

    suspend fun deleteBookmark(id: Long) {
        bookmarkDao.deleteById(id)
    }

    suspend fun renameMedia(id: Long, newTitle: String) {
        mediaDao.renameMedia(id, newTitle)
    }

    suspend fun deleteMedia(id: Long) {
        mediaDao.deleteById(id)
    }

    suspend fun syncMediaToCloud(id: Long, provider: String) {
        mediaDao.updateSyncStatus(id, true, provider)
    }

    suspend fun insertSampleMediaIfEmpty() {
        // Provide sample entries for immediate rich preview
    }
}
