package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Effects
import com.example.R
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ColorGradeSettings
import com.example.domain.model.Keyframe
import com.example.domain.model.LutPreset
import com.example.domain.model.TextFontStyle
import com.example.domain.model.TextOverlayItem
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

@UnstableApi
object MediaEffectsBuilder {

    fun buildClipEffects(
        context: Context,
        clip: VideoClip,
        aspectRatio: AspectRatioPreset,
        textOverlaysForClip: List<TextOverlayItem> = emptyList(),
        clipStartInTimelineMs: Long = 0L,
        applyColorGrading: Boolean = true,
        targetHeight: Int? = null
    ): Effects {
        val videoEffects = mutableListOf<Effect>()
        val audioProcessors = mutableListOf<AudioProcessor>()

        // 1. Base Geometry (Rotation, Flip, Static Scale/Pan)
        val flipSignX = if (clip.flipHorizontal) -1f else 1f
        if (clip.rotationDegrees != 0f || clip.flipHorizontal || clip.scale != 1f) {
            videoEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(-clip.rotationDegrees)
                    .setScale(clip.scale * flipSignX, clip.scale)
                    .build()
            )
        }

        // 2. Keyframe Animation MatrixTransformation (Scale, Pan, Rotation over time)
        if (clip.keyframes.isNotEmpty() || clip.panX != 0f || clip.panY != 0f || clip.transitionOut != TransitionType.NONE) {
            val sortedKeyframes = clip.keyframes.sortedBy { it.offsetMs }
            val clipDurationMs = clip.timelineDurationMs
            videoEffects.add(
                MatrixTransformation { presentationTimeUs ->
                    val tMs = presentationTimeUs / 1000L
                    val matrix = Matrix()
                    if (sortedKeyframes.isNotEmpty()) {
                        val interp = interpolateKeyframes(sortedKeyframes, tMs)
                        matrix.postScale(interp.scale, interp.scale)
                        matrix.postRotate(-interp.rotationDeg)
                        matrix.postTranslate(interp.offsetX * 2f, -interp.offsetY * 2f)
                    } else if (clip.panX != 0f || clip.panY != 0f) {
                        matrix.postTranslate(clip.panX * 2f, -clip.panY * 2f)
                    }

                    // Outgoing transition motion polish (Zoom burst / Whip pan)
                    if (clip.transitionOut == TransitionType.ZOOM_BURST && clipDurationMs > 400L) {
                        val transStart = clipDurationMs - clip.transitionDurationMs
                        if (tMs >= transStart) {
                            val p = ((tMs - transStart).toFloat() / clip.transitionDurationMs).coerceIn(0f, 1f)
                            val zoom = 1f + (p * p * 0.45f)
                            matrix.postScale(zoom, zoom)
                        }
                    } else if (clip.transitionOut == TransitionType.WHIP_LEFT && clipDurationMs > 400L) {
                        val transStart = clipDurationMs - clip.transitionDurationMs
                        if (tMs >= transStart) {
                            val p = ((tMs - transStart).toFloat() / clip.transitionDurationMs).coerceIn(0f, 1f)
                            matrix.postTranslate(-p * p * 2.2f, 0f)
                        }
                    }
                    matrix
                }
            )
        }

        // 3. Color Grading & LUTs
        if (applyColorGrading) {
            val effectiveGrade = resolveEffectiveColorGrade(clip.colorGrade)
            if (abs(effectiveGrade.brightness) > 0.005f) {
                videoEffects.add(Brightness(effectiveGrade.brightness.coerceIn(-0.9f, 0.9f)))
            }
            if (abs(effectiveGrade.contrast) > 0.005f) {
                videoEffects.add(Contrast(effectiveGrade.contrast.coerceIn(-0.9f, 0.9f)))
            }
            if (abs(effectiveGrade.saturation) > 0.5f || abs(effectiveGrade.hue) > 0.5f) {
                videoEffects.add(
                    HslAdjustment.Builder()
                        .adjustSaturation(effectiveGrade.saturation.coerceIn(-100f, 100f))
                        .adjustHue(effectiveGrade.hue.coerceIn(-180f, 180f))
                        .build()
                )
            }
            if (abs(effectiveGrade.temperature) > 0.005f) {
                val temp = effectiveGrade.temperature.coerceIn(-0.5f, 0.5f)
                val rScale = (1f + temp * 0.45f).coerceIn(0.4f, 1.8f)
                val bScale = (1f - temp * 0.45f).coerceIn(0.4f, 1.8f)
                videoEffects.add(
                    RgbAdjustment.Builder()
                        .setRedScale(rScale)
                        .setGreenScale(1f)
                        .setBlueScale(bScale)
                        .build()
                )
            }
        }

        // 4. Baked-in Text Overlays for Export / Preview
        if (textOverlaysForClip.isNotEmpty()) {
            val bitmapOverlays = textOverlaysForClip.map { item ->
                val bmp = renderTextOverlayBitmap(context, item)
                val xGl = (item.xFraction * 2f - 1f).coerceIn(-0.9f, 0.9f)
                val yGl = (1f - item.yFraction * 2f).coerceIn(-0.9f, 0.9f)
                val settings = StaticOverlaySettings.Builder()
                    .setOverlayFrameAnchor(0f, 0f)
                    .setBackgroundFrameAnchor(xGl, yGl)
                    .setScale(0.75f, 0.75f)
                    .build()
                BitmapOverlay.createStaticBitmapOverlay(bmp, settings)
            }
            videoEffects.add(OverlayEffect(bitmapOverlays))
        }

        // 5. Output Aspect Ratio & Target Resolution Presentation
        if (targetHeight != null) {
            val targetWidth = ((targetHeight * aspectRatio.ratio).toInt() / 2) * 2
            val evenHeight = (targetHeight / 2) * 2
            videoEffects.add(
                Presentation.createForWidthAndHeight(
                    max(128, targetWidth),
                    max(128, evenHeight),
                    Presentation.LAYOUT_SCALE_TO_FIT
                )
            )
        } else {
            videoEffects.add(
                Presentation.createForAspectRatio(
                    aspectRatio.ratio,
                    Presentation.LAYOUT_SCALE_TO_FIT
                )
            )
        }

        // 6. Audio Gain & Fade Processor
        if (!clip.muted && (abs(clip.volume - 1f) > 0.01f || clip.fadeInMs > 0L || clip.fadeOutMs > 0L)) {
            audioProcessors.add(
                GainFadeAudioProcessor(
                    gain = clip.volume.coerceIn(0f, 2f),
                    fadeInMs = clip.fadeInMs,
                    fadeOutMs = clip.fadeOutMs,
                    totalDurationMs = clip.timelineDurationMs
                )
            )
        }

        return Effects(audioProcessors, videoEffects)
    }

    fun resolveEffectiveColorGrade(grade: ColorGradeSettings): ColorGradeSettings {
        if (grade.lutPreset == LutPreset.NONE) return grade
        val lut = grade.lutPreset
        return ColorGradeSettings(
            lutPreset = lut,
            brightness = (lut.brightness + grade.brightness).coerceIn(-0.8f, 0.8f),
            contrast = (lut.contrast + grade.contrast).coerceIn(-0.8f, 0.9f),
            saturation = (lut.saturation + grade.saturation).coerceIn(-100f, 100f),
            temperature = (lut.temperature + grade.temperature).coerceIn(-0.7f, 0.7f),
            hue = (lut.hue + grade.hue).coerceIn(-180f, 180f)
        )
    }

    fun interpolateKeyframes(sorted: List<Keyframe>, timeMs: Long): Keyframe {
        if (sorted.size == 1) return sorted.first()
        if (timeMs <= sorted.first().offsetMs) return sorted.first()
        if (timeMs >= sorted.last().offsetMs) return sorted.last()

        val nextIdx = sorted.indexOfFirst { it.offsetMs >= timeMs }.coerceAtLeast(1)
        val prev = sorted[nextIdx - 1]
        val next = sorted[nextIdx]
        val span = (next.offsetMs - prev.offsetMs).coerceAtLeast(1L).toFloat()
        val rawT = ((timeMs - prev.offsetMs) / span).coerceIn(0f, 1f)
        // Smooth cubic ease-in-out
        val t = rawT * rawT * (3f - 2f * rawT)

        return Keyframe(
            offsetMs = timeMs,
            scale = prev.scale + (next.scale - prev.scale) * t,
            offsetX = prev.offsetX + (next.offsetX - prev.offsetX) * t,
            offsetY = prev.offsetY + (next.offsetY - prev.offsetY) * t,
            rotationDeg = prev.rotationDeg + (next.rotationDeg - prev.rotationDeg) * t,
            opacity = prev.opacity + (next.opacity - prev.opacity) * t
        )
    }

    private fun renderTextOverlayBitmap(context: Context, item: TextOverlayItem): Bitmap {
        val typeface = when (item.fontStyle) {
            TextFontStyle.DISPLAY -> ResourcesCompat.getFont(context, R.font.space_grotesk) ?: Typeface.DEFAULT_BOLD
            TextFontStyle.CLEAN -> ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.SANS_SERIF
            TextFontStyle.MONO -> ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = item.colorHex.toInt()
            textSize = item.fontSizeSp * 3.2f
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            setShadowLayer(8f, 0f, 4f, 0x99000000.toInt())
        }

        val lines = item.text.ifBlank { " " }.split("\n")
        val maxLineW = lines.maxOfOrNull { textPaint.measureText(it) } ?: 100f
        val lineHeight = textPaint.fontSpacing
        val padH = 44f
        val padV = 26f
        val bmpW = max(64, (maxLineW + padH * 2).toInt())
        val bmpH = max(48, (lines.size * lineHeight + padV * 2).toInt())

        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgAlpha = (item.backgroundHex ushr 24) and 0xFF
        if (bgAlpha > 0) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = item.backgroundHex.toInt()
            }
            canvas.drawRoundRect(
                RectF(0f, 0f, bmpW.toFloat(), bmpH.toFloat()),
                28f, 28f, bgPaint
            )
        }

        var y = padV - textPaint.ascent()
        lines.forEach { line ->
            canvas.drawText(line, padH, y, textPaint)
            y += lineHeight
        }
        return bitmap
    }
}

@UnstableApi
class GainFadeAudioProcessor(
    private val gain: Float,
    private val fadeInMs: Long,
    private val fadeOutMs: Long,
    private val totalDurationMs: Long
) : BaseAudioProcessor() {

    private var processedBytes = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        processedBytes = 0L
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val out = replaceOutputBuffer(remaining)
        val bytesPerMs = max(
            1.0,
            (inputAudioFormat.sampleRate * inputAudioFormat.channelCount * 2) / 1000.0
        )

        while (inputBuffer.hasRemaining()) {
            val sample = inputBuffer.short
            val currentMs = (processedBytes / bytesPerMs).toLong()
            var fadeMultiplier = 1f
            if (fadeInMs > 0 && currentMs < fadeInMs) {
                fadeMultiplier *= (currentMs.toFloat() / fadeInMs).coerceIn(0f, 1f)
            }
            if (fadeOutMs > 0 && totalDurationMs > fadeOutMs && currentMs > totalDurationMs - fadeOutMs) {
                val remMs = (totalDurationMs - currentMs).coerceAtLeast(0L)
                fadeMultiplier *= (remMs.toFloat() / fadeOutMs).coerceIn(0f, 1f)
            }
            val scaled = (sample * gain * fadeMultiplier).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out.putShort(scaled.toShort())
            processedBytes += 2
        }
        out.flip()
    }
}
