package com.example.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class AspectRatioPreset(
    val label: String,
    val ratio: Float,
    val badge: String
) {
    RATIO_9_16("Reels / Shorts", 9f / 16f, "9:16"),
    RATIO_16_9("YouTube / Кино", 16f / 9f, "16:9"),
    RATIO_1_1("Квадрат", 1f, "1:1"),
    RATIO_4_5("Лента", 4f / 5f, "4:5"),
    RATIO_239_1("Анаморф", 2.39f, "2.39:1")
}

@Serializable
enum class ExportResolution(val label: String, val shortHeight: Int, val bitrateBps: Int) {
    HD_720P("720p HD", 720, 5_000_000),
    FHD_1080P("1080p Full HD", 1080, 12_000_000),
    UHD_4K("4K Ultra HD", 2160, 32_000_000)
}

@Serializable
enum class ExportCodec(val label: String, val mimeType: String) {
    H264("H.264 (Совмест.)", "video/avc"),
    H265("H.265 HEVC (Pro)", "video/hevc")
}

@Serializable
enum class LutPreset(
    val title: String,
    val accentHex: Long,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val hue: Float = 0f
) {
    NONE("Оригинал", 0xFF6B7385),
    TEAL_ORANGE("Teal & Orange", 0xFF00E5FF, brightness = 0.02f, contrast = 0.18f, saturation = 18f, temperature = 0.15f, hue = -4f),
    KODAK_PORTRA("Kodak Portra", 0xFFFFB800, brightness = 0.06f, contrast = 0.08f, saturation = -8f, temperature = 0.22f, hue = 3f),
    CYBER_NEON("Cyber Neon", 0xFF9D6BFF, brightness = -0.02f, contrast = 0.25f, saturation = 34f, temperature = -0.25f, hue = -12f),
    NOIR_BW("Noir 35mm", 0xFFB8C0CC, brightness = 0f, contrast = 0.30f, saturation = -100f, temperature = 0f, hue = 0f),
    GOLDEN_HOUR("Золотой час", 0xFFFF8F00, brightness = 0.05f, contrast = 0.12f, saturation = 16f, temperature = 0.35f, hue = 5f),
    BLEACH_BYPASS("Bleach Bypass", 0xFF8FA3B0, brightness = -0.04f, contrast = 0.34f, saturation = -38f, temperature = -0.08f, hue = 0f),
    FUJI_VELVIA("Fuji Velvia", 0xFF00E676, brightness = 0.02f, contrast = 0.20f, saturation = 28f, temperature = 0.05f, hue = 2f)
}

@Serializable
data class ColorGradeSettings(
    val lutPreset: LutPreset = LutPreset.NONE,
    val brightness: Float = 0f,     // -0.5 .. 0.5
    val contrast: Float = 0f,       // -0.5 .. 0.8
    val saturation: Float = 0f,     // -100 .. 100
    val temperature: Float = 0f,    // -0.5 .. 0.5 (cool to warm)
    val hue: Float = 0f             // -45 .. 45 degrees
) {
    val isNeutral: Boolean
        get() = lutPreset == LutPreset.NONE &&
            brightness == 0f &&
            contrast == 0f &&
            saturation == 0f &&
            temperature == 0f &&
            hue == 0f
}

@Serializable
enum class SpeedCurvePreset(
    val title: String,
    val multipliers: List<Float>
) {
    NORMAL("Ровно", listOf(1f, 1f, 1f, 1f, 1f)),
    MONTAGE("Монтаж", listOf(0.5f, 1.8f, 2.5f, 1.2f, 0.6f)),
    HERO("Герой", listOf(1.2f, 2.4f, 0.35f, 0.35f, 2.0f)),
    BULLET("Пуля", listOf(2.8f, 1.5f, 0.3f, 1.5f, 2.8f)),
    JUMP_CUT("Прыжок", listOf(0.4f, 0.8f, 3.0f, 0.8f, 0.4f)),
    FLASH_IN("Вспышка", listOf(3.2f, 2.0f, 1.0f, 1.0f, 1.0f))
}

@Serializable
data class Keyframe(
    val id: String = UUID.randomUUID().toString(),
    val offsetMs: Long,
    val scale: Float = 1f,
    val offsetX: Float = 0f,       // Normalized -1..1
    val offsetY: Float = 0f,       // Normalized -1..1
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f
)

@Serializable
enum class TransitionType(val title: String) {
    NONE("Склейка"),
    CROSSFADE("Растворение"),
    FLASH("Вспышка"),
    ZOOM_BURST("Зум-рывок"),
    WHIP_LEFT("Свист влево"),
    CINEMA_BARS("Кино-шторка")
}

@Serializable
data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val isImage: Boolean = false,
    val title: String = "Клип",
    val sourceDurationMs: Long = 5000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 5000L,
    val speed: Float = 1f,
    val speedCurve: SpeedCurvePreset = SpeedCurvePreset.NORMAL,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val scale: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val colorGrade: ColorGradeSettings = ColorGradeSettings(),
    val keyframes: List<Keyframe> = emptyList(),
    val transitionOut: TransitionType = TransitionType.NONE,
    val transitionDurationMs: Long = 500L,
    val width: Int = 1920,
    val height: Int = 1080
) {
    val trimmedSourceDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(250L)

    val effectiveSpeed: Float
        get() = if (speedCurve == SpeedCurvePreset.NORMAL) {
            speed.coerceIn(0.1f, 8f)
        } else {
            val avgCurve = speedCurve.multipliers.average().toFloat()
            (speed * avgCurve).coerceIn(0.1f, 8f)
        }

    val timelineDurationMs: Long
        get() = (trimmedSourceDurationMs / effectiveSpeed).toLong().coerceAtLeast(250L)
}

@Serializable
enum class TextFontStyle(val title: String) {
    DISPLAY("Space Grotesk"),
    CLEAN("Plus Jakarta"),
    MONO("JetBrains Mono")
}

@Serializable
enum class TextAnimationStyle(val title: String) {
    NONE("Статичный"),
    FADE_POP("Появление"),
    SLIDE_UP("Выезд снизу"),
    TYPEWRITER("Печатная машинка")
}

@Serializable
data class TextOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.78f,
    val fontSizeSp: Float = 26f,
    val colorHex: Long = 0xFFFFFFFF,
    val backgroundHex: Long = 0x990A0B0E,
    val fontStyle: TextFontStyle = TextFontStyle.DISPLAY,
    val animationStyle: TextAnimationStyle = TextAnimationStyle.FADE_POP
)

@Serializable
data class AudioTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val title: String,
    val startMs: Long = 0L,
    val sourceDurationMs: Long = 15000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 15000L,
    val volume: Float = 0.85f,
    val fadeInMs: Long = 400L,
    val fadeOutMs: Long = 800L,
    val muted: Boolean = false,
    val waveformSamples: List<Float> = emptyList()
) {
    val timelineDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(500L)
}

@Serializable
data class VideoProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Новый проект",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_9_16,
    val targetFps: Int = 60,
    val clips: List<VideoClip> = emptyList(),
    val textOverlays: List<TextOverlayItem> = emptyList(),
    val audioTracks: List<AudioTrackItem> = emptyList(),
    val thumbnailPath: String? = null,
    val lastExportPath: String? = null
) {
    val totalDurationMs: Long
        get() {
            val videoDur = clips.sumOf { it.timelineDurationMs }
            val textDur = textOverlays.maxOfOrNull { it.endMs } ?: 0L
            return maxOf(videoDur, textDur)
        }
}

enum class EditorToolTab(val title: String) {
    CUT("Монтаж"),
    COLOR("Цвет"),
    KEYFRAME("Ключи"),
    TEXT("Текст"),
    AUDIO("Аудио"),
    TRANSITION("Переходы")
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Exporting(val progressPercent: Int, val stageLabel: String) : ExportState
    data class Completed(val outputUriString: String, val fileSizeBytes: Long, val durationMs: Long) : ExportState
    data class Failed(val message: String) : ExportState
}
