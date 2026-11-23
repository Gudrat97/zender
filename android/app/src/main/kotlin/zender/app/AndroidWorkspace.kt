package zender.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
import android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
import android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
import android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
import android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
import android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import zender.core.Segment
import zender.core.UnsupportedFeature
import zender.core.VideoInfo
import zender.core.Workspace
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * Реализация Workspace для Android: видео через Media3 Transformer и MediaMetadataRetriever.
 * Не поддержаны пока: громкость (LUFS), распознавание речи. Для них бросается UnsupportedFeature.
 */
@OptIn(UnstableApi::class)
class AndroidWorkspace(private val context: Context) : Workspace {

    private val outputsDir = File(context.filesDir, "outputs").apply { mkdirs() }
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }

    // ---- Файлы ----

    private fun safeChild(base: File, relative: String): File {
        val root = base.canonicalFile
        val target = File(root, relative).canonicalFile
        if (target != root && !target.path.startsWith(root.path + File.separator)) {
            throw SecurityException("путь вне папки приложения запрещён")
        }
        return target
    }

    private fun outFile(name: String): File = safeChild(outputsDir, name).also { it.parentFile?.mkdirs() }

    override fun readText(path: String): String = File(path).readText()

    override fun saveText(name: String, content: String): String =
        outFile(name).also { it.writeText(content) }.absolutePath

    override fun listMedia(folder: String): List<String> {
        val root = safeChild(mediaDir, folder)
        return root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(mediaDir.canonicalFile).path }
            .sorted()
            .toList()
    }

    override fun fileHash(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        File(safeChild(mediaDir, path).path).inputStream().use { input ->
            val buf = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun moveFile(src: String, dst: String): String {
        val from = safeChild(mediaDir, src)
        val to = safeChild(mediaDir, dst)
        require(from.isFile) { "нет файла: $src" }
        require(!to.exists()) { "уже существует: $dst" }
        to.parentFile?.mkdirs()
        check(from.renameTo(to)) { "не удалось переместить $src" }
        return "перемещено: $src -> $dst"
    }

    // ---- Видео ----

    override suspend fun probe(path: String): VideoInfo = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(path)
            val durationS = (r.extractMetadata(METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
            var w = r.extractMetadata(METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = r.extractMetadata(METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val tmp = w; w = h; h = tmp   // поворот телефона: размеры меняются местами
            }
            val fps = r.extractMetadata(METADATA_KEY_CAPTURE_FRAMERATE)?.toDoubleOrNull() ?: 0.0
            VideoInfo(durationS, w, h, fps, hasAudioTrack(path))
        } finally {
            r.release()
        }
    }

    private fun hasAudioTrack(path: String): Boolean {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(path)
            (0 until ex.trackCount).any { ex.getTrackFormat(it).getString("mime")?.startsWith("audio/") == true }
        } finally {
            ex.release()
        }
    }

    override suspend fun cut(path: String, start: Double, end: Double, outName: String): String {
        require(end > start) { "end должен быть больше start" }
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs((start * 1000).toLong())
            .setEndPositionMs((end * 1000).toLong())
            .build()
        val item = MediaItem.Builder().setUri(Uri.fromFile(File(path))).setClippingConfiguration(clip).build()
        val dst = outFile(outName)
        export(dst) { it.start(EditedMediaItem.Builder(item).build(), dst.absolutePath) }
        return dst.absolutePath
    }

    override suspend fun concat(paths: List<String>, outName: String): String {
        require(paths.size >= 2) { "нужно минимум два видео" }
        val items = paths.map { EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(File(it)))).build() }
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(items).build()).build()
        val dst = outFile(outName)
        export(dst) { it.start(composition, dst.absolutePath) }
        return dst.absolutePath
    }

    override suspend fun toVertical(path: String, outName: String): String {
        val effects = Effects(
            emptyList(),
            listOf(Presentation.createForWidthAndHeight(1080, 1920, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)),
        )
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(File(path))))
            .setEffects(effects)
            .build()
        val dst = outFile(outName)
        export(dst) { it.start(item, dst.absolutePath) }
        return dst.absolutePath
    }

    /** Transformer создаётся и запускается на главном потоке и ждёт результата экспорта. */
    private suspend fun export(dst: File, start: (Transformer) -> Unit) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                cont.invokeOnCancellation { transformer.cancel() }
                start(transformer)
            }
        }
    }

    /**
     * Смены сцены: один кадр в секунду в уменьшенном виде, сравнение яркости соседних кадров.
     * threshold — доля отличий яркости от 0 до 1 (по умолчанию 0.4 в инструменте, для этого метода подходит 0.15–0.3).
     */
    override suspend fun sceneDetect(path: String, threshold: Double): List<Double> = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(path)
            val durationMs = r.extractMetadata(METADATA_KEY_DURATION)?.toLongOrNull() ?: return@withContext emptyList()
            val cuts = mutableListOf<Double>()
            var prev: IntArray? = null
            var t = 0L
            while (t < durationMs && cuts.size < 300) {
                val bmp = r.getScaledFrameAtTime(t * 1000, OPTION_CLOSEST_SYNC, 32, 32) ?: break
                val px = IntArray(32 * 32)
                bmp.getPixels(px, 0, 32, 0, 0, 32, 32)
                bmp.recycle()
                val luma = IntArray(px.size) { i ->
                    val c = px[i]
                    (((c shr 16) and 0xff) * 299 + ((c shr 8) and 0xff) * 587 + (c and 0xff) * 114) / 1000
                }
                prev?.let { p ->
                    val diff = luma.indices.sumOf { abs(luma[it] - p[it]) }.toDouble() / (luma.size * 255)
                    if (diff > threshold) cuts += t / 1000.0
                }
                prev = luma
                t += 1000
            }
            cuts
        } finally {
            r.release()
        }
    }

    override suspend fun extractFrame(path: String, timeS: Double, outName: String): String = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(path)
            val bmp: Bitmap = r.getFrameAtTime((timeS * 1_000_000).toLong(), OPTION_CLOSEST_SYNC)
                ?: error("кадр на ${timeS} с не получен")
            val dst = outFile(outName)
            val format = if (outName.lowercase().endsWith(".png")) CompressFormat.PNG else CompressFormat.JPEG
            dst.outputStream().use { bmp.compress(format, 92, it) }
            bmp.recycle()
            dst.absolutePath
        } finally {
            r.release()
        }
    }

    // ---- Не поддержано на Android пока ----

    override suspend fun measureLoudness(path: String): Double = throw UnsupportedFeature("измерение громкости (LUFS)")

    override suspend fun normalizeAudio(path: String, outName: String): String =
        throw UnsupportedFeature("нормализация громкости")

    override suspend fun transcribe(path: String, language: String): List<Segment> =
        throw UnsupportedFeature("распознавание речи (whisper.cpp)")
}
