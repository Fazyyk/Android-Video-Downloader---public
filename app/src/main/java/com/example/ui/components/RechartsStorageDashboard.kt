package com.example.ui.components

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadedMedia
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

enum class RechartsMode {
    DONUT,
    BAR,
    STACKED
}

data class RechartsMediaSegment(
    val categoryKey: String,
    val label: String,
    val sizeBytes: Long,
    val count: Int,
    val color: Color,
    val icon: ImageVector
)

// Recharts signature clean modern palette
val RechartsBlue = Color(0xFF3B82F6)    // Video
val RechartsPurple = Color(0xFF8B5CF6)  // Audio
val RechartsEmerald = Color(0xFF10B981) // Image
val RechartsAmber = Color(0xFFF59E0B)   // Document
val RechartsPink = Color(0xFFEC4899)    // Other
val RechartsSlate = Color(0xFF64748B)   // Free space

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RechartsStorageDashboard(
    mediaList: List<DownloadedMedia>,
    modifier: Modifier = Modifier,
    onCategorySelected: (String) -> Unit = {}
) {
    var chartMode by remember { mutableStateOf(RechartsMode.DONUT) }
    var selectedSegmentKey by remember { mutableStateOf<String?>(null) }

    // Read real system storage statistics
    val statFs = remember {
        try {
            StatFs(Environment.getDataDirectory().path)
        } catch (_: Exception) {
            null
        }
    }
    val totalDeviceStorage = statFs?.totalBytes ?: (64L * 1024 * 1024 * 1024)
    val freeDeviceStorage = statFs?.availableBytes ?: (28L * 1024 * 1024 * 1024)

    // Partition media list by categories
    val videoBytes = mediaList.filter { it.category.equals("VIDEO", ignoreCase = true) }.sumOf { it.fileSizeBytes }
    val videoCount = mediaList.count { it.category.equals("VIDEO", ignoreCase = true) }

    val audioBytes = mediaList.filter { it.category.equals("AUDIO", ignoreCase = true) }.sumOf { it.fileSizeBytes }
    val audioCount = mediaList.count { it.category.equals("AUDIO", ignoreCase = true) }

    val imageBytes = mediaList.filter { it.category.equals("IMAGE", ignoreCase = true) }.sumOf { it.fileSizeBytes }
    val imageCount = mediaList.count { it.category.equals("IMAGE", ignoreCase = true) }

    val docBytes = mediaList.filter {
        it.category.equals("DOCUMENT", ignoreCase = true) || it.category.equals("DOCUMENTS", ignoreCase = true)
    }.sumOf { it.fileSizeBytes }
    val docCount = mediaList.count {
        it.category.equals("DOCUMENT", ignoreCase = true) || it.category.equals("DOCUMENTS", ignoreCase = true)
    }

    val otherBytes = mediaList.filter {
        !it.category.equals("VIDEO", ignoreCase = true) &&
                !it.category.equals("AUDIO", ignoreCase = true) &&
                !it.category.equals("IMAGE", ignoreCase = true) &&
                !it.category.equals("DOCUMENT", ignoreCase = true) &&
                !it.category.equals("DOCUMENTS", ignoreCase = true)
    }.sumOf { it.fileSizeBytes }
    val otherCount = mediaList.count {
        !it.category.equals("VIDEO", ignoreCase = true) &&
                !it.category.equals("AUDIO", ignoreCase = true) &&
                !it.category.equals("IMAGE", ignoreCase = true) &&
                !it.category.equals("DOCUMENT", ignoreCase = true) &&
                !it.category.equals("DOCUMENTS", ignoreCase = true)
    }

    val totalMediaBytes = videoBytes + audioBytes + imageBytes + docBytes + otherBytes

    val segments = remember(mediaList) {
        listOf(
            RechartsMediaSegment("VIDEO", "Video", videoBytes, videoCount, RechartsBlue, Icons.Default.Movie),
            RechartsMediaSegment("AUDIO", "Audio", audioBytes, audioCount, RechartsPurple, Icons.Default.Audiotrack),
            RechartsMediaSegment("IMAGE", "Image", imageBytes, imageCount, RechartsEmerald, Icons.Default.Image),
            RechartsMediaSegment("DOCUMENT", "Document", docBytes, docCount, RechartsAmber, Icons.Default.Description),
            RechartsMediaSegment("OTHER", "Other", otherBytes, otherCount, RechartsPink, Icons.Default.Folder)
        )
    }

    val activeSegment = segments.find { it.categoryKey == selectedSegmentKey }

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("recharts_disk_storage_dashboard")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Dashboard Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(RechartsBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Disk Storage",
                        tint = RechartsBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Storage Analytics",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = RechartsBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Recharts Engine",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = RechartsBlue,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = "Real-time disk space distribution by media type",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Recharts Mode Switcher Segmented Buttons
            val modes = listOf(
                RechartsMode.DONUT to "Donut",
                RechartsMode.BAR to "Bar",
                RechartsMode.STACKED to "Distribution"
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recharts_chart_mode_switcher")
            ) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = chartMode == mode,
                        onClick = {
                            chartMode = mode
                            selectedSegmentKey = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        RechartsMode.DONUT -> Icons.Default.PieChart
                                        RechartsMode.BAR -> Icons.Default.BarChart
                                        RechartsMode.STACKED -> Icons.Default.Storage
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart Canvas Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                when (chartMode) {
                    RechartsMode.DONUT -> {
                        RechartsDonutChart(
                            segments = segments,
                            totalMediaBytes = totalMediaBytes,
                            selectedSegmentKey = selectedSegmentKey,
                            onSegmentTapped = { key ->
                                selectedSegmentKey = if (selectedSegmentKey == key) null else key
                            }
                        )
                    }
                    RechartsMode.BAR -> {
                        RechartsBarChart(
                            segments = segments,
                            selectedSegmentKey = selectedSegmentKey,
                            onSegmentTapped = { key ->
                                selectedSegmentKey = if (selectedSegmentKey == key) null else key
                            }
                        )
                    }
                    RechartsMode.STACKED -> {
                        RechartsStackedBarChart(
                            segments = segments,
                            totalMediaBytes = totalMediaBytes,
                            freeDeviceStorage = freeDeviceStorage,
                            totalDeviceStorage = totalDeviceStorage,
                            selectedSegmentKey = selectedSegmentKey,
                            onSegmentTapped = { key ->
                                selectedSegmentKey = if (selectedSegmentKey == key) null else key
                            }
                        )
                    }
                }
            }

            // Interactive Recharts Tooltip Box (like Recharts <Tooltip />)
            AnimatedVisibility(
                visible = activeSegment != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                activeSegment?.let { segment ->
                    val percentage = if (totalMediaBytes > 0) {
                        (segment.sizeBytes.toFloat() / totalMediaBytes.toFloat() * 100f)
                    } else 0f

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, segment.color.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("recharts_tooltip_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(segment.color)
                                )
                                Column {
                                    Text(
                                        text = "${segment.label} Files",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${segment.count} stored files",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatBytes(segment.sizeBytes),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = segment.color
                                )
                                Text(
                                    text = "%.1f%% of media space".format(percentage),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Recharts Interactive Legend (Recharts <Legend />)
            Text(
                text = "Media Types Legend (Tap slice or legend to inspect):",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recharts_legend_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                segments.forEach { segment ->
                    val isSelected = selectedSegmentKey == segment.categoryKey
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) segment.color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, segment.color) else null,
                        modifier = Modifier
                            .clickable {
                                selectedSegmentKey = if (isSelected) null else segment.categoryKey
                                onCategorySelected(segment.categoryKey)
                            }
                            .testTag("recharts_legend_item_${segment.categoryKey.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(segment.color)
                            )
                            Text(
                                text = segment.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatBytes(segment.sizeBytes),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = segment.color
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Total Device Disk Telemetry Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recharts_device_telemetry_banner")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Media Stored",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${formatBytes(totalMediaBytes)} (${mediaList.size} files)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress bar comparing Media to Free Device storage
                    val mediaRatio = (totalMediaBytes.toFloat() / totalDeviceStorage.toFloat()).coerceIn(0.01f, 1f)
                    LinearProgressIndicator(
                        progress = { mediaRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = RechartsBlue,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Free Device Space: ${formatBytes(freeDeviceStorage)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Total Disk: ${formatBytes(totalDeviceStorage)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recharts-style Donut Chart with center value & interactive segment arc selection
 */
@Composable
private fun RechartsDonutChart(
    segments: List<RechartsMediaSegment>,
    totalMediaBytes: Long,
    selectedSegmentKey: String?,
    onSegmentTapped: (String) -> Unit
) {
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    val activeSegments = segments.filter { it.sizeBytes > 0 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("recharts_donut_canvas_box"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeSegments, totalMediaBytes) {
                    detectTapGestures { tapOffset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val dx = tapOffset.x - centerX
                        val dy = tapOffset.y - centerY
                        val distance = sqrt(dx * dx + dy * dy)
                        val maxRadius = min(size.width, size.height) / 2f * 0.85f
                        val minRadius = maxRadius * 0.55f

                        if (distance in minRadius..maxRadius && totalMediaBytes > 0) {
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            if (angle < 0) angle += 360f

                            var currentStartAngle = 0f
                            for (seg in activeSegments) {
                                val sweep = (seg.sizeBytes.toFloat() / totalMediaBytes.toFloat()) * 360f
                                if (angle >= currentStartAngle && angle < currentStartAngle + sweep) {
                                    onSegmentTapped(seg.categoryKey)
                                    return@detectTapGestures
                                }
                                currentStartAngle += sweep
                            }
                        }
                    }
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height
            val centerX = canvasW / 2f
            val centerY = canvasH / 2f
            val baseRadius = min(canvasW, canvasH) / 2f * 0.82f
            val defaultStroke = baseRadius * 0.30f

            if (totalMediaBytes <= 0L || activeSegments.isEmpty()) {
                // Empty state donut ring
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.25f),
                    radius = baseRadius - defaultStroke / 2f,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = defaultStroke)
                )
                return@Canvas
            }

            var startAngle = -90f
            for (segment in activeSegments) {
                val fullSweep = (segment.sizeBytes.toFloat() / totalMediaBytes.toFloat()) * 360f
                val animatedSweep = fullSweep * animProgress.value
                val isSelected = segment.categoryKey == selectedSegmentKey
                val strokeWidth = if (isSelected) defaultStroke * 1.25f else defaultStroke
                val ringRadius = if (isSelected) baseRadius + 3f else baseRadius

                val arcTopLeft = Offset(centerX - ringRadius + strokeWidth / 2f, centerY - ringRadius + strokeWidth / 2f)
                val arcSize = Size((ringRadius - strokeWidth / 2f) * 2f, (ringRadius - strokeWidth / 2f) * 2f)

                drawArc(
                    color = segment.color,
                    startAngle = startAngle + 1.5f,
                    sweepAngle = (animatedSweep - 3f).coerceAtLeast(1f),
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                startAngle += fullSweep
            }
        }

        // Center Metric Overlay (Recharts center text in donut)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val selected = segments.find { it.categoryKey == selectedSegmentKey }
            if (selected != null) {
                Text(
                    text = selected.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = selected.color
                )
                Text(
                    text = formatBytes(selected.sizeBytes),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${selected.count} files",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Total Media",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatBytes(totalMediaBytes),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${segments.sumOf { it.count }} files",
                    fontSize = 10.sp,
                    color = RechartsBlue
                )
            }
        }
    }
}

/**
 * Recharts-style Bar Chart with Cartesian grid lines & rounded bars
 */
@Composable
private fun RechartsBarChart(
    segments: List<RechartsMediaSegment>,
    selectedSegmentKey: String?,
    onSegmentTapped: (String) -> Unit
) {
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
    }

    val maxBytes = (segments.maxOfOrNull { it.sizeBytes } ?: 1L).coerceAtLeast(1L)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(segments) {
                detectTapGestures { tapOffset ->
                    val barCount = segments.size
                    val slotWidth = size.width / barCount
                    val index = (tapOffset.x / slotWidth).toInt().coerceIn(0, barCount - 1)
                    onSegmentTapped(segments[index].categoryKey)
                }
            }
            .testTag("recharts_bar_canvas")
    ) {
        val width = size.width
        val height = size.height
        val bottomMargin = 28f
        val topMargin = 20f
        val plotHeight = height - bottomMargin - topMargin
        val barCount = segments.size
        val slotWidth = width / barCount
        val barWidth = slotWidth * 0.52f

        // Cartesian Grid Horizontal lines (Recharts style)
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = topMargin + (plotHeight / gridLines) * i
            drawLine(
                color = Color.Gray.copy(alpha = 0.2f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
        }

        // Draw Bars
        segments.forEachIndexed { i, seg ->
            val isSelected = seg.categoryKey == selectedSegmentKey
            val ratio = (seg.sizeBytes.toFloat() / maxBytes.toFloat()).coerceIn(0.02f, 1f)
            val barHeight = plotHeight * ratio * animProgress.value
            val left = (i * slotWidth) + (slotWidth - barWidth) / 2f
            val top = topMargin + plotHeight - barHeight

            // Bar background slot highlight if selected
            if (isSelected) {
                drawRoundRect(
                    color = seg.color.copy(alpha = 0.12f),
                    topLeft = Offset(i * slotWidth + 4f, topMargin),
                    size = Size(slotWidth - 8f, plotHeight),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }

            // Foreground Bar
            drawRoundRect(
                color = seg.color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Selection indicator dot
            if (isSelected) {
                drawCircle(
                    color = seg.color,
                    radius = 4f,
                    center = Offset(left + barWidth / 2f, top - 8f)
                )
            }
        }
    }
}

/**
 * Recharts-style Stacked Horizontal Distribution Bar
 */
@Composable
private fun RechartsStackedBarChart(
    segments: List<RechartsMediaSegment>,
    totalMediaBytes: Long,
    freeDeviceStorage: Long,
    totalDeviceStorage: Long,
    selectedSegmentKey: String?,
    onSegmentTapped: (String) -> Unit
) {
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceAround
    ) {
        Text(
            text = "Linear Partition Breakdown (Recharts Stacked Area)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Stacked Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Gray.copy(alpha = 0.2f))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width * animProgress.value
                val h = size.height

                if (totalMediaBytes > 0) {
                    var currentX = 0f
                    segments.filter { it.sizeBytes > 0 }.forEach { segment ->
                        val segmentWidth = (segment.sizeBytes.toFloat() / totalMediaBytes.toFloat()) * w
                        val isSelected = segment.categoryKey == selectedSegmentKey

                        drawRect(
                            color = if (isSelected) segment.color else segment.color.copy(alpha = 0.85f),
                            topLeft = Offset(currentX, 0f),
                            size = Size(segmentWidth, h)
                        )
                        currentX += segmentWidth
                    }
                }
            }
        }

        // Percentage indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            segments.forEach { segment ->
                val pct = if (totalMediaBytes > 0) (segment.sizeBytes.toFloat() / totalMediaBytes * 100f) else 0f
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = segment.label.take(3),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = segment.color
                    )
                    Text(
                        text = "%.0f%%".format(pct),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
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
