package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.data.model.DownloadedMedia
import com.example.ui.components.BrowserView
import com.example.ui.components.CloudSyncTab
import com.example.ui.components.DownloadsTab
import com.example.ui.components.FileManagementTab
import com.example.ui.components.LockScreen
import com.example.ui.components.MediaPlayerDialog
import com.example.ui.components.MediaPreviewDialog
import com.example.ui.components.MediaSnifferBottomSheet
import com.example.ui.components.RenameMediaDialog
import com.example.ui.components.MoveMediaDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.SettingsTab
import com.example.ui.components.SitesHubTab
import com.example.ui.components.SnifferDownloadBottomSheet
import com.example.ui.components.SnifferDownloadFloatingBar
import com.example.ui.theme.MediaFetchTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.MainViewModel

class MainActivity : FragmentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            android.system.Os.setenv("MESA_LOG_FILE", "/dev/null", true)
            android.system.Os.setenv("MESA_LOG_LEVEL", "none", true)
            android.system.Os.setenv("MESA_NO_ERROR", "1", true)
            android.system.Os.setenv("MESA_DEBUG", "silent", true)
            android.system.Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
            android.system.Os.setenv("LIBGL_DRI3_DISABLE", "1", true)
            android.system.Os.setenv("GALLIUM_DRIVER", "llvmpipe", true)
            android.system.Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "llvmpipe", true)
        } catch (_: Throwable) {}

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.data.util.ChromiumCacheHelper.prepareDirectories(this)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MainViewModel::class.java]

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val accentColor by viewModel.accentColor.collectAsState()

            MediaFetchTheme(
                themeMode = themeMode,
                accentColor = accentColor
            ) {
                MainAppScreen(
                    viewModel = viewModel,
                    onShareMedia = { media -> shareMedia(media) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.checkBatteryOptimizationStatus()
        }
    }

    override fun onStop() {
        super.onStop()
        // Lock app on minimize if security lock is enabled
        viewModel.lockApp()
    }

    private fun shareMedia(media: DownloadedMedia) {
        try {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Shared from MediaFetch: ${media.title}\nSource: ${media.sourceUrl}")
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Share Media")
            startActivity(shareIntent)
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to share media", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    onShareMedia: (DownloadedMedia) -> Unit
) {
    val isAppLocked by viewModel.isAppLocked.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val browserUrl by viewModel.browserUrl.collectAsState()
    val isDesktopMode by viewModel.isDesktopMode.collectAsState()
    val detectedMediaList by viewModel.detectedMediaList.collectAsState()
    val isMediaSnifferOpen by viewModel.isMediaSnifferOpen.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val completedMedia by viewModel.completedMedia.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val filteredFiles by viewModel.filteredFiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val layoutStyle by viewModel.layoutStyle.collectAsState()
    val selectedMediaForPlayer by viewModel.selectedMediaForPlayer.collectAsState()
    val miniPlayerMedia by viewModel.miniPlayerMedia.collectAsState()
    val renameTarget by viewModel.renameTarget.collectAsState()
    val moveTarget by viewModel.moveTarget.collectAsState()
    val deleteTarget by viewModel.deleteTarget.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val isBatteryRestricted by viewModel.isBatteryRestricted.collectAsState()
    val isBatteryAlertDismissed by viewModel.isBatteryAlertDismissed.collectAsState()
    val isSniffingActive by viewModel.isSniffingActive.collectAsState()
    val deepScanTrigger by viewModel.deepScanTrigger.collectAsState()
    val previewDetectedMedia by viewModel.previewDetectedMedia.collectAsState()
    val media3Queue by viewModel.media3Queue.collectAsState()
    val isAutoQueueMedia3 by viewModel.isAutoQueueMedia3.collectAsState()
    val snifferDownloads by viewModel.snifferDownloads.collectAsState()
    val isSnifferDownloadSheetOpen by viewModel.isSnifferDownloadSheetOpen.collectAsState()
    val isSnifferFloatingBarDismissed by viewModel.isSnifferFloatingBarDismissed.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (!isAppLocked) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.testTag("main_navigation_bar")
                    ) {
                        // Browser Tab
                        NavigationBarItem(
                            selected = currentTab == AppTab.BROWSER,
                            onClick = { viewModel.selectTab(AppTab.BROWSER) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (detectedMediaList.isNotEmpty()) {
                                            Badge { Text("${detectedMediaList.size}") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (currentTab == AppTab.BROWSER) Icons.Filled.Language else Icons.Outlined.Language,
                                        contentDescription = "Browser"
                                    )
                                }
                            },
                            label = { Text("Browser", fontSize = 11.sp) },
                            modifier = Modifier.testTag("nav_item_browser")
                        )

                        // Downloads Tab
                        NavigationBarItem(
                            selected = currentTab == AppTab.DOWNLOADS,
                            onClick = { viewModel.selectTab(AppTab.DOWNLOADS) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (activeDownloads.isNotEmpty()) {
                                            Badge { Text("${activeDownloads.size}") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (currentTab == AppTab.DOWNLOADS) Icons.Filled.Download else Icons.Outlined.Download,
                                        contentDescription = "Downloads"
                                    )
                                }
                            },
                            label = { Text("Downloads", fontSize = 11.sp) },
                            modifier = Modifier.testTag("nav_item_downloads")
                        )

                        // Files Tab
                        NavigationBarItem(
                            selected = currentTab == AppTab.FILES,
                            onClick = { viewModel.selectTab(AppTab.FILES) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.FILES) Icons.Filled.Folder else Icons.Outlined.Folder,
                                    contentDescription = "Files"
                                )
                            },
                            label = { Text("Files", fontSize = 11.sp) },
                            modifier = Modifier.testTag("nav_item_files")
                        )

                        // Sites Hub Tab
                        NavigationBarItem(
                            selected = currentTab == AppTab.SITES,
                            onClick = { viewModel.selectTab(AppTab.SITES) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.SITES) Icons.Filled.Public else Icons.Outlined.Public,
                                    contentDescription = "Sites Hub"
                                )
                            },
                            label = { Text("Sites Hub", fontSize = 11.sp) },
                            modifier = Modifier.testTag("nav_item_sites")
                        )

                        // Cloud Sync Tab
                        NavigationBarItem(
                            selected = currentTab == AppTab.SYNC,
                            onClick = { viewModel.selectTab(AppTab.SYNC) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.SYNC) Icons.Filled.CloudSync else Icons.Outlined.CloudSync,
                                    contentDescription = "Sync"
                                )
                            },
                            label = { Text("Sync", fontSize = 11.sp) },
                            modifier = Modifier.testTag("nav_item_sync")
                        )

                        // Settings Tab
                        NavigationBarItem(
                            selected = currentTab == AppTab.SETTINGS,
                            onClick = { viewModel.selectTab(AppTab.SETTINGS) },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = "Settings"
                                )
                            },
                            label = { Text("Settings", fontSize = 11.sp) },
                            modifier = Modifier.testTag("nav_item_settings")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
                    when (tab) {
                        AppTab.BROWSER -> {
                            BrowserView(
                                initialUrl = browserUrl,
                                detectedMediaList = detectedMediaList,
                                isDesktopMode = isDesktopMode,
                                isSniffingActive = isSniffingActive,
                                deepScanTrigger = deepScanTrigger,
                                onUrlChanged = { url, title, progress ->
                                    viewModel.updateBrowserInfo(url, title, progress)
                                },
                                onMediaDetected = { media ->
                                    viewModel.addDetectedMedia(media)
                                },
                                onOpenMediaSniffer = {
                                    viewModel.openMediaSniffer(true)
                                },
                                onRequestPageScan = {
                                    viewModel.requestPageScan()
                                },
                                onQuickDirectDownload = { url, title ->
                                    viewModel.downloadDirectUrl(url, title)
                                },
                                onToggleDesktopMode = {
                                    viewModel.toggleDesktopMode()
                                },
                                onDirectDownloadMedia = { media ->
                                    viewModel.downloadMedia(media)
                                },
                                onPreviewDetectedMedia = { media ->
                                    viewModel.setPreviewMedia(media)
                                },
                                bookmarks = bookmarks,
                                onSaveBookmark = { title, url, category, isPinned ->
                                    viewModel.saveBookmark(title, url, category, isPinned)
                                },
                                onDeleteBookmark = { id ->
                                    viewModel.deleteBookmark(id)
                                },
                                onDeleteBookmarkByUrl = { url ->
                                    viewModel.deleteBookmarkByUrl(url)
                                }
                            )
                        }

                        AppTab.DOWNLOADS -> {
                            DownloadsTab(
                                activeDownloads = activeDownloads,
                                completedDownloads = completedMedia,
                                isBatteryRestricted = isBatteryRestricted,
                                isBatteryAlertDismissed = isBatteryAlertDismissed,
                                media3Queue = media3Queue,
                                isAutoQueueMedia3 = isAutoQueueMedia3,
                                onToggleAutoQueueMedia3 = { viewModel.setAutoQueueMedia3(it) },
                                onPauseMedia3Download = { viewModel.pauseMedia3Download(it) },
                                onResumeMedia3Download = { viewModel.resumeMedia3Download(it) },
                                onRemoveMedia3Download = { viewModel.removeMedia3Download(it) },
                                onOpenBatterySettings = { viewModel.openBatteryOptimizationSettings() },
                                onDismissBatteryAlert = { viewModel.dismissBatteryAlert() },
                                onPauseDownload = { viewModel.pauseDownload(it) },
                                onResumeDownload = { viewModel.resumeDownload(it) },
                                onCancelDownload = { viewModel.cancelDownload(it) },
                                onPlayMedia = { viewModel.openMediaPlayer(it) },
                                onRenameMedia = { viewModel.openRenameDialog(it) },
                                onMoveMedia = { viewModel.openMoveDialog(it) },
                                onDeleteMedia = { id ->
                                    val target = completedMedia.find { it.id == id }
                                        ?: DownloadedMedia(id = id, title = "File", sourceUrl = "")
                                    viewModel.openDeleteDialog(target)
                                },
                                onSyncToCloud = { media, provider ->
                                    if (provider == "Firestore") viewModel.syncMediaToFirestore(media)
                                    else viewModel.syncMediaToCloud(media, provider)
                                },
                                onShareMedia = onShareMedia,
                                onNavigateToBrowser = { viewModel.selectTab(AppTab.BROWSER) }
                            )
                        }

                        AppTab.FILES -> {
                            FileManagementTab(
                                files = filteredFiles,
                                searchQuery = searchQuery,
                                selectedCategory = selectedCategoryFilter,
                                sortOption = sortOption,
                                layoutStyle = layoutStyle,
                                onSearchChange = { viewModel.searchQuery.value = it },
                                onCategoryChange = { viewModel.selectedCategoryFilter.value = it },
                                onSortChange = { viewModel.sortOption.value = it },
                                onLayoutChange = { viewModel.layoutStyle.value = it },
                                onPlayMedia = { viewModel.playInMiniPlayer(it) },
                                onRenameClick = { viewModel.openRenameDialog(it) },
                                onMoveClick = { viewModel.openMoveDialog(it) },
                                onDeleteMedia = { id ->
                                    val target = filteredFiles.find { it.id == id }
                                        ?: completedMedia.find { it.id == id }
                                        ?: DownloadedMedia(id = id, title = "File", sourceUrl = "")
                                    viewModel.openDeleteDialog(target)
                                },
                                onSyncCloud = { viewModel.syncMediaToFirestore(it) },
                                onShareMedia = onShareMedia,
                                miniPlayerMedia = miniPlayerMedia,
                                onMiniPlayerClose = { viewModel.closeMiniPlayer() },
                                onOpenFullscreenPlayer = { viewModel.openMediaPlayer(it) }
                            )
                        }

                        AppTab.SITES -> {
                            SitesHubTab(
                                bookmarks = bookmarks,
                                onSelectSite = { url ->
                                    viewModel.loadUrlInBrowser(url)
                                },
                                onAddBookmark = { title, url, category, isAdult ->
                                    viewModel.addBookmark(title, url, category, isAdult)
                                },
                                onDeleteBookmark = { id ->
                                    viewModel.deleteBookmark(id)
                                }
                            )
                        }

                        AppTab.SYNC -> {
                            CloudSyncTab(
                                cloudSyncManager = viewModel.repository.cloudSyncManager,
                                firestoreSyncManager = viewModel.repository.firestoreSyncManager,
                                mediaList = completedMedia,
                                onImportSuccess = { importedList ->
                                    // Media list updated
                                }
                            )
                        }

                        AppTab.SETTINGS -> {
                            SettingsTab(
                                themeMode = themeMode,
                                accentColor = accentColor,
                                layoutStyle = layoutStyle,
                                isPinEnabled = viewModel.isPinEnabled,
                                isBiometricEnabled = viewModel.isBiometricEnabled,
                                canUseBiometric = viewModel.canUseBiometric,
                                hasPinSet = viewModel.hasPinSet,
                                isBatteryRestricted = isBatteryRestricted,
                                mediaList = completedMedia,
                                onNavigateToCategory = { category ->
                                    viewModel.selectedCategoryFilter.value = category
                                    viewModel.selectTab(AppTab.FILES)
                                },
                                onOpenBatterySettings = { viewModel.openBatteryOptimizationSettings() },
                                onThemeModeChange = { viewModel.setThemeMode(it) },
                                onAccentColorChange = { viewModel.setAccentColor(it) },
                                onLayoutStyleChange = { viewModel.layoutStyle.value = it },
                                onSetPinEnabled = { viewModel.setPinEnabled(it) },
                                onSetBiometricEnabled = { viewModel.setBiometricEnabled(it) },
                                onUpdatePin = { viewModel.setPin(it) },
                                onClearSecurity = { viewModel.clearSecurity() }
                            )
                        }
                    }
                }

                // Floating Status Bar tracking active downloads using WorkManager progress
                SnifferDownloadFloatingBar(
                    activeDownloads = snifferDownloads,
                    isDismissed = isSnifferFloatingBarDismissed,
                    onExpandSheet = { viewModel.openSnifferDownloadSheet(true) },
                    onDismiss = { viewModel.dismissSnifferFloatingBar() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // Lock Screen overlay
        AnimatedVisibility(
            visible = isAppLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LockScreen(
                onUnlockSuccess = { viewModel.unlockApp() },
                onVerifyPin = { pin -> viewModel.verifyPin(pin) },
                onSetPin = { newPin -> viewModel.setPin(newPin) },
                hasPinSet = viewModel.hasPinSet,
                isBiometricEnabled = viewModel.isBiometricEnabled,
                canUseBiometric = viewModel.canUseBiometric,
                onEmergencyReset = { viewModel.clearSecurity() }
            )
        }

        // Media Sniffer Bottom Sheet
        if (isMediaSnifferOpen) {
            MediaSnifferBottomSheet(
                mediaList = detectedMediaList,
                isScanning = isSniffingActive,
                onDismiss = { viewModel.openMediaSniffer(false) },
                onDownload = { item -> viewModel.downloadMedia(item) },
                onDownloadAll = { viewModel.downloadAllDetectedMedia() },
                onQueueMedia3 = { item -> viewModel.enqueueMedia3Media(item) },
                onPreview = { item -> viewModel.setPreviewMedia(item) },
                onRescanPage = { viewModel.requestPageScan() },
                onClearMedia = { viewModel.clearDetectedMedia() }
            )
        }

        // Detected Media Preview Modal
        previewDetectedMedia?.let { media ->
            MediaPreviewDialog(
                media = media,
                onDismiss = { viewModel.setPreviewMedia(null) },
                onDownload = {
                    viewModel.downloadMedia(media)
                    viewModel.setPreviewMedia(null)
                }
            )
        }

        // In-App Media Player Modal (for downloaded files)
        selectedMediaForPlayer?.let { media ->
            MediaPlayerDialog(
                media = media,
                onDismiss = { viewModel.openMediaPlayer(null) }
            )
        }

        // Rename dialog
        renameTarget?.let { media ->
            RenameMediaDialog(
                media = media,
                onDismiss = { viewModel.openRenameDialog(null) },
                onConfirm = { newTitle -> viewModel.renameMedia(media.id, newTitle) }
            )
        }

        // Move dialog
        moveTarget?.let { media ->
            MoveMediaDialog(
                media = media,
                onDismiss = { viewModel.openMoveDialog(null) },
                onConfirm = { targetCategory -> viewModel.moveMedia(media.id, targetCategory) }
            )
        }

        // Delete confirmation dialog
        deleteTarget?.let { media ->
            DeleteConfirmDialog(
                media = media,
                onDismiss = { viewModel.openDeleteDialog(null) },
                onConfirm = { viewModel.deleteMedia(media.id) }
            )
        }

        // Sniffer Download Progress Bottom Sheet (WorkManager Progress Tracking)
        if (isSnifferDownloadSheetOpen) {
            SnifferDownloadBottomSheet(
                activeDownloads = snifferDownloads,
                onDismiss = { viewModel.openSnifferDownloadSheet(false) },
                onPauseDownload = { id -> viewModel.pauseDownload(id) },
                onResumeDownload = { id -> viewModel.resumeDownload(id) },
                onCancelDownload = { id -> viewModel.cancelDownload(id) },
                onNavigateToDownloads = {
                    viewModel.openSnifferDownloadSheet(false)
                    viewModel.selectTab(AppTab.DOWNLOADS)
                }
            )
        }
    }
}
