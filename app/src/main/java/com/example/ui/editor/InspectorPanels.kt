package com.example.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AudioTrackItem
import com.example.domain.model.ColorGradeSettings
import com.example.domain.model.EditorToolTab
import com.example.domain.model.LutPreset
import com.example.domain.model.SpeedCurvePreset
import com.example.domain.model.TextAnimationStyle
import com.example.domain.model.TextFontStyle
import com.example.domain.model.TextOverlayItem
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import com.example.domain.model.VideoProject
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CardConsole
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.CoralCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedConsole
import com.example.ui.theme.EmeraldWave
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletKey
import java.util.Locale

@Composable
fun InspectorDock(
    activeTab: EditorToolTab,
    project: VideoProject,
    selectedClip: VideoClip?,
    selectedText: TextOverlayItem?,
    selectedAudio: AudioTrackItem?,
    playheadMs: Long,
    clipStartMs: Long,
    onSelectTab: (EditorToolTab) -> Unit,
    onSplit: () -> Unit,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onDuplicate: () -> Unit,
    onDeleteClip: () -> Unit,
    onMoveClip: (Int) -> Unit,
    onScaleClip: (Float) -> Unit,
    onSpeedChange: (Float, SpeedCurvePreset) -> Unit,
    onColorGradeChange: (ColorGradeSettings) -> Unit,
    onAddKeyframe: (Float, Float, Float, Float) -> Unit,
    onRemoveKeyframe: (String) -> Unit,
    onSeekTo: (Long) -> Unit,
    onAddText: () -> Unit,
    onUpdateText: (TextOverlayItem) -> Unit,
    onDeleteText: (String) -> Unit,
    onAddAudioClick: () -> Unit,
    onUpdateClipAudio: (Float, Boolean, Long, Long) -> Unit,
    onUpdateAudioTrack: (AudioTrackItem) -> Unit,
    onDeleteAudioTrack: (String) -> Unit,
    onUpdateTransition: (TransitionType, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ConsoleSurface)
    ) {
        // Active Tool Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .height(210.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when (activeTab) {
                EditorToolTab.CUT -> CutAndSpeedPanel(
                    clip = selectedClip,
                    canDelete = project.clips.size > 1,
                    onSplit = onSplit,
                    onRotate = onRotate,
                    onFlip = onFlip,
                    onDuplicate = onDuplicate,
                    onDelete = onDeleteClip,
                    onMove = onMoveClip,
                    onScaleChange = onScaleClip,
                    onSpeedChange = onSpeedChange
                )
                EditorToolTab.COLOR -> ColorGradingPanel(
                    clip = selectedClip,
                    onGradeChange = onColorGradeChange
                )
                EditorToolTab.KEYFRAME -> KeyframePanel(
                    clip = selectedClip,
                    playheadMs = playheadMs,
                    clipStartMs = clipStartMs,
                    onAddKeyframe = onAddKeyframe,
                    onRemoveKeyframe = onRemoveKeyframe,
                    onSeekTo = onSeekTo
                )
                EditorToolTab.TEXT -> TextOverlayPanel(
                    project = project,
                    selectedText = selectedText,
                    onAddText = onAddText,
                    onUpdateText = onUpdateText,
                    onDeleteText = onDeleteText
                )
                EditorToolTab.AUDIO -> AudioMixerPanel(
                    clip = selectedClip,
                    selectedAudio = selectedAudio,
                    onAddAudioClick = onAddAudioClick,
                    onUpdateClipAudio = onUpdateClipAudio,
                    onUpdateAudioTrack = onUpdateAudioTrack,
                    onDeleteAudioTrack = onDeleteAudioTrack
                )
                EditorToolTab.TRANSITION -> TransitionPanel(
                    clip = selectedClip,
                    onUpdateTransition = onUpdateTransition
                )
            }
        }

        // Bottom 6-Tool Studio Dock Bar
        Surface(
            color = ObsidianBg,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EditorToolTab.entries.forEach { tab ->
                    val selected = tab == activeTab
                    val icon = when (tab) {
                        EditorToolTab.CUT -> Icons.Default.Speed
                        EditorToolTab.COLOR -> Icons.Default.Palette
                        EditorToolTab.KEYFRAME -> Icons.Default.AutoAwesome
                        EditorToolTab.TEXT -> Icons.Default.TextFields
                        EditorToolTab.AUDIO -> Icons.Default.MusicNote
                        EditorToolTab.TRANSITION -> Icons.Default.SwapHoriz
                    }
                    val accent = when (tab) {
                        EditorToolTab.COLOR -> AmberGold
                        EditorToolTab.KEYFRAME -> VioletKey
                        EditorToolTab.AUDIO -> EmeraldWave
                        else -> ElectricCyan
                    }
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectTab(tab) }
                            .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("tool_tab_${tab.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.title,
                            tint = if (selected) accent else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) accent else TextSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CutAndSpeedPanel(
    clip: VideoClip?,
    canDelete: Boolean,
    onSplit: () -> Unit,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onScaleChange: (Float) -> Unit,
    onSpeedChange: (Float, SpeedCurvePreset) -> Unit
) {
    if (clip == null) {
        Text("Выберите клип на таймлайне", color = TextSecondary)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Quick Action Pill Row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ToolActionPill(Icons.Default.ContentCut, "Разделить", ElectricCyan, onClick = onSplit)
            }
            item {
                ToolActionPill(Icons.Default.RotateRight, "Поворот", TextSecondary, onClick = onRotate)
            }
            item {
                ToolActionPill(Icons.Default.Flip, "Отразить", TextSecondary, onClick = onFlip)
            }
            item {
                ToolActionPill(Icons.Default.ContentCopy, "Дубликат", TextSecondary, onClick = onDuplicate)
            }
            item {
                ToolActionPill(Icons.AutoMirrored.Filled.ArrowBack, "Левее", TextSecondary, onClick = { onMove(-1) })
            }
            item {
                ToolActionPill(Icons.AutoMirrored.Filled.ArrowForward, "Правее", TextSecondary, onClick = { onMove(1) })
            }
            if (canDelete) {
                item {
                    ToolActionPill(Icons.Default.DeleteOutline, "Удалить", CoralCrimson, onClick = onDelete)
                }
            }
        }

        // Speed Curve Presets + Live Spline Preview
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Кривая скорости", style = MaterialTheme.typography.titleSmall)
            Text(
                text = String.format(Locale.US, "%.2fx", clip.speed),
                style = MaterialTheme.typography.labelMedium,
                color = AmberGold
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SpeedCurvePreset.entries) { preset ->
                val active = clip.speedCurve == preset
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) AmberGold.copy(alpha = 0.18f) else ElevatedConsole,
                    border = BorderStroke(1.dp, if (active) AmberGold else HairlineBorder),
                    modifier = Modifier
                        .width(82.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSpeedChange(clip.speed, preset) }
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SpeedCurveGraph(
                            multipliers = preset.multipliers,
                            color = if (active) AmberGold else TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = preset.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) AmberGold else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        ProSliderRow(
            label = "Базовая скорость",
            valueText = String.format(Locale.US, "%.2fx", clip.speed),
            value = clip.speed,
            range = 0.25f..4.0f,
            accent = AmberGold,
            onValueChange = { onSpeedChange(it, clip.speedCurve) }
        )

        ProSliderRow(
            label = "Масштаб кадра",
            valueText = String.format(Locale.US, "%.0f%%", clip.scale * 100f),
            value = clip.scale,
            range = 0.6f..2.5f,
            accent = ElectricCyan,
            onValueChange = onScaleChange
        )
    }
}

@Composable
private fun SpeedCurveGraph(
    multipliers: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (multipliers.size < 2) return@Canvas
        val path = Path()
        val maxVal = 3.5f
        multipliers.forEachIndexed { i, v ->
            val x = (i.toFloat() / (multipliers.size - 1)) * size.width
            val y = size.height - ((v / maxVal).coerceIn(0.08f, 0.95f) * size.height)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun ColorGradingPanel(
    clip: VideoClip?,
    onGradeChange: (ColorGradeSettings) -> Unit
) {
    if (clip == null) {
        Text("Выберите клип для цветокоррекции", color = TextSecondary)
        return
    }
    val grade = clip.colorGrade
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Кино-LUT профили", style = MaterialTheme.typography.titleSmall)
            TextButton(
                onClick = { onGradeChange(ColorGradeSettings()) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Сброс", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LutPreset.entries) { preset ->
                val selected = grade.lutPreset == preset
                val accent = Color(preset.accentHex)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) accent.copy(alpha = 0.18f) else ElevatedConsole,
                    border = BorderStroke(1.dp, if (selected) accent else HairlineBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onGradeChange(grade.copy(lutPreset = preset)) }
                        .testTag("lut_${preset.name.lowercase()}")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 46.dp, height = 18.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(accent.copy(alpha = 0.4f), accent, ObsidianBg)
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = preset.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) accent else TextSecondary
                        )
                    }
                }
            }
        }

        ProSliderRow(
            label = "Яркость",
            valueText = String.format(Locale.US, "%+.0f", grade.brightness * 100),
            value = grade.brightness,
            range = -0.5f..0.5f,
            accent = AmberGold,
            onValueChange = { onGradeChange(grade.copy(brightness = it)) }
        )
        ProSliderRow(
            label = "Контраст",
            valueText = String.format(Locale.US, "%+.0f", grade.contrast * 100),
            value = grade.contrast,
            range = -0.5f..0.8f,
            accent = ElectricCyan,
            onValueChange = { onGradeChange(grade.copy(contrast = it)) }
        )
        ProSliderRow(
            label = "Насыщенность",
            valueText = String.format(Locale.US, "%+.0f", grade.saturation),
            value = grade.saturation,
            range = -100f..100f,
            accent = EmeraldWave,
            onValueChange = { onGradeChange(grade.copy(saturation = it)) }
        )
        ProSliderRow(
            label = "Температура",
            valueText = String.format(Locale.US, "%+.0f", grade.temperature * 100),
            value = grade.temperature,
            range = -0.5f..0.5f,
            accent = AmberGold,
            onValueChange = { onGradeChange(grade.copy(temperature = it)) }
        )
        ProSliderRow(
            label = "Оттенок (Hue)",
            valueText = String.format(Locale.US, "%+.0f°", grade.hue),
            value = grade.hue,
            range = -45f..45f,
            accent = VioletKey,
            onValueChange = { onGradeChange(grade.copy(hue = it)) }
        )
    }
}

@Composable
private fun KeyframePanel(
    clip: VideoClip?,
    playheadMs: Long,
    clipStartMs: Long,
    onAddKeyframe: (Float, Float, Float, Float) -> Unit,
    onRemoveKeyframe: (String) -> Unit,
    onSeekTo: (Long) -> Unit
) {
    if (clip == null) {
        Text("Выберите клип для анимации ключевыми кадрами", color = TextSecondary)
        return
    }
    val localMs = (playheadMs - clipStartMs).coerceIn(0L, clip.timelineDurationMs)
    var scale by remember(clip.id, localMs / 150) { mutableFloatStateOf(1.15f) }
    var offsetX by remember(clip.id, localMs / 150) { mutableFloatStateOf(0f) }
    var offsetY by remember(clip.id, localMs / 150) { mutableFloatStateOf(0f) }
    var rotDeg by remember(clip.id, localMs / 150) { mutableFloatStateOf(0f) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Анимация (Keyframes ◆)", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = String.format(Locale.US, "Позиция в клипе: %.2f с", localMs / 1000f),
                    style = MaterialTheme.typography.labelSmall,
                    color = VioletKey
                )
            }
            Button(
                onClick = { onAddKeyframe(scale, offsetX, offsetY, rotDeg) },
                colors = ButtonDefaults.buttonColors(containerColor = VioletKey, contentColor = TextPrimary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("add_keyframe_button")
            ) {
                Text("◆ Добавить ключ", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (clip.keyframes.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(clip.keyframes, key = { it.id }) { kf ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ElevatedConsole,
                        border = BorderStroke(1.dp, VioletKey.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { onSeekTo(clipStartMs + kf.offsetMs) }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(Locale.US, "◆ %.1fс (%.1fx)", kf.offsetMs / 1000f, kf.scale),
                                style = MaterialTheme.typography.labelSmall,
                                color = VioletKey
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Удалить ключ",
                                tint = TextMuted,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onRemoveKeyframe(kf.id) }
                            )
                        }
                    }
                }
            }
        }

        ProSliderRow("Масштаб ◆", String.format(Locale.US, "%.2fx", scale), scale, 0.5f..2.5f, VioletKey) { scale = it }
        ProSliderRow("Сдвиг X ◆", String.format(Locale.US, "%+.2f", offsetX), offsetX, -0.6f..0.6f, VioletKey) { offsetX = it }
        ProSliderRow("Сдвиг Y ◆", String.format(Locale.US, "%+.2f", offsetY), offsetY, -0.6f..0.6f, VioletKey) { offsetY = it }
        ProSliderRow("Поворот ◆", String.format(Locale.US, "%+.0f°", rotDeg), rotDeg, -45f..45f, VioletKey) { rotDeg = it }
    }
}

@Composable
private fun TextOverlayPanel(
    project: VideoProject,
    selectedText: TextOverlayItem?,
    onAddText: () -> Unit,
    onUpdateText: (TextOverlayItem) -> Unit,
    onDeleteText: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Титры и субтитры (${project.textOverlays.size})", style = MaterialTheme.typography.titleSmall)
            Button(
                onClick = onAddText,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("add_text_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Новый титр", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (selectedText == null) {
            Text(
                text = "Нажмите «Новый титр» или выберите титр на дорожке T1 / прямо на видео для перетаскивания.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            return
        }

        OutlinedTextField(
            value = selectedText.text,
            onValueChange = { onUpdateText(selectedText.copy(text = it)) },
            label = { Text("Текст титра") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(TextFontStyle.entries) { font ->
                FilterChip(
                    selected = selectedText.fontStyle == font,
                    onClick = { onUpdateText(selectedText.copy(fontStyle = font)) },
                    label = { Text(font.title, style = MaterialTheme.typography.labelSmall) }
                )
            }
            items(TextAnimationStyle.entries) { anim ->
                FilterChip(
                    selected = selectedText.animationStyle == anim,
                    onClick = { onUpdateText(selectedText.copy(animationStyle = anim)) },
                    label = { Text(anim.title, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VioletKey.copy(alpha = 0.25f),
                        selectedLabelColor = VioletKey
                    )
                )
            }
        }

        val textColors = listOf(0xFFFFFFFF, 0xFF00E5FF, 0xFFFFB800, 0xFFFF3D71, 0xFF00E676, 0xFF0A0B0E)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                textColors.forEach { hex ->
                    val active = selectedText.colorHex == hex
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .clickable { onUpdateText(selectedText.copy(colorHex = hex)) }
                            .then(
                                if (active) Modifier.padding(2.dp) else Modifier
                            )
                    )
                }
            }
            TextButton(onClick = { onDeleteText(selectedText.id) }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = CoralCrimson, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Удалить", color = CoralCrimson)
            }
        }

        ProSliderRow(
            label = "Размер шрифта",
            valueText = "${selectedText.fontSizeSp.toInt()} sp",
            value = selectedText.fontSizeSp,
            range = 14f..54f,
            accent = ElectricCyan,
            onValueChange = { onUpdateText(selectedText.copy(fontSizeSp = it)) }
        )
    }
}

@Composable
private fun AudioMixerPanel(
    clip: VideoClip?,
    selectedAudio: AudioTrackItem?,
    onAddAudioClick: () -> Unit,
    onUpdateClipAudio: (Float, Boolean, Long, Long) -> Unit,
    onUpdateAudioTrack: (AudioTrackItem) -> Unit,
    onDeleteAudioTrack: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Аудиомикшер", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = onAddAudioClick,
                border = BorderStroke(1.dp, EmeraldWave),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldWave),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.testTag("add_audio_button")
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("+ Музыка", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (clip != null && !clip.isImage) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ElevatedConsole,
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Звук клипа: ${clip.title}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(
                            onClick = { onUpdateClipAudio(clip.volume, !clip.muted, clip.fadeInMs, clip.fadeOutMs) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (clip.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Выключить звук клипа",
                                tint = if (clip.muted) CoralCrimson else ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    ProSliderRow(
                        label = "Громкость",
                        valueText = "${(clip.volume * 100).toInt()}%",
                        value = clip.volume,
                        range = 0f..2f,
                        accent = ElectricCyan,
                        onValueChange = { onUpdateClipAudio(it, false, clip.fadeInMs, clip.fadeOutMs) }
                    )
                }
            }
        }

        if (selectedAudio != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CardConsole,
                border = BorderStroke(1.dp, EmeraldWave.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedAudio.title, style = MaterialTheme.typography.bodySmall, color = EmeraldWave, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Row {
                            Switch(
                                checked = !selectedAudio.muted,
                                onCheckedChange = { onUpdateAudioTrack(selectedAudio.copy(muted = !it)) }
                            )
                            IconButton(onClick = { onDeleteAudioTrack(selectedAudio.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить аудио", tint = CoralCrimson)
                            }
                        }
                    }
                    ProSliderRow(
                        label = "Уровень музыки",
                        valueText = "${(selectedAudio.volume * 100).toInt()}%",
                        value = selectedAudio.volume,
                        range = 0f..2f,
                        accent = EmeraldWave,
                        onValueChange = { onUpdateAudioTrack(selectedAudio.copy(volume = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransitionPanel(
    clip: VideoClip?,
    onUpdateTransition: (TransitionType, Long) -> Unit
) {
    if (clip == null) {
        Text("Выберите клип для настройки перехода", color = TextSecondary)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Переход после «${clip.title}»", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TransitionType.entries) { type ->
                val active = clip.transitionOut == type
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) AmberGold.copy(alpha = 0.2f) else ElevatedConsole,
                    border = BorderStroke(1.dp, if (active) AmberGold else HairlineBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onUpdateTransition(type, clip.transitionDurationMs) }
                ) {
                    Text(
                        text = type.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) AmberGold else TextSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
        ProSliderRow(
            label = "Длительность",
            valueText = String.format(Locale.US, "%.1f с", clip.transitionDurationMs / 1000f),
            value = clip.transitionDurationMs.toFloat(),
            range = 200f..1500f,
            accent = AmberGold,
            onValueChange = { onUpdateTransition(clip.transitionOut, it.toLong()) }
        )
    }
}

@Composable
private fun ToolActionPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ElevatedConsole,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ProSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(106.dp)
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = HairlineBorder
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            modifier = Modifier.width(52.dp)
        )
    }
}
