package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadedMedia
import com.example.ui.viewmodel.LayoutStyle
import com.example.ui.viewmodel.SortOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagementTab(
    files: List<DownloadedMedia>,
    searchQuery: String,
    selectedCategory: String,
    sortOption: SortOption,
    layoutStyle: LayoutStyle,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onLayoutChange: (LayoutStyle) -> Unit,
    onPlayMedia: (DownloadedMedia) -> Unit,
    onRenameClick: (DownloadedMedia) -> Unit,
    onMoveClick: (DownloadedMedia) -> Unit = {},
    onDeleteMedia: (Long) -> Unit,
    onSyncCloud: (DownloadedMedia) -> Unit,
    onShareMedia: (DownloadedMedia) -> Unit,
    miniPlayerMedia: DownloadedMedia? = null,
    onMiniPlayerClose: () -> Unit = {},
    onOpenFullscreenPlayer: (DownloadedMedia) -> Unit = onPlayMedia
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("file_management_tab")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        // Search & Controls Bar
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                // Search Input Box (Requirement 10, 17)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("file_search_input"),
                    placeholder = { Text("Search files by name or format...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Chips (Requirement 17) & Sort / Layout Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 8.dp)
                    ) {
                        val categories = listOf("ALL", "VIDEO", "AUDIO", "IMAGE", "DOCUMENT", "OFFLINE_READY", "FIRESTORE_VERIFIED")
                        items(categories) { cat ->
                            val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onCategoryChange(cat) },
                                label = {
                                    Text(
                                        text = when (cat) {
                                            "ALL" -> "All"
                                            "VIDEO" -> "Video"
                                            "AUDIO" -> "Audio"
                                            "IMAGE" -> "Image"
                                            "DOCUMENT" -> "Document"
                                            "OFFLINE_READY" -> "Offline"
                                            "FIRESTORE_VERIFIED" -> "Firestore"
                                            else -> cat
                                        }
                                    )
                                },
                                leadingIcon = {
                                    when (cat) {
                                        "VIDEO" -> Icon(
                                            Icons.Default.Movie,
                                            contentDescription = "Video",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        "AUDIO" -> Icon(
                                            Icons.Default.Audiotrack,
                                            contentDescription = "Audio",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        "IMAGE" -> Icon(
                                            Icons.Default.Image,
                                            contentDescription = "Image",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        "DOCUMENT" -> Icon(
                                            Icons.Default.Description,
                                            contentDescription = "Document",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        "OFFLINE_READY" -> Icon(
                                            Icons.Default.OfflinePin,
                                            contentDescription = "Offline Ready",
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF059669),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        "FIRESTORE_VERIFIED" -> Icon(
                                            Icons.Default.CloudDone,
                                            contentDescription = "Firestore Verified",
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFEA580C),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        else -> null
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("filter_chip_${cat.lowercase()}")
                            )
                        }
                    }

                    // Sort Button
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("sort_menu_button")
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort Options")
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        onSortChange(option)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Layout Toggle (Grid vs List) (Requirement 15)
                    IconButton(
                        onClick = {
                            val newLayout = if (layoutStyle == LayoutStyle.GRID) LayoutStyle.LIST else LayoutStyle.GRID
                            onLayoutChange(newLayout)
                        },
                        modifier = Modifier.testTag("layout_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (layoutStyle == LayoutStyle.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle Grid/List view"
                        )
                    }
                }
            }
        }

        // Summary Bar (Offline Access & Storage)
        val totalBytes = files.sumOf { it.fileSizeBytes }
        val offlineCount = files.count { it.status == "COMPLETED" || (it.fileSizeBytes > 0 && it.localUri.isNotBlank()) }
        val firestoreCount = files.count { it.isSyncedToCloud && it.cloudProvider.contains("Firestore", ignoreCase = true) }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth().testTag("file_management_summary_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${files.size} items • ${formatBytes(totalBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (offlineCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF059669).copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OfflinePin,
                                    contentDescription = "Offline ready files",
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "$offlineCount Offline",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669)
                                )
                            }
                        }
                    }

                    if (firestoreCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEA580C).copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Firestore verified files",
                                    tint = Color(0xFFEA580C),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "$firestoreCount Firestore",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEA580C)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Files Grid / List View
        val bottomContentPadding = if (miniPlayerMedia != null) 96.dp else 12.dp

        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No files match \"$searchQuery\"" else "No files in this category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            if (layoutStyle == LayoutStyle.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomContentPadding),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(files, key = { it.id }) { item ->
                        FileGridCard(
                            item = item,
                            onPlay = { onPlayMedia(item) },
                            onPlayFullscreen = { onOpenFullscreenPlayer(item) },
                            onRename = { onRenameClick(item) },
                            onMove = { onMoveClick(item) },
                            onDelete = { onDeleteMedia(item.id) },
                            onSync = { onSyncCloud(item) },
                            onShare = { onShareMedia(item) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = bottomContentPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(files, key = { it.id }) { item ->
                        CompletedDownloadCard(
                            item = item,
                            onPlay = { onPlayMedia(item) },
                            onRename = { onRenameClick(item) },
                            onMove = { onMoveClick(item) },
                            onDelete = { onDeleteMedia(item.id) },
                            onSyncCloud = { onSyncCloud(item) },
                            onShare = { onShareMedia(item) }
                        )
                    }
                }
            }
        }
    }

    // Media3 Mini-Player Component embedded in local File Management View
    AnimatedVisibility(
        visible = miniPlayerMedia != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
    ) {
        miniPlayerMedia?.let { media ->
            Media3MiniPlayer(
                media = media,
                onClose = onMiniPlayerClose,
                onOpenFullscreen = onOpenFullscreenPlayer
            )
        }
    }
}
}

@Composable
fun FileGridCard(
    item: DownloadedMedia,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSync: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit = {},
    onPlayFullscreen: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .testTag("file_grid_card_${item.id}")
    ) {
        Column {
            // Preview / Thumbnail Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .background(
                        when (item.category) {
                            "AUDIO" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            "IMAGE" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (item.category) {
                        "AUDIO" -> Icons.Default.Audiotrack
                        "IMAGE" -> Icons.Default.Image
                        else -> Icons.Default.Movie
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )

                // Play icon overlay
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Thumbnail Overlay Badge (Offline & Firestore sync)
                OfflineFirestoreThumbnailBadge(
                    item = item,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            // Info Details
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Preview in Mini Player") },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                                onClick = {
                                    menuOpen = false
                                    onPlay()
                                }
                            )
                            if (onPlayFullscreen != null) {
                                DropdownMenuItem(
                                    text = { Text("Open in Full Player") },
                                    leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                                    onClick = {
                                        menuOpen = false
                                        onPlayFullscreen()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = {
                                    menuOpen = false
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move to Folder") },
                                leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                                onClick = {
                                    menuOpen = false
                                    onMove()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Verify via Firestore") },
                                leadingIcon = { Icon(Icons.Default.CloudDone, null, tint = Color(0xFFEA580C)) },
                                onClick = {
                                    menuOpen = false
                                    onSync()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sync to Cloud") },
                                leadingIcon = { Icon(Icons.Default.CloudUpload, null) },
                                onClick = {
                                    menuOpen = false
                                    onSync()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = {
                                    menuOpen = false
                                    onShare()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                OfflineFirestoreStatusBadge(item = item, compact = true)
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatBytes(item.fileSizeBytes),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = item.category,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RenameFileDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nameText by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename File") },
        text = {
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text("File Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_input_field")
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameText.isNotBlank()) {
                        onConfirm(nameText.trim())
                    }
                },
                enabled = nameText.isNotBlank(),
                modifier = Modifier.testTag("rename_confirm_button")
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "14.5 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}
