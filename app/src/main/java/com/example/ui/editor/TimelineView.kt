package com.example.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import com.example.domain.model.VideoProject
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CardConsole
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedConsole
import com.example.ui.theme.EmeraldWave
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ScrubberNeedle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletKey
import kotlin.math.roundToInt

@Composable
fun TimelineView(
    project: VideoProject,
    selectedClipId: String?,
    selectedTextId: String?,
    selectedAudioId: String?,
    playheadMs: Long,
    isPlaying: Boolean,
    snapEnabled: Boolean,
    filmstrips: Map<String, List<Bitmap>>,
    onScrubTo: (Long) -> Unit,
    onSelectClip: (String) -> Unit,
    onSelectTransition: (String) -> Unit,
    onSelectText: (String) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSplitAtPlayhead: () -> Unit,
    onToggleSnap: () -> Unit,
    onAddClipClick: () -> Unit,
    onAddAudioClick: () -> Unit,
    onTrimClipEdge: (VideoClip, Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dpPerSecond by remember { mutableFloatStateOf(46f) }
    val density = LocalDensity.current
    val pxPerMs = with(density) { (dpPerSecond / 1000f).dp.toPx() }
    val scrollState = rememberScrollState()

    // Sync scroll position when playing or seeking externally
    LaunchedEffect(playheadMs, isPlaying, dpPerSecond) {
        if (!scrollState.isScrollInProgress) {
            val targetPx = (playheadMs * pxPerMs).roundToInt()
            if (kotlin.math.abs(scrollState.value - targetPx) > 2) {
                scrollState.scrollTo(targetPx)
            }
        }
    }

    // Update playhead when user scrolls timeline manually
    LaunchedEffect(scrollState.value, scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress && pxPerMs > 0f) {
            val ms = (scrollState.value / pxPerMs).toLong().coerceIn(0L, project.totalDurationMs.coerceAtLeast(1L))
            onScrubTo(ms)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBg)
    ) {
        // Timeline Action Toolbar
        Surface(
            color = ConsoleSurface,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ElevatedConsole,
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onSplitAtPlayhead)
                            .testTag("timeline_split_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCut,
                                contentDescription = "Разделить",
                                tint = ElectricCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Разделить",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onToggleSnap,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AlignHorizontalCenter,
                            contentDescription = "Магнитная привязка",
                            tint = if (snapEnabled) AmberGold else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { dpPerSecond = (dpPerSecond / 1.3f).coerceIn(18f, 140f) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = "Отдалить таймлайн",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { dpPerSecond = (dpPerSecond * 1.3f).coerceIn(18f, 140f) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Приблизить таймлайн",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = CircleShape,
                        color = ElectricCyan,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onAddClipClick)
                            .testTag("add_clip_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Добавить клип",
                                tint = ObsidianBg,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Multi-Track Scrollable Canvas with Center Playhead Needle
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(178.dp)
        ) {
            val halfWidthDp = maxWidth / 2
            val totalContentWidthDp: Dp = ((project.totalDurationMs / 1000f) * dpPerSecond).dp.coerceAtLeast(120.dp)

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.width(halfWidthDp))

                Column(
                    modifier = Modifier
                        .width(totalContentWidthDp + 80.dp)
                        .fillMaxHeight()
                ) {
                    // Time Ruler
                    TimeRuler(
                        totalDurationMs = project.totalDurationMs,
                        dpPerSecond = dpPerSecond,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // V1 Video Track
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        project.clips.forEachIndexed { index, clip ->
                            val clipWidthDp = ((clip.timelineDurationMs / 1000f) * dpPerSecond).dp.coerceAtLeast(36.dp)
                            val isSelected = clip.id == selectedClipId
                            VideoTrackBlock(
                                clip = clip,
                                widthDp = clipWidthDp,
                                isSelected = isSelected,
                                frames = filmstrips[clip.id].orEmpty(),
                                dpPerSecond = dpPerSecond,
                                onClick = { onSelectClip(clip.id) },
                                onTrimEdge = onTrimClipEdge
                            )

                            if (index < project.clips.lastIndex) {
                                TransitionBadge(
                                    type = clip.transitionOut,
                                    onClick = { onSelectTransition(clip.id) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // T1 Text Overlay Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                    ) {
                        if (project.textOverlays.isEmpty()) {
                            Text(
                                text = "T1 • Дорожка титров",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 6.dp)
                            )
                        } else {
                            project.textOverlays.forEach { textItem ->
                                val offsetDp = ((textItem.startMs / 1000f) * dpPerSecond).dp
                                val wDp = (((textItem.endMs - textItem.startMs) / 1000f) * dpPerSecond).dp.coerceAtLeast(42.dp)
                                val selected = textItem.id == selectedTextId
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (selected) VioletKey.copy(alpha = 0.35f) else CardConsole,
                                    border = BorderStroke(1.dp, if (selected) VioletKey else HairlineBorder),
                                    modifier = Modifier
                                        .offset(x = offsetDp)
                                        .width(wDp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onSelectText(textItem.id) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TextFields,
                                            contentDescription = null,
                                            tint = VioletKey,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = textItem.text,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // A1 Audio Waveform Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        if (project.audioTracks.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ElevatedConsole,
                                border = BorderStroke(1.dp, HairlineBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(onClick = onAddAudioClick)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = EmeraldWave,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "+ Добавить музыку / звук",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldWave
                                    )
                                }
                            }
                        } else {
                            project.audioTracks.forEach { audio ->
                                val offsetDp = ((audio.startMs / 1000f) * dpPerSecond).dp
                                val wDp = ((audio.timelineDurationMs / 1000f) * dpPerSecond).dp.coerceAtLeast(60.dp)
                                val selected = audio.id == selectedAudioId
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (selected) EmeraldWave.copy(alpha = 0.22f) else CardConsole,
                                    border = BorderStroke(1.dp, if (selected) EmeraldWave else HairlineBorder),
                                    modifier = Modifier
                                        .offset(x = offsetDp)
                                        .width(wDp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onSelectAudio(audio.id) }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        val samples = audio.waveformSamples.ifEmpty {
                                            List(32) { idx -> 0.3f + ((idx * 37) % 7) * 0.09f }
                                        }
                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                        ) {
                                            val barW = (size.width / samples.size).coerceAtLeast(2f)
                                            samples.forEachIndexed { idx, amp ->
                                                val h = size.height * amp.coerceIn(0.12f, 1f)
                                                val x = idx * barW
                                                val y = (size.height - h) / 2f
                                                drawLine(
                                                    color = if (audio.muted) TextMuted else EmeraldWave.copy(alpha = 0.7f),
                                                    start = Offset(x, y),
                                                    end = Offset(x, y + h),
                                                    strokeWidth = (barW * 0.6f).coerceAtLeast(1.5f)
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.GraphicEq,
                                                contentDescription = null,
                                                tint = EmeraldWave,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = audio.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(halfWidthDp))
            }

            // Fixed Center Playhead Needle
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(ScrubberNeedle)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(width = 10.dp, height = 10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ElectricCyan)
            )
        }
    }
}

@Composable
private fun TimeRuler(
    totalDurationMs: Long,
    dpPerSecond: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val totalSec = ((totalDurationMs / 1000f) + 2f).toInt().coerceAtLeast(2)
        val pxPerSec = dpPerSecond.dp.toPx()
        for (s in 0..totalSec) {
            val x = s * pxPerSec
            if (x > size.width) break
            drawLine(
                color = TextSecondary.copy(alpha = 0.7f),
                start = Offset(x, size.height * 0.35f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx()
            )
            val halfX = x + pxPerSec * 0.5f
            if (halfX <= size.width) {
                drawLine(
                    color = TextMuted.copy(alpha = 0.45f),
                    start = Offset(halfX, size.height * 0.65f),
                    end = Offset(halfX, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
        drawLine(
            color = HairlineBorder,
            start = Offset(0f, size.height - 1f),
            end = Offset(size.width, size.height - 1f),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
private fun VideoTrackBlock(
    clip: VideoClip,
    widthDp: Dp,
    isSelected: Boolean,
    frames: List<Bitmap>,
    dpPerSecond: Float,
    onClick: () -> Unit,
    onTrimEdge: (VideoClip, Long, Long) -> Unit
) {
    val pxPerMs = with(LocalDensity.current) { (dpPerSecond / 1000f).dp.toPx() }
    val borderColor = if (isSelected) ElectricCyan else HairlineBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(CardConsole)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        // Real Video Filmstrip Frames
        if (frames.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                frames.forEach { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        // Subtle dark overlay for readable badges
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg.copy(alpha = 0.28f))
        )

        // Speed & Color badges
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (clip.effectiveSpeed != 1f) {
                Surface(shape = RoundedCornerShape(4.dp), color = AmberGold.copy(alpha = 0.9f)) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1fx", clip.effectiveSpeed),
                        style = MaterialTheme.typography.labelSmall,
                        color = ObsidianBg,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            if (!clip.colorGrade.isNeutral) {
                Surface(shape = RoundedCornerShape(4.dp), color = ElectricCyan.copy(alpha = 0.85f)) {
                    Text(
                        text = "LUT",
                        style = MaterialTheme.typography.labelSmall,
                        color = ObsidianBg,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Keyframe Diamonds along bottom edge
        if (clip.keyframes.isNotEmpty()) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(14.dp)
            ) {
                clip.keyframes.forEach { kf ->
                    val frac = (kf.offsetMs.toFloat() / clip.timelineDurationMs.coerceAtLeast(1L)).coerceIn(0.03f, 0.97f)
                    val cx = size.width * frac
                    val cy = size.height * 0.5f
                    val r = 5.dp.toPx()
                    val diamond = Path().apply {
                        moveTo(cx, cy - r)
                        lineTo(cx + r, cy)
                        lineTo(cx, cy + r)
                        lineTo(cx - r, cy)
                        close()
                    }
                    drawPath(diamond, color = VioletKey)
                }
            }
        }

        // Draggable Left & Right Trim Handles when selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(12.dp)
                    .fillMaxHeight()
                    .background(ElectricCyan)
                    .pointerInput(clip.id, pxPerMs) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaMs = (dragAmount / pxPerMs).toLong()
                            val newStart = (clip.trimStartMs + deltaMs).coerceIn(0L, clip.trimEndMs - 300L)
                            onTrimEdge(clip, newStart, clip.trimEndMs)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 2.dp, height = 18.dp)
                        .background(ObsidianBg, CircleShape)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(12.dp)
                    .fillMaxHeight()
                    .background(ElectricCyan)
                    .pointerInput(clip.id, pxPerMs) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaMs = (dragAmount / pxPerMs).toLong()
                            val newEnd = (clip.trimEndMs + deltaMs).coerceIn(clip.trimStartMs + 300L, clip.sourceDurationMs)
                            onTrimEdge(clip, clip.trimStartMs, newEnd)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 2.dp, height = 18.dp)
                        .background(ObsidianBg, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun TransitionBadge(
    type: TransitionType,
    onClick: () -> Unit
) {
    val active = type != TransitionType.NONE
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (active) AmberGold else ElevatedConsole,
        border = BorderStroke(1.dp, if (active) AmberGold else HairlineBorder),
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (active) "✦" else "+",
                style = MaterialTheme.typography.labelSmall,
                color = if (active) ObsidianBg else TextSecondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
