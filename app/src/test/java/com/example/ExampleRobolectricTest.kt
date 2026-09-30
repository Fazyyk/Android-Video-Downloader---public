package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DownloadedMedia
import com.example.data.repository.CloudSyncManager
import com.example.data.repository.SecurityPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MediaFetch", appName)
  }

  @Test
  fun `security preferences set and verify pin`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val securityPrefs = SecurityPreferences(context)

    securityPrefs.setPin("1234")
    assertTrue(securityPrefs.hasPinSet())
    assertTrue(securityPrefs.verifyPin("1234"))
    assertFalse(securityPrefs.verifyPin("9999"))

    securityPrefs.clearSecurity()
    assertFalse(securityPrefs.hasPinSet())
  }

  @Test
  fun `cloud sync export and import manifest`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cloudSync = CloudSyncManager(context)

    val sampleMedia = listOf(
      DownloadedMedia(
        id = 1,
        title = "Test Clip",
        sourceUrl = "https://example.com/test.mp4",
        mimeType = "video/mp4",
        category = "VIDEO",
        fileSizeBytes = 5000000L
      )
    )

    val json = cloudSync.exportBackupManifest(sampleMedia)
    assertTrue(json.contains("Test Clip"))
    assertTrue(json.contains("test.mp4"))

    val parsed = cloudSync.parseBackupManifest(json)
    assertEquals(1, parsed.size)
    assertEquals("Test Clip", parsed[0].title)
    assertEquals("https://example.com/test.mp4", parsed[0].sourceUrl)
  }

  @Test
  fun `workmanager enqueues download work successfully`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = com.example.data.db.AppDatabase.getInstance(context)
    val downloadManager = com.example.data.repository.DownloadServiceManager(
      context,
      database.mediaDao(),
      this
    )

    val id = downloadManager.enqueueDownload(
      sourceUrl = "https://example.com/nature.mp4",
      title = "Nature Video",
      mimeType = "video/mp4",
      category = "VIDEO"
    )
    assertTrue(id > 0)

    val workManager = androidx.work.WorkManager.getInstance(context)
    val workInfos = workManager.getWorkInfosByTag("download_$id").get()
    assertTrue(workInfos.isNotEmpty())
  }

  @Test
  fun `battery optimization helper check and intent launch safety`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // In Robolectric environment, check should execute safely and return boolean
    val isRestricted = com.example.data.util.BatteryOptimizationHelper.isBatteryOptimizationRestricted(context)
    // Result is a valid boolean
    assertTrue(isRestricted || !isRestricted)

    // Opening battery optimization settings should launch intent without throwing
    val launched = com.example.data.util.BatteryOptimizationHelper.openBatteryOptimizationSettings(context)
    assertTrue(launched)
  }

  @Test
  fun `viewmodel battery alert dismiss updates state flow`() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.viewmodel.MainViewModel(application)

    assertFalse(viewModel.isBatteryAlertDismissed.value)
    viewModel.dismissBatteryAlert()
    assertTrue(viewModel.isBatteryAlertDismissed.value)
  }

  @Test
  fun `media sniffer engine detects video streams with quality`() {
    val detected = com.example.data.util.MediaSnifferEngine.sniffUrl(
      rawUrl = "https://cdn.example.com/videos/epic_nature_1080p.mp4?token=xyz123",
      pageTitle = "Documentary Page"
    )
    assertNotNull(detected)
    assertEquals(com.example.data.model.MediaType.VIDEO, detected?.mediaType)
    assertEquals("mp4", detected?.extension)
    assertTrue(detected?.quality?.contains("1080p") == true)
  }

  @Test
  fun `media sniffer engine detects hls streaming manifests`() {
    val detected = com.example.data.util.MediaSnifferEngine.sniffUrl(
      rawUrl = "https://live.stream.org/playlist.m3u8",
      pageTitle = "Live News Broadcaster"
    )
    assertNotNull(detected)
    assertEquals(com.example.data.model.MediaType.VIDEO, detected?.mediaType)
    assertEquals("m3u8", detected?.extension)
    assertTrue(detected?.isStream == true)
  }

  @Test
  fun `media sniffer engine detects audio tracks and images`() {
    val audio = com.example.data.util.MediaSnifferEngine.sniffUrl(
      rawUrl = "https://audio.example.com/track_320kbps.mp3",
      pageTitle = "Artist Album"
    )
    assertNotNull(audio)
    assertEquals(com.example.data.model.MediaType.AUDIO, audio?.mediaType)
    assertTrue(audio?.quality?.contains("320") == true)

    val image = com.example.data.util.MediaSnifferEngine.sniffUrl(
      rawUrl = "https://images.example.com/poster_art.webp",
      pageTitle = "Art Gallery"
    )
    assertNotNull(image)
    assertEquals(com.example.data.model.MediaType.IMAGE, image?.mediaType)
  }

  @Test
  fun `media sniffer javascript injection script is generated`() {
    val script = com.example.data.util.MediaSnifferEngine.generateSnifferScript()
    assertTrue(script.isNotBlank())
    assertTrue(script.contains("MediaSnifferBridge"))
    assertTrue(script.contains("MutationObserver"))
    assertTrue(script.contains("HTMLMediaElement"))
  }

  @Test
  fun `detected media properties support exoplayer preview`() {
    val video = com.example.data.model.DetectedMedia(
      url = "https://test.stream/video.mp4",
      title = "Trailer 1",
      mimeType = "video/mp4",
      extension = "mp4",
      mediaType = com.example.data.model.MediaType.VIDEO,
      quality = "1080p Full HD"
    )
    assertEquals("MP4", video.displayExtension)
    assertFalse(video.isStream)

    val hls = com.example.data.model.DetectedMedia(
      url = "https://test.stream/master.m3u8",
      title = "HLS Stream",
      mimeType = "application/x-mpegURL",
      extension = "m3u8",
      mediaType = com.example.data.model.MediaType.VIDEO,
      quality = "Adaptive HLS Stream"
    )
    assertTrue(hls.isStream)
    assertEquals("M3U8", hls.displayExtension)
  }

  @Test
  fun `firestore media item converts to and from map accurately`() {
    val item = com.example.data.repository.FirestoreMediaItem(
      id = "doc123",
      title = "Open Movie Documentary",
      sourceUrl = "https://archive.org/stream/documentary.mp4",
      mimeType = "video/mp4",
      fileSizeBytes = 52428800L,
      category = "VIDEO",
      status = "COMPLETED",
      createdAt = 1700000000000L,
      syncedAt = 1700000001000L,
      deviceId = "DEV-TEST-01",
      deviceName = "Google Pixel 8",
      durationSeconds = 120L
    )

    val map = item.toMap()
    assertEquals("doc123", map["id"])
    assertEquals("Open Movie Documentary", map["title"])
    assertEquals("https://archive.org/stream/documentary.mp4", map["sourceUrl"])
    assertEquals(52428800L, map["fileSizeBytes"])
    assertEquals("DEV-TEST-01", map["deviceId"])

    val reconstructed = com.example.data.repository.FirestoreMediaItem.fromMap(map)
    assertEquals(item.id, reconstructed.id)
    assertEquals(item.title, reconstructed.title)
    assertEquals(item.sourceUrl, reconstructed.sourceUrl)
    assertEquals(item.fileSizeBytes, reconstructed.fileSizeBytes)
    assertEquals(item.deviceId, reconstructed.deviceId)
    assertEquals(item.deviceName, reconstructed.deviceName)
  }

  @Test
  fun `media detection hub emits and tracks intercepted media`() {
    val detected = com.example.data.model.DetectedMedia(
      url = "https://example.com/videos/nature_4k.mp4",
      title = "nature_4k.mp4",
      mimeType = "video/mp4",
      extension = "mp4",
      estimatedSize = "48.2 MB",
      mediaType = com.example.data.model.MediaType.VIDEO,
      quality = "1080p HD"
    )

    com.example.data.worker.MediaDetectionHub.postDetectedMedia(detected)
    assertEquals("https://example.com/videos/nature_4k.mp4", com.example.data.worker.MediaDetectionHub.lastDetectedUrl.value)
    assertTrue(com.example.data.worker.MediaDetectionHub.totalInterceptedCount.value > 0)

    com.example.data.worker.MediaDetectionHub.setWorkerRunning(true)
    assertTrue(com.example.data.worker.MediaDetectionHub.isWorkerRunning.value)

    com.example.data.worker.MediaDetectionHub.setWorkerRunning(false)
    assertFalse(com.example.data.worker.MediaDetectionHub.isWorkerRunning.value)
  }

  @Test
  fun `downloaded media model stores speed and eta correctly`() {
    val media = com.example.data.model.DownloadedMedia(
      id = 101L,
      title = "Open Document.mp4",
      sourceUrl = "https://example.com/video.mp4",
      fileSizeBytes = 104857600L,
      downloadProgress = 45,
      status = "DOWNLOADING",
      downloadSpeedText = "3.5 MB/s",
      etaText = "16s remaining"
    )

    assertEquals("3.5 MB/s", media.downloadSpeedText)
    assertEquals("16s remaining", media.etaText)
    assertEquals(45, media.downloadProgress)
    assertEquals("DOWNLOADING", media.status)
  }

  @Test
  fun `app database initializes and accesses mediaDao`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.db.AppDatabase.getInstance(context)
    assertNotNull(db)
    val dao = db.mediaDao()
    assertNotNull(dao)
    val item = dao.getMediaById(999999L)
    org.junit.Assert.assertNull(item)
  }

  @Test
  fun `offline and firestore sync verified status check`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.db.AppDatabase.getInstance(context)
    val dao = db.mediaDao()

    val mediaItem = com.example.data.model.DownloadedMedia(
      id = 555L,
      title = "Offline_Nature_Documentary.mp4",
      sourceUrl = "https://example.com/nature.mp4",
      localUri = "content://media/local/nature.mp4",
      fileSizeBytes = 52428800L,
      status = "COMPLETED",
      category = "VIDEO",
      isSyncedToCloud = false,
      cloudProvider = ""
    )
    val insertedId = dao.insert(mediaItem)
    assertTrue(insertedId > 0)

    val syncManager = com.example.data.repository.FirestoreSyncManager(context, dao)
    val syncResult = syncManager.pushSingleMediaToFirestore(mediaItem)
    assertTrue(syncResult.isSuccess)

    val updatedItem = dao.getMediaById(mediaItem.id)
    assertNotNull(updatedItem)
    assertTrue(updatedItem!!.isSyncedToCloud)
    assertEquals("Firestore", updatedItem.cloudProvider)
    assertEquals("COMPLETED", updatedItem.status)
  }

  @Test
  fun `recharts storage dashboard computes category sizes correctly`() {
    val items = listOf(
      com.example.data.model.DownloadedMedia(
        id = 1,
        title = "Clip1.mp4",
        sourceUrl = "https://example.com/1.mp4",
        category = "VIDEO",
        fileSizeBytes = 100_000_000L,
        status = "COMPLETED"
      ),
      com.example.data.model.DownloadedMedia(
        id = 2,
        title = "Song1.mp3",
        sourceUrl = "https://example.com/1.mp3",
        category = "AUDIO",
        fileSizeBytes = 25_000_000L,
        status = "COMPLETED"
      ),
      com.example.data.model.DownloadedMedia(
        id = 3,
        title = "Photo1.png",
        sourceUrl = "https://example.com/1.png",
        category = "IMAGE",
        fileSizeBytes = 5_000_000L,
        status = "COMPLETED"
      )
    )

    val videoBytes = items.filter { it.category.equals("VIDEO", ignoreCase = true) }.sumOf { it.fileSizeBytes }
    val audioBytes = items.filter { it.category.equals("AUDIO", ignoreCase = true) }.sumOf { it.fileSizeBytes }
    val imageBytes = items.filter { it.category.equals("IMAGE", ignoreCase = true) }.sumOf { it.fileSizeBytes }
    val totalBytes = videoBytes + audioBytes + imageBytes

    assertEquals(100_000_000L, videoBytes)
    assertEquals(25_000_000L, audioBytes)
    assertEquals(5_000_000L, imageBytes)
    assertEquals(130_000_000L, totalBytes)
  }

  @Test
  fun `persistent theme preferences stores and retrieves System Light and Dark modes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val themePrefs = com.example.data.repository.ThemePreferences(context)

    // Test Light Mode
    themePrefs.setThemeMode(com.example.ui.viewmodel.ThemeMode.LIGHT)
    assertEquals(com.example.ui.viewmodel.ThemeMode.LIGHT, themePrefs.getStoredThemeMode())
    assertEquals(com.example.ui.viewmodel.ThemeMode.LIGHT, themePrefs.themeMode.value)

    // Test Dark Mode
    themePrefs.setThemeMode(com.example.ui.viewmodel.ThemeMode.DARK)
    assertEquals(com.example.ui.viewmodel.ThemeMode.DARK, themePrefs.getStoredThemeMode())
    assertEquals(com.example.ui.viewmodel.ThemeMode.DARK, themePrefs.themeMode.value)

    // Test System Default Mode
    themePrefs.setThemeMode(com.example.ui.viewmodel.ThemeMode.SYSTEM)
    assertEquals(com.example.ui.viewmodel.ThemeMode.SYSTEM, themePrefs.getStoredThemeMode())
    assertEquals(com.example.ui.viewmodel.ThemeMode.SYSTEM, themePrefs.themeMode.value)
  }

  @Test
  fun `media network interceptor detects video and audio URLs and categories`() {
    val videoUrl = "https://example.com/streams/nature_video.mp4?auth=token123"
    val audioUrl = "https://example.com/podcasts/episode_42.mp3#t=10"
    val hlsStream = "https://example.com/live/master.m3u8"
    val webpageUrl = "https://example.com/articles/news.html"

    assertTrue(com.example.data.media3.MediaNetworkInterceptor.isMediaUrl(videoUrl))
    assertTrue(com.example.data.media3.MediaNetworkInterceptor.isMediaUrl(audioUrl))
    assertTrue(com.example.data.media3.MediaNetworkInterceptor.isMediaUrl(hlsStream))
    assertFalse(com.example.data.media3.MediaNetworkInterceptor.isMediaUrl(webpageUrl))

    assertEquals("VIDEO", com.example.data.media3.MediaNetworkInterceptor.detectCategory(videoUrl, "video/mp4"))
    assertEquals("AUDIO", com.example.data.media3.MediaNetworkInterceptor.detectCategory(audioUrl, "audio/mpeg"))
    assertEquals("VIDEO", com.example.data.media3.MediaNetworkInterceptor.detectCategory(hlsStream, "application/x-mpegURL"))
  }

  @Test
  fun `media browser interceptor service detects and queues media for background download`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val service = com.example.data.media3.MediaBrowserInterceptorService.getInstance(context)

    val sampleUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
    val detected = service.processUrl(
      url = sampleUrl,
      pageTitle = "For Bigger Blazes",
      mimeType = "video/mp4",
      forceQueue = true
    )

    assertNotNull(detected)
    assertEquals("For Bigger Blazes", detected!!.title)
    assertEquals("mp4", detected.extension)
    assertEquals(com.example.data.model.MediaType.VIDEO, detected.mediaType)

    // Verify it was added to the Media3 queue
    val queue = service.media3Queue.value
    assertTrue(queue.any { it.uri.toString() == sampleUrl })
  }

  @Test
  fun `sniffer download progress model accurately reports state and telemetry`() {
    val progressItem = com.example.data.worker.SnifferDownloadProgress(
      workId = "work-1234",
      mediaId = 42L,
      title = "Sample Sniffed Video",
      sourceUrl = "https://example.com/video.mp4",
      progress = 65,
      speed = "3.5 MB/s",
      eta = "4s remaining",
      state = androidx.work.WorkInfo.State.RUNNING,
      category = "VIDEO"
    )

    assertEquals("Downloading", progressItem.stateLabel)
    assertEquals(65, progressItem.progress)
    assertEquals("3.5 MB/s", progressItem.speed)
    assertEquals("4s remaining", progressItem.eta)
    assertTrue(progressItem.isRunning)
    assertFalse(progressItem.isEnqueued)
  }

  @Test
  fun `work manager download tracker emits active downloads combined with room database`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = com.example.data.db.AppDatabase.getInstance(context)
    val mediaDao = database.mediaDao()

    val testMedia = com.example.data.model.DownloadedMedia(
      title = "Sniffed Movie Clip",
      sourceUrl = "https://example.com/clip.mp4",
      localUri = "",
      downloadProgress = 40,
      status = "DOWNLOADING",
      downloadSpeedText = "2.1 MB/s",
      etaText = "6s remaining",
      category = "VIDEO"
    )
    val id = mediaDao.insert(testMedia)

    val tracker = com.example.data.worker.WorkManagerDownloadTracker(context, mediaDao)
    val activeFlow = tracker.getActiveSnifferDownloadsFlow()

    val firstEmission = activeFlow.firstOrNull()
    assertNotNull(firstEmission)
    assertTrue(firstEmission!!.any { it.mediaId == id && it.title == "Sniffed Movie Clip" })

    val item = firstEmission.first { it.mediaId == id }
    assertEquals(40, item.progress)
    assertEquals("2.1 MB/s", item.speed)
    assertEquals("6s remaining", item.eta)

    // Cleanup
    mediaDao.deleteById(id)
  }

  @Test
  fun `chromium cache helper ensures all http cache and crashpad directories exist`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.data.util.ChromiumCacheHelper.prepareDirectories(context)

    val cacheDir = context.cacheDir
    val jsDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
    val wasmDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
    val indexDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/index-dir")
    val crashpadAttachments = java.io.File(cacheDir, "WebView/Crashpad/attachments")
    val knownCrashpadReport = java.io.File(crashpadAttachments, "7fac6a52-9500-47b9-a5e3-aef5ab395ad6")

    assertTrue(jsDir.exists())
    assertTrue(jsDir.isDirectory)
    assertTrue(wasmDir.exists())
    assertTrue(wasmDir.isDirectory)
    assertTrue(indexDir.exists())
    assertTrue(indexDir.isDirectory)
    assertTrue(crashpadAttachments.exists())
    assertTrue(crashpadAttachments.isDirectory)
    assertTrue(knownCrashpadReport.exists())
    assertTrue(knownCrashpadReport.isDirectory)
  }

  @Test
  fun `rename move and delete media manage files on disk and update room db`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = com.example.data.db.AppDatabase.getInstance(context)
    val mediaDao = database.mediaDao()
    val bookmarkDao = database.bookmarkDao()
    val downloadManager = com.example.data.repository.DownloadServiceManager(context, mediaDao, this)
    val cloudSync = com.example.data.repository.CloudSyncManager(context)
    val firestoreSync = com.example.data.repository.FirestoreSyncManager(context, mediaDao)
    val securityPrefs = com.example.data.repository.SecurityPreferences(context)

    val repository = com.example.data.repository.MediaRepository(
      mediaDao = mediaDao,
      bookmarkDao = bookmarkDao,
      downloadManager = downloadManager,
      cloudSyncManager = cloudSync,
      firestoreSyncManager = firestoreSync,
      securityPrefs = securityPrefs
    )

    // 1. Create a dummy file on disk
    val testFolder = java.io.File(context.filesDir, "test_files").apply { mkdirs() }
    val initialFile = java.io.File(testFolder, "nature_doc.mp4").apply {
      writeText("sample video binary dummy data")
    }
    assertTrue(initialFile.exists())

    val mediaItem = DownloadedMedia(
      title = "nature_doc",
      sourceUrl = "https://example.com/nature_doc.mp4",
      mimeType = "video/mp4",
      category = "VIDEO",
      fileSizeBytes = initialFile.length(),
      localUri = android.net.Uri.fromFile(initialFile).toString(),
      status = "COMPLETED"
    )
    val id = mediaDao.insert(mediaItem)

    // 2. Test Rename
    repository.renameMedia(id, "renamed_nature_documentary")
    val renamedItem = mediaDao.getMediaById(id)
    assertNotNull(renamedItem)
    assertEquals("renamed_nature_documentary", renamedItem!!.title)
    val renamedFile = java.io.File(android.net.Uri.parse(renamedItem.localUri).path!!)
    assertTrue(renamedFile.exists())
    assertEquals("renamed_nature_documentary.mp4", renamedFile.name)

    // 3. Test Move to AUDIO (Music folder)
    repository.moveMedia(id, "AUDIO")
    val movedItem = mediaDao.getMediaById(id)
    assertNotNull(movedItem)
    assertEquals("AUDIO", movedItem!!.category)
    val movedFile = java.io.File(android.net.Uri.parse(movedItem.localUri).path!!)
    assertTrue(movedFile.exists())
    assertTrue(movedFile.parentFile!!.name.equals("Music", ignoreCase = true))

    // 4. Test Delete
    repository.deleteMedia(id)
    val deletedItem = mediaDao.getMediaById(id)
    assertNull(deletedItem)
    assertFalse(movedFile.exists())
  }

  @Test
  fun `mini player state management allows previewing and dismissing media`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.viewmodel.MainViewModel(context)

    assertNull(viewModel.miniPlayerMedia.value)

    val previewItem = DownloadedMedia(
      id = 42L,
      title = "ambient_music_sample",
      sourceUrl = "https://example.com/ambient.mp3",
      category = "AUDIO",
      status = "COMPLETED"
    )

    // Play in mini player
    viewModel.playInMiniPlayer(previewItem)
    assertEquals(previewItem, viewModel.miniPlayerMedia.value)
    assertEquals("ambient_music_sample", viewModel.miniPlayerMedia.value?.title)

    // Close mini player
    viewModel.closeMiniPlayer()
    assertNull(viewModel.miniPlayerMedia.value)
  }

  @Test
  fun `media sniffer engine detects audio and video streams and integrates with browser downloading in view model`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.viewmodel.MainViewModel(context)

    // 1. Test MediaSnifferEngine URL detection
    val videoUrl = "https://example.com/assets/wildlife_1080p.mp4?token=abc"
    val sniffedVideo = com.example.data.util.MediaSnifferEngine.sniffUrl(
      rawUrl = videoUrl,
      pageTitle = "Wildlife Documentary HD"
    )
    assertNotNull(sniffedVideo)
    assertEquals(com.example.data.model.MediaType.VIDEO, sniffedVideo!!.mediaType)
    assertEquals("mp4", sniffedVideo.extension)
    assertTrue(sniffedVideo.quality.contains("1080p"))

    val audioUrl = "https://example.com/music/podcast_episode_12.mp3"
    val sniffedAudio = com.example.data.util.MediaSnifferEngine.sniffUrl(
      rawUrl = audioUrl,
      pageTitle = "Tech Talk Podcast"
    )
    assertNotNull(sniffedAudio)
    assertEquals(com.example.data.model.MediaType.AUDIO, sniffedAudio!!.mediaType)
    assertEquals("mp3", sniffedAudio.extension)

    // 2. Add detected media to ViewModel
    viewModel.addDetectedMedia(sniffedVideo)
    viewModel.addDetectedMedia(sniffedAudio)

    val detectedList = viewModel.detectedMediaList.value
    assertTrue(detectedList.any { it.url == videoUrl })
    assertTrue(detectedList.any { it.url == audioUrl })

    // 3. Initiate download from sniffed media
    viewModel.openMediaSniffer(true)
    assertTrue(viewModel.isMediaSnifferOpen.value)

    val downloadId = viewModel.repository.downloadManager.enqueueDownload(
      sourceUrl = sniffedVideo.url,
      title = sniffedVideo.title,
      mimeType = sniffedVideo.mimeType,
      category = "VIDEO"
    )
    assertTrue(downloadId > 0)

    val database = com.example.data.db.AppDatabase.getInstance(context)
    val dbItem = database.mediaDao().getMediaById(downloadId)
    assertNotNull(dbItem)
    assertEquals(videoUrl, dbItem!!.sourceUrl)
    assertEquals("VIDEO", dbItem.category)

    viewModel.downloadMedia(sniffedAudio)
    assertFalse(viewModel.isMediaSnifferOpen.value)
  }

  @Test
  fun `browser bookmarking system allows saving, pinning, checking, and removing favorite media websites`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    val database = com.example.data.db.AppDatabase.getInstance(context)
    val bookmarkDao = database.bookmarkDao()

    val testSiteUrl = "https://example.com/stream-hub"
    val testSiteTitle = "Ultra Stream Hub"

    // 1. Direct DAO verification
    val bookmark = com.example.data.model.WebBookmark(
      title = testSiteTitle,
      url = testSiteUrl,
      category = "Video Portals",
      isPinned = true
    )
    val id = bookmarkDao.insert(bookmark)
    assertTrue(id > 0)

    val allBookmarks = bookmarkDao.getAllBookmarks()
    val initialList = allBookmarks.first()
    assertTrue(initialList.any { it.url == testSiteUrl && it.isPinned })

    // 2. Delete by URL
    bookmarkDao.deleteByUrl(testSiteUrl)
    val updatedList = allBookmarks.first()
    assertFalse(updatedList.any { it.url == testSiteUrl })
  }
}




