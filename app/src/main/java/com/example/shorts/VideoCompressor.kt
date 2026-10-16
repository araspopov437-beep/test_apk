package com.example.shorts

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Сжатие видео перед загрузкой: H.264, 720p (для вертикальных — 720×1280), ~2,5 Мбит/с, AAC.
 * Если сжать не вышло или файл не стал заметно меньше — возвращает null (грузим оригинал).
 */
object VideoCompressor {

    private const val VIDEO_BITRATE = 2_500_000

    suspend fun compress(ctx: Context, input: File): File? = withContext(Dispatchers.Main) {
        val out = File(ctx.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")
        try {
            val targetHeight = targetHeight(input)

            val ok = suspendCancellableCoroutine<Boolean> { cont ->
                val transformer = Transformer.Builder(ctx)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(ctx)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE).build()
                            )
                            .build()
                    )
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onError(
                            composition: Composition, exportResult: ExportResult, exportException: ExportException
                        ) {
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    .build()

                val videoEffects = if (targetHeight != null) listOf(Presentation.createForHeight(targetHeight)) else emptyList()
                val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()
                try {
                    transformer.start(item, out.absolutePath)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(false)
                }
                cont.invokeOnCancellation { transformer.cancel() }
            }

            if (ok && out.exists() && out.length() > 0 && out.length() < input.length() * 0.9) {
                out
            } else {
                out.delete()
                null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            out.delete()
            throw e
        } catch (e: Throwable) {
            out.delete()
            null
        }
    }

    /** Высота результата или null, если видео уже не больше нужного размера. */
    private fun targetHeight(file: File): Int? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return 720
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return 720
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            val target = if (h >= w) 1280 else 720
            if (h > target) target else null
        } catch (e: Exception) {
            null
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }
}
