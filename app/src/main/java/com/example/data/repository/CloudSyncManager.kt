package com.example.data.repository

import android.content.Context
import com.example.data.model.DownloadedMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CloudAccount(
    val provider: String, // "Google Drive", "Dropbox"
    val isConnected: Boolean = false,
    val accountEmail: String = "",
    val quotaTotalGb: Float = 15.0f,
    val quotaUsedGb: Float = 3.2f,
    val autoSync: Boolean = false
)

class CloudSyncManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("mediafetch_cloud_prefs", Context.MODE_PRIVATE)

    private val _driveAccount = MutableStateFlow(
        CloudAccount(
            provider = "Google Drive",
            isConnected = prefs.getBoolean("drive_connected", true),
            accountEmail = prefs.getString("drive_email", "vault.user@gmail.com") ?: "vault.user@gmail.com",
            quotaTotalGb = 15.0f,
            quotaUsedGb = 4.8f,
            autoSync = prefs.getBoolean("drive_autosync", true)
        )
    )
    val driveAccount: StateFlow<CloudAccount> = _driveAccount.asStateFlow()

    private val _dropboxAccount = MutableStateFlow(
        CloudAccount(
            provider = "Dropbox",
            isConnected = prefs.getBoolean("dropbox_connected", false),
            accountEmail = prefs.getString("dropbox_email", "") ?: "",
            quotaTotalGb = 2.0f,
            quotaUsedGb = 0.4f,
            autoSync = prefs.getBoolean("dropbox_autosync", false)
        )
    )
    val dropboxAccount: StateFlow<CloudAccount> = _dropboxAccount.asStateFlow()

    fun connectDrive(email: String) {
        prefs.edit()
            .putBoolean("drive_connected", true)
            .putString("drive_email", email)
            .apply()
        _driveAccount.value = _driveAccount.value.copy(
            isConnected = true,
            accountEmail = email
        )
    }

    fun disconnectDrive() {
        prefs.edit().putBoolean("drive_connected", false).apply()
        _driveAccount.value = _driveAccount.value.copy(isConnected = false)
    }

    fun toggleDriveAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean("drive_autosync", enabled).apply()
        _driveAccount.value = _driveAccount.value.copy(autoSync = enabled)
    }

    fun connectDropbox(email: String) {
        prefs.edit()
            .putBoolean("dropbox_connected", true)
            .putString("dropbox_email", email)
            .apply()
        _dropboxAccount.value = _dropboxAccount.value.copy(
            isConnected = true,
            accountEmail = email
        )
    }

    fun disconnectDropbox() {
        prefs.edit().putBoolean("dropbox_connected", false).apply()
        _dropboxAccount.value = _dropboxAccount.value.copy(isConnected = false)
    }

    fun toggleDropboxAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean("dropbox_autosync", enabled).apply()
        _dropboxAccount.value = _dropboxAccount.value.copy(autoSync = enabled)
    }

    fun exportBackupManifest(items: List<DownloadedMedia>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("appName", "MediaFetch")
        root.put("exportedAt", System.currentTimeMillis())
        val jsonArray = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("title", item.title)
            obj.put("sourceUrl", item.sourceUrl)
            obj.put("mimeType", item.mimeType)
            obj.put("fileSizeBytes", item.fileSizeBytes)
            obj.put("category", item.category)
            obj.put("createdAt", item.createdAt)
            jsonArray.put(obj)
        }
        root.put("mediaItems", jsonArray)
        return root.toString(2)
    }

    fun parseBackupManifest(jsonStr: String): List<DownloadedMedia> {
        val list = mutableListOf<DownloadedMedia>()
        try {
            val root = JSONObject(jsonStr)
            val jsonArray = root.getJSONArray("mediaItems")
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val item = DownloadedMedia(
                    title = obj.optString("title", "Untitled Media"),
                    sourceUrl = obj.optString("sourceUrl", ""),
                    mimeType = obj.optString("mimeType", "video/mp4"),
                    fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                    category = obj.optString("category", "VIDEO"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    status = "COMPLETED",
                    downloadProgress = 100,
                    isSyncedToCloud = true,
                    cloudProvider = "Imported Manifest"
                )
                list.add(item)
            }
        } catch (_: Exception) { }
        return list
    }
}
