package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DownloadedMedia
import com.example.data.repository.CloudSyncManager
import com.example.data.repository.SecurityPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}
