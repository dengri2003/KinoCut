package com.example.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.domain.model.ExportCodec
import com.example.domain.model.ExportResolution
import com.example.domain.model.ExportState
import com.example.domain.model.SpeedCurvePreset
import com.example.domain.model.VideoClip
import com.example.domain.model.VideoProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@UnstableApi
object CompositionEngine {

    fun buildComposition(
        context: Context,
        project: VideoProject,
        includeBakedTextOverlays: Boolean = false,
        applyColorGrading: Boolean = true,
        targetHeight: Int? = null
    ): Composition? {
        if (project.clips.isEmpty()) return null

        var timelineCursorMs = 0L
        val videoEditedItems = project.clips.map { clip ->
            val clipStartMs = timelineCursorMs
            val clipEndMs = clipStartMs + clip.timelineDurationMs
            timelineCursorMs = clipEndMs

            val overlappingText = if (includeBakedTextOverlays) {
                project.textOverlays.filter { overlay ->
                    overlay.endMs > clipStartMs && overlay.startMs < clipEndMs
                }
            } else {
                emptyList()
            }

            buildEditedMediaItem(
                context = context,
                clip = clip,
                project = project,
                textOverlays = overlappingText,
                clipStartMs = clipStartMs,
                applyColorGrading = applyColorGrading,
                targetHeight = targetHeight
            )
        }

        val hasAnyAudioInClips = project.clips.any { !it.isImage && !it.muted }
        val videoSequence = if (hasAnyAudioInClips) {
            EditedMediaItemSequence.withAudioAndVideoFrom(videoEditedItems)
        } else {
            EditedMediaItemSequence.withVideoFrom(videoEditedItems)
        }

        val sequences = mutableListOf(videoSequence)

        val activeAudioTracks = project.audioTracks.filter { !it.muted }
        if (activeAudioTracks.isNotEmpty()) {
            val audioEditedItems = activeAudioTracks.map { track ->
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(track.uriString))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(track.trimStartMs.coerceAtLeast(0L))
                            .setEndPositionMs(track.trimEndMs.coerceAtLeast(track.trimStartMs + 500L))
                            .build()
                    )
                    .build()

                val gainProcessor = GainFadeAudioProcessor(
                    gain = track.volume.coerceIn(0f, 2f),
                    fadeInMs = track.fadeInMs,
                    fadeOutMs = track.fadeOutMs,
                    totalDurationMs = track.timelineDurationMs
                )

                EditedMediaItem.Builder(mediaItem)
                    .setDurationUs(track.timelineDurationMs * 1000L)
                    .setEffects(androidx.media3.transformer.Effects(listOf(gainProcessor), emptyList()))
                    .build()
            }
            val audioSequence = EditedMediaItemSequence.withAudioFrom(audioEditedItems)
            sequences.add(audioSequence)
        }

        return Composition.Builder(sequences).build()
    }

    private fun buildEditedMediaItem(
        context: Context,
        clip: VideoClip,
        project: VideoProject,
        textOverlays: List<com.example.domain.model.TextOverlayItem>,
        clipStartMs: Long,
        applyColorGrading: Boolean,
        targetHeight: Int?
    ): EditedMediaItem {
        val uri = Uri.parse(clip.uriString)
        val mediaItemBuilder = MediaItem.Builder().setUri(uri)

        if (clip.isImage) {
            mediaItemBuilder.setImageDurationMs(clip.timelineDurationMs)
        } else {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L))
                    .setEndPositionMs(clip.trimEndMs.coerceAtLeast(clip.trimStartMs + 250L))
                    .build()
            )
        }

        val effects = MediaEffectsBuilder.buildClipEffects(
            context = context,
            clip = clip,
            aspectRatio = project.aspectRatio,
            textOverlaysForClip = textOverlays,
            clipStartInTimelineMs = clipStartMs,
            applyColorGrading = applyColorGrading,
            targetHeight = targetHeight
        )

        val builder = EditedMediaItem.Builder(mediaItemBuilder.build())
            .setEffects(effects)

        if (clip.isImage) {
            builder.setFrameRate(project.targetFps.coerceIn(24, 60))
        } else {
            builder.setDurationUs(clip.trimmedSourceDurationMs * 1000L)
            builder.setRemoveAudio(clip.muted)
        }

        if (clip.speed != 1f || clip.speedCurve != SpeedCurvePreset.NORMAL) {
            val totalUs = clip.trimmedSourceDurationMs * 1000L
            builder.setSpeed(ClipSpeedProvider(clip.speed, clip.speedCurve, totalUs))
        }

        return builder.build()
    }
}

@UnstableApi
private class ClipSpeedProvider(
    private val baseSpeed: Float,
    private val curve: SpeedCurvePreset,
    private val durationUs: Long
) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float {
        if (curve == SpeedCurvePreset.NORMAL) return baseSpeed.coerceIn(0.1f, 8f)
        val multipliers = curve.multipliers
        if (multipliers.isEmpty() || durationUs <= 0L) return baseSpeed.coerceIn(0.1f, 8f)
        val progress = (timeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 0.9999f)
        val scaled = progress * (multipliers.size - 1)
        val idx = scaled.toInt().coerceIn(0, multipliers.size - 2)
        val frac = scaled - idx
        val m = multipliers[idx] + (multipliers[idx + 1] - multipliers[idx]) * frac
        return (baseSpeed * m).coerceIn(0.1f, 8f)
    }

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
        if (curve == SpeedCurvePreset.NORMAL || durationUs <= 0L) return androidx.media3.common.C.TIME_UNSET
        val stepUs = (durationUs / 16L).coerceAtLeast(33_333L)
        val next = timeUs + stepUs
        return if (next < durationUs) next else androidx.media3.common.C.TIME_UNSET
    }
}

@UnstableApi
class ExportManager(private val context: Context) {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeTransformer: Transformer? = null
    private var progressRunnable: Runnable? = null

    fun startExport(
        project: VideoProject,
        resolution: ExportResolution,
        fps: Int,
        codec: ExportCodec,
        onSavedToProject: (String) -> Unit
    ) {
        if (project.clips.isEmpty()) {
            _exportState.value = ExportState.Failed("Добавьте хотя бы один клип на таймлайн")
            return
        }

        val composition = CompositionEngine.buildComposition(
            context = context,
            project = project.copy(targetFps = fps),
            includeBakedTextOverlays = true,
            applyColorGrading = true,
            targetHeight = resolution.shortHeight
        ) ?: run {
            _exportState.value = ExportState.Failed("Не удалось собрать композицию")
            return
        }

        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "KinoCut_$timestamp.mp4"
        val tempFile = File(exportDir, fileName)

        _exportState.value = ExportState.Exporting(0, "Инициализация кодеков ${codec.label}...")

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                stopProgressPolling()
                val publishedUri = publishToMediaStore(tempFile, fileName)
                val finalUri = publishedUri ?: FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                ).toString()

                onSavedToProject(finalUri)
                _exportState.value = ExportState.Completed(
                    outputUriString = finalUri,
                    fileSizeBytes = exportResult.fileSizeBytes.takeIf { it > 0 } ?: tempFile.length(),
                    durationMs = exportResult.durationMs.takeIf { it > 0 } ?: project.totalDurationMs
                )
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                stopProgressPolling()
                _exportState.value = ExportState.Failed(
                    exportException.localizedMessage ?: "Ошибка аппаратного рендеринга"
                )
            }
        }

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(codec.mimeType)
            .addListener(listener)
            .build()

        activeTransformer = transformer
        transformer.start(composition, tempFile.absolutePath)

        val holder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                val t = activeTransformer ?: return
                val state = t.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val pct = holder.progress.coerceIn(0, 99)
                    val stage = when {
                        pct < 20 -> "Подготовка кадров и LUT..."
                        pct < 75 -> "Рендеринг видеодорожки ($pct%)..."
                        else -> "Финальное сведение звука и MP4..."
                    }
                    _exportState.value = ExportState.Exporting(pct, stage)
                }
                if (_exportState.value is ExportState.Exporting) {
                    mainHandler.postDelayed(this, 180L)
                }
            }
        }
        progressRunnable = runnable
        mainHandler.postDelayed(runnable, 180L)
    }

    fun cancelExport() {
        stopProgressPolling()
        activeTransformer?.cancel()
        activeTransformer = null
        _exportState.value = ExportState.Idle
    }

    fun resetState() {
        _exportState.value = ExportState.Idle
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun publishToMediaStore(sourceFile: File, displayName: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/KinoCut")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input -> input.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri.toString()
        }.getOrNull()
    }
}
