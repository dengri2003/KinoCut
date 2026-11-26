package com.example

import com.example.domain.model.ColorGradeSettings
import com.example.domain.model.Keyframe
import com.example.domain.model.LutPreset
import com.example.domain.model.SpeedCurvePreset
import com.example.domain.model.TextOverlayItem
import com.example.domain.model.VideoClip
import com.example.domain.model.VideoProject
import com.example.media.MediaEffectsBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEditorEngineTest {

    @Test
    fun clipDuration_respectsTrimAndSpeed() {
        val clip = VideoClip(
            uriString = "content://media/video/1",
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
    fun keyframeInterpolation_easesSmoothlyBetweenPoints() {
        val k1 = Keyframe(offsetMs = 0L, scale = 1.0f, offsetX = 0f, rotationDeg = 0f)
        val k2 = Keyframe(offsetMs = 1_000L, scale = 2.0f, offsetX = 0.5f, rotationDeg = 90f)

        val mid = MediaEffectsBuilder.interpolateKeyframes(listOf(k1, k2), 500L)
        assertEquals(1.5f, mid.scale, 0.01f)
        assertEquals(0.25f, mid.offsetX, 0.01f)
        assertEquals(45f, mid.rotationDeg, 0.1f)

        val early = MediaEffectsBuilder.interpolateKeyframes(listOf(k1, k2), 200L)
        assertTrue(early.scale > 1.0f && early.scale < 1.2f)
    }

    @Test
    fun colorGrading_combinesLutPresetAndManualOffsetSafely() {
        val grade = ColorGradeSettings(
            lutPreset = LutPreset.TEAL_ORANGE,
            contrast = 0.2f,
            saturation = 20f
        )
        val effective = MediaEffectsBuilder.resolveEffectiveColorGrade(grade)
        assertEquals(0.38f, effective.contrast, 0.01f)
        assertEquals(38f, effective.saturation, 0.01f)
    }

    @Test
    fun projectTotalDuration_takesMaxOfClipsAndTextTrack() {
        val project = VideoProject(
            clips = listOf(
                VideoClip(uriString = "u1", sourceDurationMs = 4_000L, trimEndMs = 4_000L),
                VideoClip(uriString = "u2", sourceDurationMs = 3_000L, trimEndMs = 3_000L)
            ),
            textOverlays = listOf(
                TextOverlayItem(text = "Intro", startMs = 0L, endMs = 8_500L)
            )
        )
        assertEquals(8_500L, project.totalDurationMs)
    }
}
