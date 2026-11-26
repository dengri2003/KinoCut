package com.example

import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ColorGradeSettings
import com.example.domain.model.Keyframe
import com.example.domain.model.LutPreset
import com.example.domain.model.SpeedCurvePreset
import com.example.domain.model.VideoClip
import com.example.domain.model.VideoProject
import com.example.media.MediaEffectsBuilder
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEditorCoreTest {

    @Test
    fun `clip timeline duration reflects speed and trim`() {
        val clip = VideoClip(
            uriString = "file:///tmp/test.mp4",
            sourceDurationMs = 10_000L,
            trimStartMs = 2_000L,
            trimEndMs = 8_000L,
            speed = 2.0f,
            speedCurve = SpeedCurvePreset.NORMAL
        )
        assertEquals(6_000L, clip.trimmedSourceDurationMs)
        assertEquals(3_000L, clip.timelineDurationMs)
    }

    @Test
    fun `keyframe interpolation uses smooth easing between keyframes`() {
        val kfs = listOf(
            Keyframe(offsetMs = 0L, scale = 1.0f, offsetX = 0f, rotationDeg = 0f),
            Keyframe(offsetMs = 1000L, scale = 2.0f, offsetX = 0.5f, rotationDeg = 90f)
        )
        val mid = MediaEffectsBuilder.interpolateKeyframes(kfs, 500L)
        assertEquals(1.5f, mid.scale, 0.01f)
        assertEquals(0.25f, mid.offsetX, 0.01f)
        assertEquals(45f, mid.rotationDeg, 0.1f)
    }

    @Test
    fun `lut color grade combines lut preset and manual tweaks`() {
        val grade = ColorGradeSettings(
            lutPreset = LutPreset.TEAL_ORANGE,
            contrast = 0.10f
        )
        val resolved = MediaEffectsBuilder.resolveEffectiveColorGrade(grade)
        assertEquals(LutPreset.TEAL_ORANGE.contrast + 0.10f, resolved.contrast, 0.001f)
        assertEquals(LutPreset.TEAL_ORANGE.saturation, resolved.saturation, 0.001f)
    }

    @Test
    fun `video project serialization roundtrip preserves clips and overlays`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val project = VideoProject(
            title = "Cinema Reel",
            aspectRatio = AspectRatioPreset.RATIO_239_1,
            clips = listOf(
                VideoClip(uriString = "file:///tmp/a.mp4", title = "Intro", sourceDurationMs = 4000L, trimEndMs = 4000L)
            )
        )
        val encoded = json.encodeToString(project)
        val decoded = json.decodeFromString<VideoProject>(encoded)
        assertEquals("Cinema Reel", decoded.title)
        assertEquals(AspectRatioPreset.RATIO_239_1, decoded.aspectRatio)
        assertTrue(decoded.clips.size == 1)
    }
}
