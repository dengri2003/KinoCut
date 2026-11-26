package com.example.ui.editor

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.StudioSettings
import com.example.domain.model.ExportCodec
import com.example.domain.model.ExportResolution
import com.example.domain.model.ExportState
import com.example.domain.model.VideoProject
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.CoralCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedConsole
import com.example.ui.theme.EmeraldWave
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    project: VideoProject,
    settings: StudioSettings,
    exportState: ExportState,
    onDismiss: () -> Unit,
    onStartExport: (ExportResolution, Int, ExportCodec) -> Unit,
    onCancelExport: () -> Unit
) {
    val context = LocalContext.current
    var resolution by remember { mutableStateOf(settings.defaultResolution) }
    var fps by remember { mutableIntStateOf(project.targetFps) }
    var codec by remember { mutableStateOf(settings.defaultCodec) }

    ModalBottomSheet(
        onDismissRequest = {
            if (exportState !is ExportState.Exporting) onDismiss()
        },
        containerColor = ConsoleSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Экспорт мастера",
                style = MaterialTheme.typography.headlineSmall
            )

            when (exportState) {
                is ExportState.Idle -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = ElevatedConsole,
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(project.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${project.aspectRatio.badge} • ${project.clips.size} клип(а) • Без водяного знака",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = formatTimecode(project.totalDurationMs, fps),
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }
                    }

                    Text("Разрешение", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportResolution.entries.forEach { res ->
                            FilterChip(
                                selected = resolution == res,
                                onClick = { resolution = res },
                                label = { Text(res.label.substringBefore(' ')) }
                            )
                        }
                    }

                    Text("Частота кадров", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(24, 30, 60).forEach { candidate ->
                            FilterChip(
                                selected = fps == candidate,
                                onClick = { fps = candidate },
                                label = { Text("$candidate fps") }
                            )
                        }
                    }

                    Text("Кодек", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportCodec.entries.forEach { c ->
                            FilterChip(
                                selected = codec == c,
                                onClick = { codec = c },
                                label = { Text(c.label) }
                            )
                        }
                    }

                    val estMb = ((resolution.bitrateBps / 8.0) * (project.totalDurationMs / 1000.0) / (1024.0 * 1024.0))
                        .coerceAtLeast(0.5)
                    Text(
                        text = String.format(Locale.US, "Оценочный размер MP4: ~%.1f МБ", estMb),
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberGold
                    )

                    Button(
                        onClick = { onStartExport(resolution, fps, codec) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBg
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("confirm_export_button")
                    ) {
                        Icon(Icons.Default.MovieCreation, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Рендерить MP4",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is ExportState.Exporting -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                            CircularProgressIndicator(
                                progress = { exportState.progressPercent / 100f },
                                color = ElectricCyan,
                                trackColor = HairlineBorder,
                                strokeWidth = 6.dp,
                                modifier = Modifier.size(84.dp)
                            )
                            Text(
                                text = "${exportState.progressPercent}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = ElectricCyan
                            )
                        }
                        Text(
                            text = exportState.stageLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        LinearProgressIndicator(
                            progress = { exportState.progressPercent / 100f },
                            color = ElectricCyan,
                            trackColor = HairlineBorder,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = onCancelExport,
                            border = BorderStroke(1.dp, CoralCrimson),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralCrimson)
                        ) {
                            Text("Отменить рендер")
                        }
                    }
                }

                is ExportState.Completed -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Готово",
                            tint = EmeraldWave,
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "Видео готово и сохранено в Галерею",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val sizeMb = exportState.fileSizeBytes / (1024.0 * 1024.0)
                        Text(
                            text = String.format(Locale.US, "Размер: %.2f МБ • %s", sizeMb, formatTimecode(exportState.durationMs, fps)),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "video/mp4"
                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(exportState.outputUriString))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Поделиться видео"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Поделиться")
                            }
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Готово")
                            }
                        }
                    }
                }

                is ExportState.Failed -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Ошибка",
                            tint = CoralCrimson,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(exportState.message, style = MaterialTheme.typography.bodyMedium, color = CoralCrimson)
                        TextButton(onClick = onDismiss) {
                            Text("Закрыть")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
