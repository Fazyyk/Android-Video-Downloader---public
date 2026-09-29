package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.media3.MediaBrowserInterceptorService
import com.example.data.model.DetectedMedia
import com.example.data.model.DownloadedMedia
import com.example.data.model.MediaType
import com.example.data.model.WebBookmark
import com.example.data.repository.CloudSyncManager
import com.example.data.repository.DownloadServiceManager
import com.example.data.repository.FirestoreSyncManager
import com.example.data.repository.MediaRepository
import com.example.data.repository.SecurityPreferences
import com.example.data.repository.ThemePreferences
import com.example.data.worker.MediaDetectionHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab(val title: String) {
    BROWSER("Browser"),
    DOWNLOADS("Downloads"),
    FILES("Files"),
    SITES("Sites Hub"),
    SYNC("Cloud Sync"),
    SETTINGS("Settings")
}

enum class SortOption(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    SIZE_DESC("Size (Largest)"),
    SIZE_ASC("Size (Smallest)")
}

enum class LayoutStyle {
    GRID, LIST
}

enum class ThemeMode {
    SYSTEM, DARK, LIGHT, AMOLED
}

enum class AccentColor(val displayName: String, val hex: Long) {
    CYAN("Electric Cyan", 0xFF06B6D4),
    PURPLE("Deep Purple", 0xFF8B5CF6),
    EMERALD("Emerald Green", 0xFF10B981),
    AMBER("Amber Flame", 0xFFF59E0B),
    ROSE("Sunset Rose", 0xFFF43F5E)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val securityPrefs = SecurityPreferences(application)
    private val themePreferences = ThemePreferences(application)
    private val downloadManager = DownloadServiceManager(application, database.mediaDao(), viewModelScope)
    private val cloudSyncManager = CloudSyncManager(application)
    val firestoreSyncManager = FirestoreSyncManager(application, database.mediaDao())
    val repository = MediaRepository(
        database.mediaDao(),
        database.bookmarkDao(),
        downloadManager,
        cloudSyncManager,
        firestoreSyncManager,
        securityPrefs
    )

    // Media3 Background Download Service & Interceptor
    val mediaBrowserService = MediaBrowserInterceptorService.getInstance(application, database.mediaDao())
    val media3Queue: StateFlow<List<com.example.data.media3.Media3QueueItem>> = mediaBrowserService.media3Queue
    val isAutoQueueMedia3: StateFlow<Boolean> = mediaBrowserService.isAutoQueueEnabled

    fun enqueueMedia3Download(url: String, title: String, mimeType: String? = null, category: String = "VIDEO"): String {
        return mediaBrowserService.queueUrlForDownload(url, title, mimeType, category)
    }

    fun enqueueMedia3Media(detected: DetectedMedia): String {
        return mediaBrowserService.queueMediaForDownload(detected)
    }

    fun setAutoQueueMedia3(enabled: Boolean) {
        mediaBrowserService.setAutoQueue(enabled)
    }

    fun pauseMedia3Download(id: String) {
        mediaBrowserService.pauseQueueItem(id)
    }

    fun resumeMedia3Download(id: String) {
        mediaBrowserService.resumeQueueItem(id)
    }

    fun removeMedia3Download(id: String) {
        mediaBrowserService.removeQueueItem(id)
    }

    // Navigation & Tabs
    private val _currentTab = MutableStateFlow(AppTab.SITES)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Browser State
    private val _browserUrl = MutableStateFlow("https://duckduckgo.com")
    val browserUrl: StateFlow<String> = _browserUrl.asStateFlow()

    private val _browserTitle = MutableStateFlow("Private Search & Discovery")
    val browserTitle: StateFlow<String> = _browserTitle.asStateFlow()

    private val _browserProgress = MutableStateFlow(0)
    val browserProgress: StateFlow<Int> = _browserProgress.asStateFlow()

    private val _isDesktopMode = MutableStateFlow(false)
    val isDesktopMode: StateFlow<Boolean> = _isDesktopMode.asStateFlow()

    // Detected Media from Page
    private val _detectedMediaList = MutableStateFlow<List<DetectedMedia>>(emptyList())
    val detectedMediaList: StateFlow<List<DetectedMedia>> = _detectedMediaList.asStateFlow()

    private val _isMediaSnifferOpen = MutableStateFlow(false)
    val isMediaSnifferOpen: StateFlow<Boolean> = _isMediaSnifferOpen.asStateFlow()

    private val _isSniffingActive = MutableStateFlow(false)
    val isSniffingActive: StateFlow<Boolean> = _isSniffingActive.asStateFlow()

    private val _deepScanTrigger = MutableStateFlow(0L)
    val deepScanTrigger: StateFlow<Long> = _deepScanTrigger.asStateFlow()

    private val _previewDetectedMedia = MutableStateFlow<DetectedMedia?>(null)
    val previewDetectedMedia: StateFlow<DetectedMedia?> = _previewDetectedMedia.asStateFlow()

    // Downloads & Files Data
    val allMedia: StateFlow<List<DownloadedMedia>> = repository.allMedia.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val activeDownloads: StateFlow<List<DownloadedMedia>> = repository.activeDownloads.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val completedMedia: StateFlow<List<DownloadedMedia>> = repository.completedMedia.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val bookmarks: StateFlow<List<WebBookmark>> = repository.allBookmarks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // File Management Filters
    val searchQuery = MutableStateFlow("")
    val selectedCategoryFilter = MutableStateFlow("ALL") // ALL, VIDEO, AUDIO, IMAGE, OTHER
    val sortOption = MutableStateFlow(SortOption.DATE_DESC)
    val layoutStyle = MutableStateFlow(LayoutStyle.GRID)

    // Battery Optimization State
    private val _isBatteryRestricted = MutableStateFlow(false)
    val isBatteryRestricted: StateFlow<Boolean> = _isBatteryRestricted.asStateFlow()

    private val _isBatteryAlertDismissed = MutableStateFlow(false)
    val isBatteryAlertDismissed: StateFlow<Boolean> = _isBatteryAlertDismissed.asStateFlow()

    val isWorkManagerScanning: StateFlow<Boolean> = MediaDetectionHub.isWorkerRunning
    val totalInterceptedCount: StateFlow<Int> = MediaDetectionHub.totalInterceptedCount

    init {
        checkBatteryOptimizationStatus()
        // Collect background WorkManager intercepted media links in real time
        viewModelScope.launch {
            MediaDetectionHub.detectedMediaFlow.collect { detected ->
                addDetectedMedia(detected)
            }
        }
    }

    // Filtered and Sorted Files
    val filteredFiles: StateFlow<List<DownloadedMedia>> = combine(
        completedMedia,
        searchQuery,
        selectedCategoryFilter,
        sortOption
    ) { list, query, category, sort ->
        var result = list

        if (query.isNotBlank()) {
            result = result.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.mimeType.contains(query, ignoreCase = true)
            }
        }

        if (category != "ALL") {
            result = when (category.uppercase()) {
                "FIRESTORE_VERIFIED", "FIRESTORE" -> result.filter {
                    it.isSyncedToCloud && it.cloudProvider.contains("Firestore", ignoreCase = true)
                }
                "OFFLINE_READY", "OFFLINE" -> result.filter {
                    it.status == "COMPLETED" || it.localUri.isNotBlank()
                }
                "DOCUMENT", "DOCUMENTS" -> result.filter {
                    it.category.equals("DOCUMENT", ignoreCase = true) || it.category.equals("OTHER", ignoreCase = true)
                }
                else -> result.filter { it.category.equals(category, ignoreCase = true) }
            }
        }

        when (sort) {
            SortOption.DATE_DESC -> result.sortedByDescending { it.createdAt }
            SortOption.DATE_ASC -> result.sortedBy { it.createdAt }
            SortOption.NAME_ASC -> result.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> result.sortedByDescending { it.title.lowercase() }
            SortOption.SIZE_DESC -> result.sortedByDescending { it.fileSizeBytes }
            SortOption.SIZE_ASC -> result.sortedBy { it.fileSizeBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Player modal
    private val _selectedMediaForPlayer = MutableStateFlow<DownloadedMedia?>(null)
    val selectedMediaForPlayer: StateFlow<DownloadedMedia?> = _selectedMediaForPlayer.asStateFlow()

    // Rename dialog
    private val _renameTarget = MutableStateFlow<DownloadedMedia?>(null)
    val renameTarget: StateFlow<DownloadedMedia?> = _renameTarget.asStateFlow()

    // Add Bookmark dialog
    private val _isAddBookmarkOpen = MutableStateFlow(false)
    val isAddBookmarkOpen: StateFlow<Boolean> = _isAddBookmarkOpen.asStateFlow()

    // Security & Auth
    val isAppLocked: StateFlow<Boolean> = securityPrefs.isAppLocked
    val isPinEnabled: Boolean get() = securityPrefs.isPinEnabled()
    val isBiometricEnabled: Boolean get() = securityPrefs.isBiometricEnabled()
    val canUseBiometric: Boolean get() = securityPrefs.canUseBiometric()
    val hasPinSet: Boolean get() = securityPrefs.hasPinSet()

    // Theme Customization (Persistent Global Theme)
    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
    val accentColor: StateFlow<AccentColor> = themePreferences.accentColor

    fun setThemeMode(mode: ThemeMode) {
        themePreferences.setThemeMode(mode)
    }

    fun setAccentColor(accent: AccentColor) {
        themePreferences.setAccentColor(accent)
    }

    init {
        viewModelScope.launch {
            repository.initDefaultBookmarksIfEmpty()
            insertSampleMediaIfEmpty()
        }
        viewModelScope.launch {
            mediaBrowserService.detectedMediaFlow.collect { detected ->
                addDetectedMedia(detected)
            }
        }
    }

    private suspend fun insertSampleMediaIfEmpty() {
        if (database.mediaDao().getCompletedMedia() != null) {
            val existing = database.mediaDao().getMediaById(1)
            if (existing == null) {
                val samples = listOf(
                    DownloadedMedia(
                        title = "Big_Buck_Bunny_Open_Movie_1080p",
                        sourceUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        localUri = "content://media/sample/big_buck_bunny.mp4",
                        mimeType = "video/mp4",
                        fileSizeBytes = 158_000_000L,
                        downloadProgress = 100,
                        status = "COMPLETED",
                        category = "VIDEO",
                        isSyncedToCloud = true,
                        cloudProvider = "Firestore"
                    ),
                    DownloadedMedia(
                        title = "Cosmic_Ambient_Synthesizer_Track",
                        sourceUrl = "https://example.com/audio/cosmic_track.mp3",
                        localUri = "content://media/sample/cosmic_track.mp3",
                        mimeType = "audio/mpeg",
                        fileSizeBytes = 8_400_000L,
                        downloadProgress = 100,
                        status = "COMPLETED",
                        category = "AUDIO",
                        isSyncedToCloud = false
                    ),
                    DownloadedMedia(
                        title = "Cyberpunk_City_Wallpapers_4K",
                        sourceUrl = "https://example.com/img/cyberpunk_art.webp",
                        localUri = "content://media/sample/cyberpunk_art.webp",
                        mimeType = "image/webp",
                        fileSizeBytes = 4_200_000L,
                        downloadProgress = 100,
                        status = "COMPLETED",
                        category = "IMAGE",
                        isSyncedToCloud = true,
                        cloudProvider = "Dropbox"
                    )
                )
                samples.forEach { database.mediaDao().insert(it) }
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun loadUrlInBrowser(url: String) {
        var formatted = url.trim()
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = if (formatted.contains(".") && !formatted.contains(" ")) {
                "https://$formatted"
            } else {
                "https://duckduckgo.com/?q=${formatted.replace(" ", "+")}"
            }
        }
        _browserUrl.value = formatted
        _detectedMediaList.value = emptyList()
        _currentTab.value = AppTab.BROWSER
    }

    fun updateBrowserInfo(url: String, title: String, progress: Int) {
        _browserUrl.value = url
        if (title.isNotBlank()) _browserTitle.value = title
        _browserProgress.value = progress
    }

    fun toggleDesktopMode() {
        _isDesktopMode.value = !_isDesktopMode.value
    }

    fun addDetectedMedia(media: DetectedMedia) {
        val current = _detectedMediaList.value
        // Clean URL matching (ignore query token variations for deduplication if identical path)
        val normalizedIncoming = media.url.substringBefore('#')
        if (current.none { it.url.substringBefore('#') == normalizedIncoming }) {
            // Put videos & audios before generic images
            val newList = (listOf(media) + current).take(50).sortedWith(
                compareBy(
                    { when (it.mediaType) {
                        MediaType.VIDEO -> 0
                        MediaType.AUDIO -> 1
                        MediaType.IMAGE -> 2
                        MediaType.OTHER -> 3
                    } },
                    { -it.detectedAt }
                )
            )
            _detectedMediaList.value = newList
        }
    }

    fun requestPageScan() {
        _isSniffingActive.value = true
        _deepScanTrigger.value = System.currentTimeMillis()
        MediaDetectionHub.schedulePageScan(
            context = getApplication(),
            pageUrl = _browserUrl.value,
            pageTitle = _browserTitle.value,
            userAgent = if (_isDesktopMode.value) "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36" else null,
            forceImmediate = true
        )
    }

    fun setSniffingActive(active: Boolean) {
        _isSniffingActive.value = active
    }

    fun setPreviewMedia(media: DetectedMedia?) {
        _previewDetectedMedia.value = media
    }

    fun clearDetectedMedia() {
        _detectedMediaList.value = emptyList()
    }

    fun openMediaSniffer(open: Boolean) {
        _isMediaSnifferOpen.value = open
    }

    fun downloadAllDetectedMedia() {
        val list = _detectedMediaList.value
        list.forEach { item ->
            downloadMedia(item)
        }
        _isMediaSnifferOpen.value = false
        _currentTab.value = AppTab.DOWNLOADS
    }

    fun downloadMedia(item: DetectedMedia) {
        val category = when (item.mediaType) {
            MediaType.VIDEO -> "VIDEO"
            MediaType.AUDIO -> "AUDIO"
            MediaType.IMAGE -> "IMAGE"
            MediaType.OTHER -> "OTHER"
        }
        repository.downloadManager.startDownload(
            sourceUrl = item.url,
            title = item.title,
            mimeType = item.mimeType,
            category = category
        )
        _isMediaSnifferOpen.value = false
        _currentTab.value = AppTab.DOWNLOADS
    }

    fun downloadDirectUrl(url: String, title: String, category: String = "VIDEO") {
        repository.downloadManager.startDownload(
            sourceUrl = url,
            title = title.ifBlank { "Download_${System.currentTimeMillis()}" },
            mimeType = if (category == "AUDIO") "audio/mpeg" else "video/mp4",
            category = category
        )
        _currentTab.value = AppTab.DOWNLOADS
    }

    fun pauseDownload(id: Long) {
        repository.downloadManager.pauseDownload(id)
    }

    fun resumeDownload(id: Long) {
        repository.downloadManager.resumeDownload(id)
    }

    fun cancelDownload(id: Long) {
        repository.downloadManager.cancelDownload(id)
    }

    fun openMediaPlayer(media: DownloadedMedia?) {
        _selectedMediaForPlayer.value = media
    }

    fun openRenameDialog(media: DownloadedMedia?) {
        _renameTarget.value = media
    }

    fun renameMedia(id: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameMedia(id, newTitle)
            _renameTarget.value = null
        }
    }

    fun deleteMedia(id: Long) {
        viewModelScope.launch {
            repository.deleteMedia(id)
        }
    }

    fun syncMediaToCloud(media: DownloadedMedia, provider: String) {
        viewModelScope.launch {
            repository.syncMediaToCloud(media.id, provider)
        }
    }

    fun syncMediaToFirestore(media: DownloadedMedia) {
        viewModelScope.launch {
            firestoreSyncManager.pushSingleMediaToFirestore(media)
        }
    }

    fun openAddBookmark(open: Boolean) {
        _isAddBookmarkOpen.value = open
    }

    fun addBookmark(title: String, url: String, category: String, isAdult: Boolean = false) {
        viewModelScope.launch {
            val bookmark = WebBookmark(
                title = title.ifBlank { "Bookmark" },
                url = if (url.startsWith("http")) url else "https://$url",
                category = category.ifBlank { "Custom" },
                description = "Custom bookmarked site",
                iconName = "bookmark",
                isPinned = false,
                isAdultCategory = isAdult
            )
            repository.addBookmark(bookmark)
            _isAddBookmarkOpen.value = false
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
        }
    }

    // Security Methods
    fun verifyPin(pin: String): Boolean {
        return securityPrefs.verifyPin(pin)
    }

    fun setPin(pin: String) {
        securityPrefs.setPin(pin)
    }

    fun setPinEnabled(enabled: Boolean) {
        securityPrefs.setPinEnabled(enabled)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        securityPrefs.setBiometricEnabled(enabled)
    }

    fun unlockApp() {
        securityPrefs.unlockApp()
    }

    fun lockApp() {
        securityPrefs.lockApp()
    }

    fun clearSecurity() {
        securityPrefs.clearSecurity()
    }

    // Battery Optimization Methods
    fun checkBatteryOptimizationStatus() {
        _isBatteryRestricted.value = com.example.data.util.BatteryOptimizationHelper.isBatteryOptimizationRestricted(getApplication())
    }

    fun dismissBatteryAlert() {
        _isBatteryAlertDismissed.value = true
    }

    fun openBatteryOptimizationSettings() {
        com.example.data.util.BatteryOptimizationHelper.openBatteryOptimizationSettings(getApplication())
    }
}
