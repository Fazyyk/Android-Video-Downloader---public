package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadedMedia

// Brand colors for status verification
private val OfflineEmerald = Color(0xFF059669)
private val OfflineEmeraldContainer = Color(0xFFD1FAE5)
private val OfflineEmeraldDarkContainer = Color(0xFF064E3B)

private val FirestoreAmber = Color(0xFFEA580C)
private val FirestoreAmberContainer = Color(0xFFFFEDD5)
private val FirestoreAmberDarkContainer = Color(0xFF7C2D12)

/**
 * Visual badge displaying offline accessibility and Firestore sync verification.
 */
@Composable
fun OfflineFirestoreStatusBadge(
    item: DownloadedMedia,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val isOfflineAvailable = item.status == "COMPLETED" || (item.fileSizeBytes > 0 && item.localUri.isNotBlank())
    val isFirestoreVerified = item.isSyncedToCloud && item.cloudProvider.contains("Firestore", ignoreCase = true)
    val isOtherCloudSynced = item.isSyncedToCloud && !item.cloudProvider.contains("Firestore", ignoreCase = true)

    when {
        // Dual State: Fully available offline AND sync-verified via Firestore
        isOfflineAvailable && isFirestoreVerified -> {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = OfflineEmerald.copy(alpha = 0.4f)
                ),
                modifier = modifier.testTag("status_badge_offline_firestore_verified_${item.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Offline component
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OfflinePin,
                            contentDescription = "Offline Access Available",
                            tint = OfflineEmerald,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (compact) "Offline" else "Offline Ready",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OfflineEmerald
                        )
                    }

                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )

                    // Firestore verified component
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Sync-verified via Firestore",
                            tint = FirestoreAmber,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (compact) "Firestore" else "Firestore Verified",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = FirestoreAmber
                        )
                    }
                }
            }
        }

        // Available offline but pending/not Firestore verified
        isOfflineAvailable -> {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OfflineEmerald.copy(alpha = 0.12f),
                modifier = modifier.testTag("status_badge_offline_ready_${item.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OfflinePin,
                        contentDescription = "Offline Available",
                        tint = OfflineEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (compact) "Offline" else "Offline Ready",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OfflineEmerald
                    )
                }
            }
        }

        // Firestore verified only
        isFirestoreVerified -> {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = FirestoreAmber.copy(alpha = 0.12f),
                modifier = modifier.testTag("status_badge_firestore_verified_${item.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Firestore Verified",
                        tint = FirestoreAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Firestore Verified",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = FirestoreAmber
                    )
                }
            }
        }

        // Other cloud provider sync
        isOtherCloudSynced -> {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = modifier.testTag("status_badge_cloud_synced_${item.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Synced to ${item.cloudProvider}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = item.cloudProvider,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Thumbnail corner overlay badge indicating offline status and Firestore sync.
 */
@Composable
fun OfflineFirestoreThumbnailBadge(
    item: DownloadedMedia,
    modifier: Modifier = Modifier
) {
    val isOfflineAvailable = item.status == "COMPLETED" || (item.fileSizeBytes > 0 && item.localUri.isNotBlank())
    val isFirestoreVerified = item.isSyncedToCloud && item.cloudProvider.contains("Firestore", ignoreCase = true)

    if (isOfflineAvailable || isFirestoreVerified) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 2.dp,
            modifier = modifier
                .padding(6.dp)
                .testTag("thumbnail_badge_sync_status_${item.id}")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (isOfflineAvailable) {
                    Icon(
                        imageVector = Icons.Default.OfflinePin,
                        contentDescription = "Offline Access",
                        tint = OfflineEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                }
                if (isOfflineAvailable && isFirestoreVerified) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                }
                if (isFirestoreVerified) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Firestore Verified",
                        tint = FirestoreAmber,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
