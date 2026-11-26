package com.example.ui.editor

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.domain.model.TextAnimationStyle
import com.example.domain.model.TextFontStyle
import com.example.domain.model.TextOverlayItem
import com.example.domain.model.VideoProject
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedConsole
import com.example.ui.theme.EmeraldWave
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(UnstableApi::class)
@Composable
fun PreviewViewport(
    project: VideoProject,
    player: Player?,
    playheadMs: Long,
    isPlaying: Boolean,
    selectedTextId: String?,
    compareBeforeAfter: Boolean,
    showGrid: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleCompare: () -> Unit,
    onToggleGrid: () -> Unit,
    onSelectText: (String) -> Unit,
    onMoveText: (TextOverlayItem, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            val aspect = project.aspectRatio.ratio
            var canvasSize by remember { mutableFloatStateOf(1f) }
            var containerSize by remember { mutableFloatStateOf(1f) }

            val boxModifier = if (maxWidth / maxHeight > aspect) {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(aspect)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
            }

            Box(
                modifier = boxModifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(10.dp))
                    .onSizeChanged { size: IntSize ->
                        canvasSize = size.width.toFloat().coerceAtLeast(1f)
                        containerSize = size.height.toFloat().coerceAtLeast(1f)
                    }
            ) {
                if (player != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            }
                        },
                        update = { view ->
                            if (view.player !== player) {
                                view.player = player
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Interactive Text Overlays active at current playheadMs
                val visibleTexts = project.textOverlays.filter {
                    playheadMs in it.startMs..it.endMs
                }
                visibleTexts.forEach { item ->
                    val elapsed = (playheadMs - item.startMs).coerceAtLeast(0L)
                    val displayText = when (item.animationStyle) {
                        TextAnimationStyle.TYPEWRITER -> {
                            val chars = ((elapsed / 55L).toInt()).coerceIn(1, item.text.length.coerceAtLeast(1))
                            item.text.take(chars)
                        }
                        else -> item.text
                    }
                    val animOffsetY = when (item.animationStyle) {
                        TextAnimationStyle.SLIDE_UP -> {
                            val p = (elapsed / 320f).coerceIn(0f, 1f)
                            ((1f - p) * 28f)
                        }
                        else -> 0f
                    }
                    val fontFamily = when (item.fontStyle) {
                        TextFontStyle.DISPLAY -> SpaceGroteskFontFamily
                        TextFontStyle.CLEAN -> PlusJakartaFontFamily
                        TextFontStyle.MONO -> JetBrainsMonoFontFamily
                    }

                    val isSelected = item.id == selectedTextId
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = ((item.xFraction - 0.5f) * canvasSize).roundToInt(),
                                    y = ((item.yFraction - 0.5f) * containerSize + animOffsetY).roundToInt()
                                )
                            }
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(item.backgroundHex))
                            .then(
                                if (isSelected) Modifier.border(1.5.dp, ElectricCyan, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .pointerInput(item.id, canvasSize, containerSize) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val nx = (item.xFraction + dragAmount.x / canvasSize).coerceIn(0.08f, 0.92f)
                                    val ny = (item.yFraction + dragAmount.y / containerSize).coerceIn(0.08f, 0.92f)
                                    onMoveText(item, nx, ny)
                                }
                            }
                            .clickable { onSelectText(item.id) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = displayText,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = (item.fontSizeSp * 0.68f).sp,
                            color = Color(item.colorHex)
                        )
                    }
                }

                // Rule of Thirds & Safe Zones Overlay
                if (showGrid) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val lineColor = Color.White.copy(alpha = 0.28f)
                        drawLine(lineColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
                        drawLine(lineColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1.dp.toPx())
                        drawLine(lineColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
                        drawLine(lineColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1.dp.toPx())
                    }
                }

                // Before/After Comparison Badge
                androidx.compose.animation.AnimatedVisibility(
                    visible = compareBeforeAfter,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AmberGold.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = "ДО ЦВЕТОКОРА",
                            style = MaterialTheme.typography.labelSmall,
                            color = ObsidianBg,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Precision Transport Bar
        Surface(
            color = ConsoleSurface,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatTimecode(playheadMs, project.targetFps),
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                    Text(
                        text = " / ${formatTimecode(project.totalDurationMs, project.targetFps)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                // Play / Pause Button
                Surface(
                    shape = CircleShape,
                    color = ElectricCyan,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                            tint = ObsidianBg,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Mini Stereo Peak Meter
                    AudioLevelMeter(
                        isPlaying = isPlaying,
                        playheadMs = playheadMs,
                        modifier = Modifier
                            .width(26.dp)
                            .height(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onToggleCompare,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Сравнить до и после",
                            tint = if (compareBeforeAfter) AmberGold else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleGrid,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Сетка третей",
                            tint = if (showGrid) ElectricCyan else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioLevelMeter(
    isPlaying: Boolean,
    playheadMs: Long,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val levelL = if (isPlaying) (0.35f + 0.55f * kotlin.math.abs(sin(playheadMs * 0.015f))).coerceIn(0.1f, 1f) else 0.12f
        val levelR = if (isPlaying) (0.35f + 0.55f * kotlin.math.abs(sin(playheadMs * 0.019f + 1.1f))).coerceIn(0.1f, 1f) else 0.12f
        val barH = size.height * 0.36f
        drawRoundRect(
            color = ElevatedConsole,
            size = androidx.compose.ui.geometry.Size(size.width, barH)
        )
        drawRoundRect(
            color = if (levelL > 0.85f) AmberGold else EmeraldWave,
            size = androidx.compose.ui.geometry.Size(size.width * levelL, barH)
        )
        drawRoundRect(
            color = ElevatedConsole,
            topLeft = Offset(0f, size.height - barH),
            size = androidx.compose.ui.geometry.Size(size.width, barH)
        )
        drawRoundRect(
            color = if (levelR > 0.85f) AmberGold else EmeraldWave,
            topLeft = Offset(0f, size.height - barH),
            size = androidx.compose.ui.geometry.Size(size.width * levelR, barH)
        )
    }
}

fun formatTimecode(ms: Long, fps: Int): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSec = safeMs / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    val frame = (((safeMs % 1000L) * fps) / 1000L).toInt().coerceIn(0, (fps - 1).coerceAtLeast(0))
    return String.format(Locale.US, "%02d:%02d:%02d", min, sec, frame)
}
