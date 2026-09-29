package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.model.DetectedMedia
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object MediaDetectionHub {

    private const val TAG = "MediaDetectionHub"

    private val _detectedMediaFlow = MutableSharedFlow<DetectedMedia>(
        extraBufferCapacity = 100,
        replay = 0
    )
    val detectedMediaFlow: SharedFlow<DetectedMedia> = _detectedMediaFlow.asSharedFlow()

    private val _isWorkerRunning = MutableStateFlow(false)
    val isWorkerRunning: StateFlow<Boolean> = _isWorkerRunning.asStateFlow()

    private val _totalInterceptedCount = MutableStateFlow(0)
    val totalInterceptedCount: StateFlow<Int> = _totalInterceptedCount.asStateFlow()

    private val _lastDetectedUrl = MutableStateFlow("")
    val lastDetectedUrl: StateFlow<String> = _lastDetectedUrl.asStateFlow()

    fun postDetectedMedia(media: DetectedMedia) {
        _lastDetectedUrl.value = media.url
        _totalInterceptedCount.value += 1
        _detectedMediaFlow.tryEmit(media)
        Log.d(TAG, "Emitted detected media: ${media.title} (${media.extension}) - ${media.url}")
    }

    fun setWorkerRunning(running: Boolean) {
        _isWorkerRunning.value = running
    }

    /**
     * Enqueues a background WorkManager task to scan an entire web page URL for media links
     */
    fun schedulePageScan(
        context: Context,
        pageUrl: String,
        pageTitle: String,
        userAgent: String? = null,
        forceImmediate: Boolean = false
    ) {
        if (pageUrl.isBlank() || pageUrl.startsWith("data:") || pageUrl.startsWith("blob:") || pageUrl.startsWith("about:")) {
            return
        }

        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workData = workDataOf(
                MediaDetectionWorker.KEY_PAGE_URL to pageUrl,
                MediaDetectionWorker.KEY_PAGE_TITLE to pageTitle,
                MediaDetectionWorker.KEY_USER_AGENT to (userAgent ?: ""),
                MediaDetectionWorker.KEY_TRIGGER_SOURCE to if (forceImmediate) "MANUAL_SCAN" else "PAGE_NAVIGATION"
            )

            val request = OneTimeWorkRequestBuilder<MediaDetectionWorker>()
                .setInputData(workData)
                .setConstraints(constraints)
                .addTag("media_detection_worker")
                .addTag("page_${pageUrl.hashCode()}")
                .build()

            // Keep unique per URL hash to avoid redundant duplicate scans
            val uniqueWorkName = "scan_${pageUrl.hashCode()}"
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName,
                if (forceImmediate) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Enqueued page scan for $pageUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule WorkManager page scan: ${e.message}")
        }
    }

    /**
     * Enqueues a background WorkManager task to intercept and resolve a direct candidate or download link
     */
    fun scheduleLinkIntercept(
        context: Context,
        linkUrl: String,
        pageTitle: String,
        userAgent: String? = null
    ) {
        if (linkUrl.isBlank() || linkUrl.startsWith("data:") || linkUrl.startsWith("blob:")) {
            return
        }

        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workData = workDataOf(
                MediaDetectionWorker.KEY_CANDIDATE_URL to linkUrl,
                MediaDetectionWorker.KEY_PAGE_TITLE to pageTitle,
                MediaDetectionWorker.KEY_USER_AGENT to (userAgent ?: ""),
                MediaDetectionWorker.KEY_TRIGGER_SOURCE to "DOWNLOAD_INTERCEPT"
            )

            val request = OneTimeWorkRequestBuilder<MediaDetectionWorker>()
                .setInputData(workData)
                .setConstraints(constraints)
                .addTag("media_detection_worker")
                .addTag("intercept_${linkUrl.hashCode()}")
                .build()

            WorkManager.getInstance(context).enqueue(request)
            Log.d(TAG, "Enqueued direct link intercept for $linkUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule WorkManager link intercept: ${e.message}")
        }
    }
}
