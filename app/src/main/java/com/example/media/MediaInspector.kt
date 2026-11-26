package com.example.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.AudioTrackItem
import com.example.domain.model.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MediaInspector(private val context: Context) {

    private val frameCache = object : LruCache<String, Bitmap>(80) {}

    suspend fun inspectVisualUri(uri: Uri): VideoClip? = withContext(Dispatchers.IO) {
        tryTakePersistablePermission(uri)
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val displayName = resolveDisplayName(uri) ?: "Медиаклип"

        if (mimeType.startsWith("image/")) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val width = if (bounds.outWidth > 0) bounds.outWidth else 1080
            val height = if (bounds.outHeight > 0) bounds.outHeight else 1920
            val thumbPath = saveImageThumbnail(uri)
            return@withContext VideoClip(
                uriString = uri.toString(),
                isImage = true,
                title = displayName.substringBeforeLast('.'),
                sourceDurationMs = 5000L,
                trimStartMs = 0L,
                trimEndMs = 5000L,
                width = width,
                height = height
            ).also {
                if (thumbPath != null) frameCache.put("${uri}_0", BitmapFactory.decodeFile(thumbPath))
            }
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            val durationMs = durationStr?.toLongOrNull()?.coerceAtLeast(500L) ?: 5000L
            val rawW = widthStr?.toIntOrNull() ?: 1920
            val rawH = heightStr?.toIntOrNull() ?: 1080
            val rot = rotStr?.toIntOrNull() ?: 0
            val (w, h) = if (rot == 90 || rot == 270) rawH to rawW else rawW to rawH

            VideoClip(
                uriString = uri.toString(),
                isImage = false,
                title = displayName.substringBeforeLast('.'),
                sourceDurationMs = durationMs,
                trimStartMs = 0L,
                trimEndMs = durationMs,
                width = w,
                height = h
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun inspectAudioUri(uri: Uri): AudioTrackItem? = withContext(Dispatchers.IO) {
        tryTakePersistablePermission(uri)
        val displayName = resolveDisplayName(uri) ?: "Аудиодорожка"
        val retriever = MediaMetadataRetriever()
        val durationMs = try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(1000L) ?: 15000L
        } catch (_: Exception) {
            15000L
        } finally {
            runCatching { retriever.release() }
        }
        val waveform = extractAudioWaveform(uri.toString(), 64)
        AudioTrackItem(
            uriString = uri.toString(),
            title = displayName.substringBeforeLast('.'),
            sourceDurationMs = durationMs,
            trimStartMs = 0L,
            trimEndMs = durationMs,
            waveformSamples = waveform
        )
    }

    suspend fun extractFilmstrip(
        uriString: String,
        isImage: Boolean,
        startMs: Long,
        endMs: Long,
        frameCount: Int
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val count = frameCount.coerceIn(1, 16)
        val uri = Uri.parse(uriString)
        if (isImage) {
            val cacheKey = "${uriString}_img"
            val cached = frameCache.get(cacheKey)
            if (cached != null) return@withContext List(count) { cached }
            val bmp = decodeSampledBitmap(uri, 160, 160) ?: return@withContext emptyList()
            frameCache.put(cacheKey, bmp)
            return@withContext List(count) { bmp }
        }

        val retriever = MediaMetadataRetriever()
        val results = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(context, uri)
            val span = (endMs - startMs).coerceAtLeast(100L)
            for (i in 0 until count) {
                val tMs = startMs + (span * (i + 0.5f) / count).toLong()
                val bucket = (tMs / 250L) * 250L
                val key = "${uriString}_$bucket"
                val existing = frameCache.get(key)
                if (existing != null) {
                    results.add(existing)
                } else {
                    val frame = retriever.getFrameAtTime(
                        tMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    if (frame != null) {
                        val scaled = scaleDownBitmap(frame, 140)
                        if (scaled !== frame) frame.recycle()
                        frameCache.put(key, scaled)
                        results.add(scaled)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }
        results
    }

    suspend fun extractAudioWaveform(uriString: String, bars: Int = 64): List<Float> =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, Uri.parse(uriString), null)
                var audioTrackIndex = -1
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        break
                    }
                }
                if (audioTrackIndex == -1) return@withContext emptyList()

                extractor.selectTrack(audioTrackIndex)
                val buffer = ByteBuffer.allocate(32 * 1024)
                val peaks = FloatArray(bars)
                val counts = IntArray(bars)
                val durationUs = extractor.getTrackFormat(audioTrackIndex)
                    .takeIf { it.containsKey(MediaFormat.KEY_DURATION) }
                    ?.getLong(MediaFormat.KEY_DURATION) ?: 10_000_000L

                var framesRead = 0
                while (framesRead < 600) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    val sampleTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                    val bucket = ((sampleTimeUs.toDouble() / max(1L, durationUs)) * bars)
                        .toInt().coerceIn(0, bars - 1)

                    var maxByte = 0f
                    val step = max(1, sampleSize / 32)
                    var idx = 0
                    while (idx < sampleSize) {
                        val v = abs(buffer.get(idx).toInt()) / 128f
                        if (v > maxByte) maxByte = v
                        idx += step
                    }
                    peaks[bucket] += maxByte
                    counts[bucket]++
                    framesRead++
                    if (!extractor.advance()) break
                }

                val raw = List(bars) { i ->
                    if (counts[i] > 0) (peaks[i] / counts[i]).coerceIn(0.08f, 1f) else 0.12f
                }
                val maxPeak = raw.maxOrNull()?.coerceAtLeast(0.2f) ?: 1f
                raw.map { (it / maxPeak).coerceIn(0.12f, 1f) }
            } catch (_: Exception) {
                emptyList()
            } finally {
                runCatching { extractor.release() }
            }
        }

    suspend fun saveProjectThumbnail(clip: VideoClip): String? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "thumbs").apply { mkdirs() }
        val outFile = File(dir, "thumb_${UUID.randomUUID()}.jpg")
        val uri = Uri.parse(clip.uriString)
        val bmp = if (clip.isImage) {
            decodeSampledBitmap(uri, 480, 480)
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(clip.trimStartMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { scaleDownBitmap(it, 480) }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        } ?: return@withContext null

        return@withContext try {
            FileOutputStream(outFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, fos)
            }
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    suspend fun createTitleCardAsset(
        title: String,
        subtitle: String,
        aspectRatio: AspectRatioPreset,
        accentStart: Int = 0xFF00E5FF.toInt(),
        accentEnd: Int = 0xFFFFB800.toInt()
    ): VideoClip = withContext(Dispatchers.IO) {
        val (w, h) = when (aspectRatio) {
            AspectRatioPreset.RATIO_9_16 -> 1080 to 1920
            AspectRatioPreset.RATIO_16_9 -> 1920 to 1080
            AspectRatioPreset.RATIO_1_1 -> 1080 to 1080
            AspectRatioPreset.RATIO_4_5 -> 1080 to 1350
            AspectRatioPreset.RATIO_239_1 -> 1920 to 804
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Studio dark gradient backdrop
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(0xFF0A0B0E.toInt(), 0xFF141B26.toInt(), 0xFF0E1016.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Subtle glowing accent ribbon
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, h * 0.35f, w.toFloat(), h * 0.65f,
                accentStart, accentEnd, Shader.TileMode.CLAMP
            )
            alpha = 55
        }
        canvas.drawCircle(w * 0.22f, h * 0.35f, min(w, h) * 0.38f, glowPaint)
        canvas.drawCircle(w * 0.78f, h * 0.65f, min(w, h) * 0.32f, glowPaint)

        // Precision studio grid lines
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x1FFFFFFF
            strokeWidth = 2f
        }
        val step = min(w, h) / 6f
        var x = step
        while (x < w) {
            canvas.drawLine(x, 0f, x, h.toFloat(), gridPaint)
            x += step
        }
        var y = step
        while (y < h) {
            canvas.drawLine(0f, y, w.toFloat(), y, gridPaint)
            y += step
        }

        // Accent rule line
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                w * 0.25f, 0f, w * 0.75f, 0f,
                accentStart, accentEnd, Shader.TileMode.CLAMP
            )
            strokeWidth = 6f
        }
        canvas.drawLine(w * 0.28f, h * 0.44f, w * 0.72f, h * 0.44f, rulePaint)

        // Main title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF4F6FA.toInt()
            textAlign = Paint.Align.CENTER
            textSize = min(w, h) * 0.085f
            isFakeBoldText = true
        }
        canvas.drawText(title, w / 2f, h * 0.52f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA0A8B8.toInt()
            textAlign = Paint.Align.CENTER
            textSize = min(w, h) * 0.036f
        }
        canvas.drawText(subtitle, w / 2f, h * 0.58f, subPaint)

        val genDir = File(context.filesDir, "generated").apply { mkdirs() }
        val file = File(genDir, "title_${UUID.randomUUID()}.png")
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
        }
        bitmap.recycle()

        VideoClip(
            uriString = Uri.fromFile(file).toString(),
            isImage = true,
            title = title,
            sourceDurationMs = 4000L,
            trimStartMs = 0L,
            trimEndMs = 4000L,
            width = w,
            height = h
        )
    }

    private fun saveImageThumbnail(uri: Uri): String? {
        val dir = File(context.filesDir, "thumbs").apply { mkdirs() }
        val outFile = File(dir, "thumb_${UUID.randomUUID()}.jpg")
        val bmp = decodeSampledBitmap(uri, 480, 480) ?: return null
        return try {
            FileOutputStream(outFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledBitmap(uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            var inSampleSize = 1
            if (options.outHeight > reqH || options.outWidth > reqW) {
                val halfH = options.outHeight / 2
                val halfW = options.outWidth / 2
                while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                    inSampleSize *= 2
                }
            }
            val decodeOpts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleDownBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val ratio = min(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(
            src,
            max(1, (w * ratio).toInt()),
            max(1, (h * ratio).toInt()),
            true
        )
    }

    private fun tryTakePersistablePermission(uri: Uri) {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }
}
