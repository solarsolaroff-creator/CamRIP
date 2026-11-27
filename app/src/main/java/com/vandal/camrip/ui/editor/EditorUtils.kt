package com.vandal.camrip.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vandal.camrip.data.Clip
import com.vandal.camrip.data.Keyframe
import com.vandal.camrip.data.Project
import com.vandal.camrip.data.ShapeItem
import com.vandal.camrip.data.ShapeKeyframe

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

fun Clip.atTime(ms: Long): Clip {
    if (keyframes.isEmpty()) return this
    val k = keyframes.sortedBy { it.timeMs }
    if (ms <= k.first().timeMs) return copy(offsetX = k.first().offsetX, offsetY = k.first().offsetY, scale = k.first().scale, rotation = k.first().rotation, volume = k.first().volume, speed = k.first().speed)
    if (ms >= k.last().timeMs) return copy(offsetX = k.last().offsetX, offsetY = k.last().offsetY, scale = k.last().scale, rotation = k.last().rotation, volume = k.last().volume, speed = k.last().speed)
    val hi = k.indexOfFirst { it.timeMs >= ms }.coerceAtLeast(1)
    val lo = hi - 1
    val a = k[lo]; val b = k[hi]
    val t = ((ms - a.timeMs).toFloat() / (b.timeMs - a.timeMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    return copy(offsetX = lerp(a.offsetX,b.offsetX,t), offsetY = lerp(a.offsetY,b.offsetY,t), scale = lerp(a.scale,b.scale,t), rotation = lerp(a.rotation,b.rotation,t), volume = lerp(a.volume,b.volume,t), speed = lerp(a.speed,b.speed,t))
}

fun ShapeItem.atTime(ms: Long): ShapeItem {
    if (keyframes.isEmpty()) return this
    val k = keyframes.sortedBy { it.timeMs }
    if (ms <= k.first().timeMs) return copy(x=k.first().x,y=k.first().y,w=k.first().w,h=k.first().h)
    if (ms >= k.last().timeMs) return copy(x=k.last().x,y=k.last().y,w=k.last().w,h=k.last().h)
    val hi = k.indexOfFirst { it.timeMs >= ms }.coerceAtLeast(1)
    val lo = hi - 1
    val a = k[lo]; val b = k[hi]
    val t = ((ms - a.timeMs).toFloat() / (b.timeMs - a.timeMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    return copy(x=lerp(a.x,b.x,t), y=lerp(a.y,b.y,t), w=lerp(a.w,b.w,t), h=lerp(a.h,b.h,t))
}

fun itEnd(clip: Clip, durationMs: Long): Long = if (clip.endMs > 0) clip.endMs else durationMs.coerceAtLeast(clip.startMs + 1000L)

fun snapMs(ms: Long, playheadMs: Long): Long {
    val grid = ((ms + 125) / 250) * 250
    return if (kotlin.math.abs(grid - playheadMs) < 200) playheadMs else grid.coerceAtLeast(0L)
}

fun moveTimelineItem(project: Project, type: String, id: Long, deltaMs: Long, playheadMs: Long = 0L): Project {
    return when (type) {
        "clip" -> {
            if (kotlin.math.abs(deltaMs) < 350) return project
            val list = project.clips.toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) return project
            val target = if (deltaMs < 0) idx - 1 else idx + 1
            if (target !in list.indices) return project
            val item = list.removeAt(idx)
            list.add(target, item)
            project.copy(clips = list)
        }
        "effect" -> project.copy(effects = project.effects.map { if (it.id == id) it.copy(startMs = snapMs(it.startMs + deltaMs, playheadMs)) else it })
        "model3d" -> project.copy(models3d = project.models3d.map { if (it.id == id) it.copy(startMs = snapMs(it.startMs + deltaMs, playheadMs)) else it })
        "shape" -> project.copy(shapes = project.shapes.map { if (it.id == id) it.copy(startMs = snapMs(it.startMs + deltaMs, playheadMs)) else it })
        else -> project
    }
}

fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60; val s = total % 60
    return "%02d:%02d".format(m, s)
}

@Composable
fun rememberAgslPreviewModifier(effectTypes: List<String>, intensity: Float): Modifier {
    // AGSL RenderEffect preview is API 33+; Compose BOM 2024.06.00 (UI 1.6.x) lacks asComposeRenderEffect().
    // Keep as safe no-op here; raw AGSL shaders are in res/raw for future integration.
    return Modifier
}
