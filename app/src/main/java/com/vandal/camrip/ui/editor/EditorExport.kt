package com.vandal.camrip.ui.editor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.vandal.camrip.data.Clip
import com.vandal.camrip.data.EffectItem
import com.vandal.camrip.data.ShapeItem
import java.io.File
import java.io.FileOutputStream

fun androidRegularPolygon(left: Float, top: Float, w: Float, h: Float, sides: Int): android.graphics.Path {
    val cx = left + w/2f; val cy = top + h/2f; val rx = w/2f; val ry = h/2f
    return android.graphics.Path().apply {
        for (i in 0 until sides) {
            val a = -Math.PI/2 + i*2*Math.PI/sides
            val x = cx + (rx*kotlin.math.cos(a)).toFloat(); val y = cy + (ry*kotlin.math.sin(a)).toFloat()
            if (i==0) moveTo(x,y) else lineTo(x,y)
        }
        close()
    }
}
fun androidStar(left: Float, top: Float, w: Float, h: Float, points: Int): android.graphics.Path {
    val cx = left + w/2f; val cy = top + h/2f; val rx = w/2f; val ry = h/2f
    return android.graphics.Path().apply {
        for (i in 0 until points*2) {
            val rrx = if (i%2==0) rx else rx*0.45f; val rry = if (i%2==0) ry else ry*0.45f
            val a = -Math.PI/2 + i*Math.PI/points
            val x = cx + (rrx*kotlin.math.cos(a)).toFloat(); val y = cy + (rry*kotlin.math.sin(a)).toFloat()
            if (i==0) moveTo(x,y) else lineTo(x,y)
        }
        close()
    }
}

@UnstableApi
class GainAudioProcessor(private val gain: Float) : androidx.media3.common.audio.BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat): androidx.media3.common.audio.AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT) return androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET
        return inputAudioFormat
    }
    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        while (inputBuffer.remaining() >= 2) {
            val lo = inputBuffer.get().toInt() and 0xFF
            val hi = inputBuffer.get().toInt()
            var sample = (hi shl 8) or lo
            if (sample >= 32768) sample -= 65536
            val boosted = (sample * gain).toInt().coerceIn(-32768, 32767)
            out.put((boosted and 0xFF).toByte())
            out.put(((boosted shr 8) and 0xFF).toByte())
        }
        out.flip()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
fun exportClip(context: Context, clip: Clip?, effects: List<EffectItem>, shapes: List<ShapeItem>, aspectRatio: String, exportQuality: String, onDone: (Boolean, String) -> Unit): Transformer? {
    val outFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
    val ratio = when (aspectRatio) { "16:9" -> 16f/9f; "9:16" -> 9f/16f; "1:1" -> 1f; "4:3" -> 4f/3f; "4:5" -> 4f/5f; else -> 16f/9f }
    val baseEffects = mutableListOf<Effect>()
    if (clip != null && (clip.scale != 1f || clip.rotation != 0f)) {
        baseEffects += ScaleAndRotateTransformation.Builder().setScale(clip.scale.coerceAtLeast(0.1f), clip.scale.coerceAtLeast(0.1f)).setRotationDegrees(clip.rotation).build()
    }
    val videoEffects = mutableListOf<Effect>()
    videoEffects += baseEffects
    videoEffects += effects.flatMap { it.toMedia3Effects() }
    videoEffects += Presentation.createForAspectRatio(ratio, Presentation.LAYOUT_SCALE_TO_FIT)
    if (shapes.isNotEmpty()) {
        try {
            val outW = 1080
            val outH = (1080f / ratio).toInt().coerceAtLeast(1)
            val overlay = object : androidx.media3.effect.BitmapOverlay() {
                override fun getBitmap(presentationTimeUs: Long): android.graphics.Bitmap {
                    val bmp = android.graphics.Bitmap.createBitmap(outW, outH, android.graphics.Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(bmp)
                    val p = android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; isAntiAlias = true }
                    val tMs = presentationTimeUs / 1000L
                    shapes.forEach { s0 ->
                        val s = s0.atTime(tMs)
                        if (s.endMs > 0 && (tMs < s.startMs || tMs > s.endMs)) return@forEach
                        p.color = s.color.toInt(); p.strokeWidth = s.stroke * outW / 1080f
                        p.style = if (s.filled && s.type != "line") android.graphics.Paint.Style.FILL else android.graphics.Paint.Style.STROKE
                        val l = s.x * outW.toFloat(); val top = s.y * outH.toFloat(); val r = (s.x + s.w) * outW.toFloat(); val b = (s.y + s.h) * outH.toFloat()
                        val bw = r-l; val bh = b-top; val cx = l + bw/2f; val cy = top + bh/2f
                        when (s.type) {
                            "circle", "oval" -> c.drawOval(l, top, r, b, p)
                            "triangle" -> { val path = android.graphics.Path().apply { moveTo(cx, top); lineTo(r, b); lineTo(l, b); close() }; c.drawPath(path, p) }
                            "diamond" -> { val path = android.graphics.Path().apply { moveTo(cx, top); lineTo(r, cy); lineTo(cx, b); lineTo(l, cy); close() }; c.drawPath(path, p) }
                            "pentagon" -> c.drawPath(androidRegularPolygon(l,top,bw,bh,5), p)
                            "hexagon" -> c.drawPath(androidRegularPolygon(l,top,bw,bh,6), p)
                            "star" -> c.drawPath(androidStar(l,top,bw,bh,5), p)
                            "arrow" -> { val path = android.graphics.Path().apply { moveTo(l, top+bh*0.35f); lineTo(l+bw*0.65f, top+bh*0.35f); lineTo(l+bw*0.65f, top); lineTo(r, cy); lineTo(l+bw*0.65f, b); lineTo(l+bw*0.65f, top+bh*0.65f); lineTo(l, top+bh*0.65f); close() }; c.drawPath(path,p) }
                            "pill" -> c.drawRoundRect(l, top, r, b, bh/2f, bh/2f, p)
                            "line" -> c.drawLine(l, cy, r, cy, p)
                            else -> c.drawRoundRect(l, top, r, b, 16f, 16f, p)
                        }
                    }
                    return bmp
                }
            }
            videoEffects += androidx.media3.effect.OverlayEffect(com.google.common.collect.ImmutableList.of<androidx.media3.effect.TextureOverlay>(overlay))
        } catch (_: Throwable) {}
    }
    val edited = if (clip != null) {
        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(clip.uri))
        if (clip.startMs > 0 || clip.endMs > 0) {
            val clipBuilder = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs.coerceAtLeast(0L))
            if (clip.endMs > 0) clipBuilder.setEndPositionMs(clip.endMs)
            mediaItemBuilder.setClippingConfiguration(clipBuilder.build())
        }
        val audioProcessors = if (clip.volume != 1f) listOf(GainAudioProcessor(clip.volume.coerceIn(0f, 100f))) else emptyList()
        EditedMediaItem.Builder(mediaItemBuilder.build()).setEffects(Effects(audioProcessors, videoEffects)).build()
    } else {
        val outW = 1080
        val outH = (1080f / ratio).toInt().coerceAtLeast(1)
        val blackFile = File(context.cacheDir, "black_${System.currentTimeMillis()}.png")
        android.graphics.Bitmap.createBitmap(outW, outH, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLACK)
            FileOutputStream(blackFile).use { compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val imageItem = MediaItem.Builder().setUri(Uri.fromFile(blackFile)).build()
        EditedMediaItem.Builder(imageItem).setDurationUs(3_000_000L).setFrameRate(30).setEffects(Effects(emptyList(), videoEffects)).build()
    }
    val bitrate = when (exportQuality) {
        "very_low" -> 400_000
        "low" -> 1_000_000
        "medium" -> 2_500_000
        "high" -> 5_000_000
        "very_high" -> 10_000_000
        "ultra" -> 20_000_000
        else -> 5_000_000
    }
    val encoderFactory = DefaultEncoderFactory.Builder(context)
        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
        .build()
    val transformer = Transformer.Builder(context)
        .setEncoderFactory(encoderFactory)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                val ok = saveToGallery(context, outFile)
                outFile.delete()
                onDone(ok, if (ok) "Сохранено в Movies/CamRIP" else "Не удалось сохранить в галерею")
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                outFile.delete()
                if (exportException.errorCode == ExportException.ERROR_CODE_CANCELLED) onDone(false, "Экспорт отменён")
                else onDone(false, "Ошибка экспорта: ${exportException.errorCodeName}: ${exportException.message ?: ""}")
            }
        }).build()
    transformer.start(edited, outFile.absolutePath)
    return transformer
}

fun saveToGallery(context: Context, file: File): Boolean {
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "CamRIP_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CamRIP"); put(MediaStore.Video.Media.IS_PENDING, 1) }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0); resolver.update(uri, values, null, null) }
        true
    } catch (_: Exception) { false }
}

fun importModelToFilesDir(context: Context, uri: Uri): File? {
    return try {
        val nameRaw = (uri.lastPathSegment ?: "model_${System.currentTimeMillis()}.glb").substringAfterLast('/')
        val safe = nameRaw.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val name = when {
            safe.endsWith(".glb", true) || safe.endsWith(".gltf", true) -> safe
            else -> "$safe.glb"
        }
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val out = File(dir, "${System.currentTimeMillis()}_$name")
        context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(out).use { input.copyTo(it) } } ?: return null
        out
    } catch (_: Exception) { null }
}
