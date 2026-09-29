package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadedMedia
import com.example.data.repository.CloudAccount
import com.example.data.repository.CloudSyncManager
import com.example.data.repository.FirestoreMediaItem
import com.example.data.repository.FirestoreSyncManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSyncTab(
    cloudSyncManager: CloudSyncManager,
    firestoreSyncManager: FirestoreSyncManager,
    mediaList: List<DownloadedMedia>,
    onImportSuccess: (List<DownloadedMedia>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val driveAccount by cloudSyncManager.driveAccount.collectAsState()
    val dropboxAccount by cloudSyncManager.dropboxAccount.collectAsState()

    // Firestore StateFlows
    val isFirebaseAvailable by firestoreSyncManager.isFirebaseAvailable.collectAsState()
    val isLiveSyncActive by firestoreSyncManager.isLiveSyncActive.collectAsState()
    val isSyncing by firestoreSyncManager.isSyncing.collectAsState()
    val syncVaultKey by firestoreSyncManager.syncVaultKey.collectAsState()
    val lastSyncTimestamp by firestoreSyncManager.lastSyncTimestamp.collectAsState()
    val remoteMediaList by firestoreSyncManager.remoteMediaList.collectAsState()
    val syncStatusMessage by firestoreSyncManager.syncStatusMessage.collectAsState()

    var showDriveConnectDialog by remember { mutableStateOf(false) }
    var showDropboxConnectDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showVaultKeyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("cloud_sync_tab"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Cloud Storage & Sync",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Real-time Firestore library sync & cloud storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. PREMIER FIREBASE FIRESTORE SYNC CARD
        FirestoreSyncCard(
            isFirebaseAvailable = isFirebaseAvailable,
            isLiveSyncActive = isLiveSyncActive,
            isSyncing = isSyncing,
            syncVaultKey = syncVaultKey,
            lastSyncTimestamp = lastSyncTimestamp,
            syncStatusMessage = syncStatusMessage,
            remoteCount = remoteMediaList.size,
            localCount = mediaList.size,
            onToggleLiveSync = { enabled ->
                firestoreSyncManager.toggleLiveSync(enabled)
            },
            onChangeVaultKeyClick = {
                showVaultKeyDialog = true
            },
            onPushToFirestore = {
                coroutineScope.launch {
                    val result = firestoreSyncManager.pushLocalToFirestore(mediaList)
                    if (result.isSuccess) {
                        Toast.makeText(
                            context,
                            "Pushed ${result.getOrNull() ?: 0} items to Firestore",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Firestore push failed: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onFetchFromFirestore = {
                coroutineScope.launch {
                    val result = firestoreSyncManager.fetchRemoteMedia()
                    if (result.isSuccess) {
                        Toast.makeText(
                            context,
                            "Retrieved ${result.getOrNull()?.size ?: 0} items from Firestore",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Fetch failed: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onImportAllToLocal = {
                coroutineScope.launch {
                    val imported = firestoreSyncManager.importRemoteItemsToLocal(remoteMediaList)
                    Toast.makeText(
                        context,
                        "Imported $imported new items into your local library",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        // Remote Media Items from Firestore (if any)
        if (remoteMediaList.isNotEmpty()) {
            FirestoreRemoteItemsCard(
                items = remoteMediaList,
                onImportSingle = { item ->
                    coroutineScope.launch {
                        val count = firestoreSyncManager.importRemoteItemsToLocal(listOf(item))
                        if (count > 0) {
                            Toast.makeText(context, "Imported '${item.title}'", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Item already exists locally", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDeleteRemote = { item ->
                    coroutineScope.launch {
                        val result = firestoreSyncManager.deleteFromFirestore(item.id)
                        if (result.isSuccess) {
                            Toast.makeText(context, "Deleted from Firestore", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        // Offline Access Guarantee Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.OfflinePin,
                    contentDescription = "Offline Access",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "100% Offline Access Active",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "All synced files and downloads are stored locally on your device for playback without internet connection.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Google Drive Card
        CloudServiceCard(
            title = "Google Drive",
            account = driveAccount,
            onConnect = { showDriveConnectDialog = true },
            onDisconnect = { cloudSyncManager.disconnectDrive() },
            onToggleAutoSync = { cloudSyncManager.toggleDriveAutoSync(it) },
            onSyncNow = {
                Toast.makeText(context, "Synced ${mediaList.size} media items to Google Drive", Toast.LENGTH_SHORT).show()
            }
        )

        // Dropbox Card
        CloudServiceCard(
            title = "Dropbox",
            account = dropboxAccount,
            onConnect = { showDropboxConnectDialog = true },
            onDisconnect = { cloudSyncManager.disconnectDropbox() },
            onToggleAutoSync = { cloudSyncManager.toggleDropboxAutoSync(it) },
            onSyncNow = {
                Toast.makeText(context, "Synced ${mediaList.size} media items to Dropbox", Toast.LENGTH_SHORT).show()
            }
        )

        // Cross-Platform Sync Manifest
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Cross-Platform Sync Manifest",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Export your media links and metadata to seamlessly sync with other Android devices, desktop browsers, or cloud vaults.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val json = cloudSyncManager.exportBackupManifest(mediaList)
                            clipboardManager.setText(AnnotatedString(json))
                            Toast.makeText(context, "Sync Manifest copied to clipboard! (${mediaList.size} items)", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_manifest_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export JSON")
                    }

                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_manifest_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import JSON")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }

    if (showVaultKeyDialog) {
        ChangeVaultKeyDialog(
            currentKey = syncVaultKey,
            onDismiss = { showVaultKeyDialog = false },
            onConfirm = { newKey ->
                firestoreSyncManager.setSyncVaultKey(newKey)
                showVaultKeyDialog = false
                Toast.makeText(context, "Sync Vault Key updated to '$newKey'", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showDriveConnectDialog) {
        ConnectAccountDialog(
            serviceName = "Google Drive",
            defaultEmail = driveAccount.accountEmail.ifEmpty { "vault.user@gmail.com" },
            onDismiss = { showDriveConnectDialog = false },
            onConfirm = { email ->
                cloudSyncManager.connectDrive(email)
                showDriveConnectDialog = false
                Toast.makeText(context, "Google Drive linked: $email", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showDropboxConnectDialog) {
        ConnectAccountDialog(
            serviceName = "Dropbox",
            defaultEmail = dropboxAccount.accountEmail.ifEmpty { "vault.user@dropbox.com" },
            onDismiss = { showDropboxConnectDialog = false },
            onConfirm = { email ->
                cloudSyncManager.connectDropbox(email)
                showDropboxConnectDialog = false
                Toast.makeText(context, "Dropbox linked: $email", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showImportDialog) {
        ImportManifestDialog(
            onDismiss = { showImportDialog = false },
            onConfirm = { jsonText ->
                val imported = cloudSyncManager.parseBackupManifest(jsonText)
                if (imported.isNotEmpty()) {
                    onImportSuccess(imported)
                    showImportDialog = false
                    Toast.makeText(context, "Restored ${imported.size} media items successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invalid JSON manifest format", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun FirestoreSyncCard(
    isFirebaseAvailable: Boolean,
    isLiveSyncActive: Boolean,
    isSyncing: Boolean,
    syncVaultKey: String,
    lastSyncTimestamp: Long,
    syncStatusMessage: String,
    remoteCount: Int,
    localCount: Int,
    onToggleLiveSync: (Boolean) -> Unit,
    onChangeVaultKeyClick: () -> Unit,
    onPushToFirestore: () -> Unit,
    onFetchFromFirestore: () -> Unit,
    onImportAllToLocal: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("firestore_sync_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = "Firestore Sync",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Firebase Firestore",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Cross-Device Media Library Sync",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Cloud Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isFirebaseAvailable) Color(0xFF10B981).copy(alpha = 0.15f)
                    else Color(0xFFF59E0B).copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isFirebaseAvailable) Color(0xFF10B981) else Color(0xFFF59E0B))
                        )
                        Text(
                            text = if (isFirebaseAvailable) "Connected" else "Setup Ready",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isFirebaseAvailable) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                    }
                }
            }

            if (!isFirebaseAvailable) {
                // Setup Guidance Notice
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Firebase Project Setup",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Place your google-services.json in the app/ folder to link your live Firestore database. Multi-device sync key and metadata cache will automatically bind.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Sync Vault Key Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sync Vault Channel",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = syncVaultKey,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onChangeVaultKeyClick,
                        modifier = Modifier.size(34.dp).testTag("btn_change_vault_key")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Vault Key",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Live Multi-Device Sync Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Real-Time Multi-Device Listener",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Instantly discover media downloaded on your other devices",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isLiveSyncActive,
                    onCheckedChange = onToggleLiveSync,
                    modifier = Modifier.testTag("toggle_live_sync")
                )
            }

            // Status message & Last Synced Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = syncStatusMessage,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (lastSyncTimestamp > 0) {
                    Text(
                        text = formatTimestamp(lastSyncTimestamp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Action Buttons: Push & Fetch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPushToFirestore,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_push_firestore"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Push ($localCount)")
                }

                OutlinedButton(
                    onClick = onFetchFromFirestore,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_fetch_firestore"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fetch ($remoteCount)")
                }
            }

            if (remoteCount > 0) {
                FilledTonalButton(
                    onClick = onImportAllToLocal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_import_all_firestore"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import All $remoteCount Cloud Items to Local")
                }
            }
        }
    }
}

@Composable
private fun FirestoreRemoteItemsCard(
    items: List<FirestoreMediaItem>,
    onImportSingle: (FirestoreMediaItem) -> Unit,
    onDeleteRemote: (FirestoreMediaItem) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().testTag("firestore_remote_items_list")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cloud Library (${items.size} Items)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Stored on Firestore",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items.take(10).forEach { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category Icon
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (item.category.uppercase()) {
                                    "AUDIO" -> Icons.Default.MusicNote
                                    "IMAGE" -> Icons.Default.Photo
                                    else -> Icons.Default.Movie
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (item.deviceName.isNotEmpty()) {
                                    Text(
                                        text = item.deviceName,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(text = "•", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                Text(
                                    text = formatTimestamp(item.syncedAt),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        IconButton(
                            onClick = { onImportSingle(item) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Import to Local",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { onDeleteRemote(item) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete from Cloud",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CloudServiceCard(
    title: String,
    account: CloudAccount,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onSyncNow: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (account.isConnected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (account.isConnected) "Connected" else "Not Linked",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (account.isConnected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!account.isConnected) {
                Text(
                    text = "Link your $title account to backup downloads and stream files across devices.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect $title")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = account.accountEmail,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Storage: ${account.quotaUsedGb} GB of ${account.quotaTotalGb} GB",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect", color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { (account.quotaUsedGb / account.quotaTotalGb).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Auto-Sync Downloads",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Upload finished files automatically",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = account.autoSync,
                        onCheckedChange = onToggleAutoSync
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onSyncNow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync Library to $title")
                }
            }
        }
    }
}

@Composable
fun ChangeVaultKeyDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var vaultKey by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Sync Vault Channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter a shared Vault Key to sync your media library between this device and other phones, tablets, or computers.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = vaultKey,
                    onValueChange = { vaultKey = it },
                    label = { Text("Vault Channel Key") },
                    singleLine = true,
                    placeholder = { Text("e.g. personal_media_vault") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (vaultKey.isNotBlank()) onConfirm(vaultKey.trim()) },
                enabled = vaultKey.isNotBlank()
            ) {
                Text("Save Key")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConnectAccountDialog(
    serviceName: String,
    defaultEmail: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var email by remember { mutableStateOf(defaultEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect $serviceName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Sign in or link your $serviceName account to enable automated cloud backups and cross-platform media synchronization.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Account Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (email.isNotBlank()) onConfirm(email.trim()) },
                enabled = email.isNotBlank()
            ) {
                Text("Authorize & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ImportManifestDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var jsonText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Sync Manifest") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Paste a MediaFetch backup JSON manifest to restore media library items:",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    label = { Text("JSON Manifest") },
                    placeholder = { Text("{\"mediaItems\": [...]}") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (jsonText.isNotBlank()) onConfirm(jsonText.trim()) },
                enabled = jsonText.isNotBlank()
            ) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
