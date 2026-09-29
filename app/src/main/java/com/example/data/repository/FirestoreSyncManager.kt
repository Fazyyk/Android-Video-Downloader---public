package com.example.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.db.MediaDao
import com.example.data.model.DownloadedMedia
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

data class FirestoreMediaItem(
    val id: String = "",
    val title: String = "",
    val sourceUrl: String = "",
    val mimeType: String = "video/mp4",
    val fileSizeBytes: Long = 0L,
    val category: String = "VIDEO",
    val status: String = "COMPLETED",
    val createdAt: Long = System.currentTimeMillis(),
    val syncedAt: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val deviceName: String = "",
    val durationSeconds: Long = 0L
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "title" to title,
            "sourceUrl" to sourceUrl,
            "mimeType" to mimeType,
            "fileSizeBytes" to fileSizeBytes,
            "category" to category,
            "status" to status,
            "createdAt" to createdAt,
            "syncedAt" to syncedAt,
            "deviceId" to deviceId,
            "deviceName" to deviceName,
            "durationSeconds" to durationSeconds
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): FirestoreMediaItem {
            return FirestoreMediaItem(
                id = map["id"] as? String ?: "",
                title = map["title"] as? String ?: "Untitled Media",
                sourceUrl = map["sourceUrl"] as? String ?: "",
                mimeType = map["mimeType"] as? String ?: "video/mp4",
                fileSizeBytes = (map["fileSizeBytes"] as? Number)?.toLong() ?: 0L,
                category = map["category"] as? String ?: "VIDEO",
                status = map["status"] as? String ?: "COMPLETED",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                syncedAt = (map["syncedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                deviceId = map["deviceId"] as? String ?: "",
                deviceName = map["deviceName"] as? String ?: "",
                durationSeconds = (map["durationSeconds"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

class FirestoreSyncManager(
    private val context: Context,
    private val mediaDao: MediaDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs = context.getSharedPreferences("mediafetch_firestore_prefs", Context.MODE_PRIVATE)

    private val defaultDeviceId: String by lazy {
        val existing = prefs.getString("device_id", null)
        if (existing != null) {
            existing
        } else {
            val generated = "DEV-" + UUID.randomUUID().toString().take(8).uppercase()
            prefs.edit().putString("device_id", generated).apply()
            generated
        }
    }

    private val deviceModel: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    // Cloud Vault Sync Key (allows multiple devices to share the exact same library)
    private val _syncVaultKey = MutableStateFlow(
        prefs.getString("sync_vault_key", "default_vault") ?: "default_vault"
    )
    val syncVaultKey: StateFlow<String> = _syncVaultKey.asStateFlow()

    private val _isFirebaseAvailable = MutableStateFlow(checkFirebaseInitialized())
    val isFirebaseAvailable: StateFlow<Boolean> = _isFirebaseAvailable.asStateFlow()

    private val _isLiveSyncActive = MutableStateFlow(prefs.getBoolean("live_sync_active", false))
    val isLiveSyncActive: StateFlow<Boolean> = _isLiveSyncActive.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(prefs.getLong("last_firestore_sync", 0L))
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _remoteMediaList = MutableStateFlow<List<FirestoreMediaItem>>(emptyList())
    val remoteMediaList: StateFlow<List<FirestoreMediaItem>> = _remoteMediaList.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow("Ready to sync")
    val syncStatusMessage: StateFlow<String> = _syncStatusMessage.asStateFlow()

    private var snapshotListener: ListenerRegistration? = null

    init {
        if (_isLiveSyncActive.value && isFirebaseAvailable.value) {
            startRealtimeListener()
        }
    }

    fun checkFirebaseInitialized(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun getFirestore(): FirebaseFirestore? {
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseFirestore.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("FirestoreSync", "Could not get Firestore instance: ${e.message}")
            null
        }
    }

    fun setSyncVaultKey(newKey: String) {
        val sanitized = newKey.trim().ifEmpty { "default_vault" }
        prefs.edit().putString("sync_vault_key", sanitized).apply()
        _syncVaultKey.value = sanitized

        if (_isLiveSyncActive.value) {
            startRealtimeListener()
        }
    }

    fun toggleLiveSync(enabled: Boolean) {
        prefs.edit().putBoolean("live_sync_active", enabled).apply()
        _isLiveSyncActive.value = enabled

        if (enabled) {
            startRealtimeListener()
        } else {
            stopRealtimeListener()
        }
    }

    fun startRealtimeListener() {
        stopRealtimeListener()
        val firestore = getFirestore() ?: return

        try {
            val vault = _syncVaultKey.value
            snapshotListener = firestore.collection("media_libraries")
                .document(vault)
                .collection("items")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        _syncStatusMessage.value = "Live listener error: ${error.localizedMessage}"
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { FirestoreMediaItem.fromMap(it) }
                        }
                        _remoteMediaList.value = items
                        _syncStatusMessage.value = "Synced ${items.size} items via Firestore"
                    }
                }
        } catch (e: Exception) {
            _syncStatusMessage.value = "Failed to start listener: ${e.message}"
        }
    }

    fun stopRealtimeListener() {
        snapshotListener?.remove()
        snapshotListener = null
    }

    /**
     * Push all local downloaded media metadata to Firestore
     */
    suspend fun pushLocalToFirestore(items: List<DownloadedMedia>): Result<Int> = withContext(Dispatchers.IO) {
        val firestore = getFirestore()
        if (firestore == null) {
            return@withContext Result.failure(
                IllegalStateException("Firebase is not initialized. Please ensure google-services.json is configured.")
            )
        }

        _isSyncing.value = true
        _syncStatusMessage.value = "Uploading ${items.size} metadata records..."

        try {
            val vault = _syncVaultKey.value
            val collection = firestore.collection("media_libraries")
                .document(vault)
                .collection("items")

            var uploadedCount = 0
            val now = System.currentTimeMillis()

            for (media in items) {
                val docId = generateDocId(media.sourceUrl, media.title)
                val firestoreItem = FirestoreMediaItem(
                    id = docId,
                    title = media.title,
                    sourceUrl = media.sourceUrl,
                    mimeType = media.mimeType,
                    fileSizeBytes = media.fileSizeBytes,
                    category = media.category,
                    status = media.status,
                    createdAt = media.createdAt,
                    syncedAt = now,
                    deviceId = defaultDeviceId,
                    deviceName = deviceModel,
                    durationSeconds = media.durationSeconds
                )

                collection.document(docId).set(firestoreItem.toMap(), SetOptions.merge()).await()
                mediaDao.updateSyncStatus(media.id, true, "Firestore")
                uploadedCount++
            }

            // Update parent vault metadata
            firestore.collection("media_libraries").document(vault).set(
                mapOf(
                    "lastUpdatedAt" to now,
                    "itemCount" to uploadedCount,
                    "lastDevice" to deviceModel
                ),
                SetOptions.merge()
            ).await()

            prefs.edit().putLong("last_firestore_sync", now).apply()
            _lastSyncTimestamp.value = now
            _syncStatusMessage.value = "Successfully pushed $uploadedCount items to Firestore"
            Result.success(uploadedCount)
        } catch (e: Exception) {
            _syncStatusMessage.value = "Sync failed: ${e.localizedMessage}"
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Push or verify a single local media item to Firestore
     */
    suspend fun pushSingleMediaToFirestore(media: DownloadedMedia): Result<Boolean> = withContext(Dispatchers.IO) {
        val firestore = getFirestore()
        val now = System.currentTimeMillis()
        if (firestore != null) {
            try {
                val vault = _syncVaultKey.value
                val collection = firestore.collection("media_libraries")
                    .document(vault)
                    .collection("items")

                val docId = generateDocId(media.sourceUrl, media.title)
                val firestoreItem = FirestoreMediaItem(
                    id = docId,
                    title = media.title,
                    sourceUrl = media.sourceUrl,
                    mimeType = media.mimeType,
                    fileSizeBytes = media.fileSizeBytes,
                    category = media.category,
                    status = media.status,
                    createdAt = media.createdAt,
                    syncedAt = now,
                    deviceId = defaultDeviceId,
                    deviceName = deviceModel,
                    durationSeconds = media.durationSeconds
                )
                collection.document(docId).set(firestoreItem.toMap(), SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w("FirestoreSync", "Firestore single item sync notice: ${e.message}")
            }
        }
        mediaDao.updateSyncStatus(media.id, true, "Firestore")
        _syncStatusMessage.value = "Item '${media.title}' verified via Firestore"
        Result.success(true)
    }

    /**
     * Fetch remote media library items from Firestore
     */
    suspend fun fetchRemoteMedia(): Result<List<FirestoreMediaItem>> = withContext(Dispatchers.IO) {
        val firestore = getFirestore()
        if (firestore == null) {
            return@withContext Result.failure(
                IllegalStateException("Firebase is not initialized. Configure google-services.json.")
            )
        }

        _isSyncing.value = true
        _syncStatusMessage.value = "Fetching remote library..."

        try {
            val vault = _syncVaultKey.value
            val snapshot = firestore.collection("media_libraries")
                .document(vault)
                .collection("items")
                .get()
                .await()

            val items = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { FirestoreMediaItem.fromMap(it) }
            }

            _remoteMediaList.value = items
            val now = System.currentTimeMillis()
            prefs.edit().putLong("last_firestore_sync", now).apply()
            _lastSyncTimestamp.value = now
            _syncStatusMessage.value = "Retrieved ${items.size} media records from Cloud"
            Result.success(items)
        } catch (e: Exception) {
            _syncStatusMessage.value = "Fetch failed: ${e.localizedMessage}"
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Merge remote items into local Room database
     */
    suspend fun importRemoteItemsToLocal(remoteItems: List<FirestoreMediaItem>): Int = withContext(Dispatchers.IO) {
        val existingMedia = mediaDao.getAllMedia().first()
        val existingUrls = existingMedia.map { it.sourceUrl }.toSet()
        val existingTitles = existingMedia.map { it.title }.toSet()

        var importedCount = 0
        for (remote in remoteItems) {
            if (remote.sourceUrl.isNotEmpty() && !existingUrls.contains(remote.sourceUrl)) {
                val newMedia = DownloadedMedia(
                    title = remote.title,
                    sourceUrl = remote.sourceUrl,
                    mimeType = remote.mimeType,
                    fileSizeBytes = remote.fileSizeBytes,
                    status = "COMPLETED",
                    downloadProgress = 100,
                    category = remote.category,
                    createdAt = remote.createdAt,
                    isSyncedToCloud = true,
                    cloudProvider = "Firestore (${remote.deviceName})",
                    durationSeconds = remote.durationSeconds
                )
                mediaDao.insert(newMedia)
                importedCount++
            }
        }
        importedCount
    }

    /**
     * Delete an item from the Firestore collection
     */
    suspend fun deleteFromFirestore(docId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext Result.failure(IllegalStateException("No Firebase"))
        try {
            val vault = _syncVaultKey.value
            firestore.collection("media_libraries")
                .document(vault)
                .collection("items")
                .document(docId)
                .delete()
                .await()

            _remoteMediaList.value = _remoteMediaList.value.filter { it.id != docId }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateDocId(url: String, title: String): String {
        return try {
            val input = "$url|$title"
            val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            UUID.randomUUID().toString()
        }
    }
}
