package com.vandal.camrip.ui.editor

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.vandal.camrip.data.Model3DItem
import com.vandal.camrip.data.ShapeItem
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun regularPolygonPath(left: Float, top: Float, w: Float, h: Float, sides: Int): androidx.compose.ui.graphics.Path {
    val cx = left + w / 2f; val cy = top + h / 2f; val rx = w / 2f; val ry = h / 2f
    return androidx.compose.ui.graphics.Path().apply {
        for (i in 0 until sides) {
            val a = -Math.PI / 2 + i * 2 * Math.PI / sides
            val x = cx + (rx * kotlin.math.cos(a)).toFloat()
            val y = cy + (ry * kotlin.math.sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

fun starPath(left: Float, top: Float, w: Float, h: Float, points: Int): androidx.compose.ui.graphics.Path {
    val cx = left + w / 2f; val cy = top + h / 2f; val rx = w / 2f; val ry = h / 2f
    return androidx.compose.ui.graphics.Path().apply {
        for (i in 0 until points * 2) {
            val rrx = if (i % 2 == 0) rx else rx * 0.45f
            val rry = if (i % 2 == 0) ry else ry * 0.45f
            val a = -Math.PI / 2 + i * Math.PI / points
            val x = cx + (rrx * kotlin.math.cos(a)).toFloat()
            val y = cy + (rry * kotlin.math.sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

@Composable
fun ShapeOverlay(
    shapes: List<ShapeItem>,
    playheadMs: Long,
    onMove: (Long, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    onResize: (Long, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    onMoveEnd: () -> Unit = {}
) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var mode by remember { mutableStateOf("move") }
    androidx.compose.foundation.Canvas(
        Modifier.fillMaxSize().pointerInput(shapes, playheadMs) {
            detectDragGestures(
                onDragStart = { offset ->
                    val wPx = size.width.toFloat(); val hPx = size.height.toFloat()
                    val hit = shapes.lastOrNull { s0 ->
                        val s = s0.atTime(playheadMs)
                        !(s.endMs > 0 && (playheadMs < s.startMs || playheadMs > s.endMs)) &&
                        offset.x >= s.x * wPx && offset.x <= (s.x + s.w) * wPx && offset.y >= s.y * hPx && offset.y <= (s.y + s.h) * hPx
                    }
                    selectedId = hit?.id
                    mode = if (hit != null) {
                        val rx = (hit.x + hit.w) * wPx; val ry = (hit.y + hit.h) * hPx
                        if (kotlin.math.abs(offset.x - rx) < 48f && kotlin.math.abs(offset.y - ry) < 48f) "resize" else "move"
                    } else "move"
                },
                onDragEnd = { selectedId = null; onMoveEnd() },
                onDragCancel = { selectedId = null; onMoveEnd() },
                onDrag = { change, dragAmount ->
                    val id = selectedId ?: return@detectDragGestures
                    change.consume()
                    if (mode == "resize") onResize(id, dragAmount.x, dragAmount.y, size.width.toFloat(), size.height.toFloat())
                    else onMove(id, dragAmount.x, dragAmount.y, size.width.toFloat(), size.height.toFloat())
                }
            )
        }
    ) {
        shapes.forEach { s0 ->
            val s = s0.atTime(playheadMs)
            if (s.endMs > 0 && (playheadMs < s.startMs || playheadMs > s.endMs)) return@forEach
            val color = Color(s.color.toInt())
            val left = s.x * size.width
            val top = s.y * size.height
            val w = s.w * size.width
            val h = s.h * size.height
            val style = if (s.filled && s.type != "line") androidx.compose.ui.graphics.drawscope.Fill else androidx.compose.ui.graphics.drawscope.Stroke(width = s.stroke)
            when (s.type) {
                "circle", "oval" -> drawOval(color = color, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(w, h), style = style)
                "triangle" -> {
                    val path = androidx.compose.ui.graphics.Path().apply { moveTo(left + w / 2f, top); lineTo(left + w, top + h); lineTo(left, top + h); close() }
                    drawPath(path, color = color, style = style)
                }
                "diamond" -> {
                    val path = androidx.compose.ui.graphics.Path().apply { moveTo(left + w/2f, top); lineTo(left + w, top + h/2f); lineTo(left + w/2f, top + h); lineTo(left, top + h/2f); close() }
                    drawPath(path, color = color, style = style)
                }
                "pentagon" -> { val path = regularPolygonPath(left, top, w, h, 5); drawPath(path, color = color, style = style) }
                "hexagon" -> { val path = regularPolygonPath(left, top, w, h, 6); drawPath(path, color = color, style = style) }
                "star" -> { val path = starPath(left, top, w, h, 5); drawPath(path, color = color, style = style) }
                "arrow" -> {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(left, top + h*0.35f); lineTo(left + w*0.65f, top + h*0.35f); lineTo(left + w*0.65f, top)
                        lineTo(left + w, top + h*0.5f); lineTo(left + w*0.65f, top + h); lineTo(left + w*0.65f, top + h*0.65f)
                        lineTo(left, top + h*0.65f); close()
                    }
                    drawPath(path, color = color, style = style)
                }
                "pill" -> drawRoundRect(color = color, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h/2f, h/2f), style = style)
                "line" -> drawLine(color = color, start = Offset(left, top + h / 2f), end = Offset(left + w, top + h / 2f), strokeWidth = s.stroke)
                else -> drawRoundRect(color = color, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f,16f), style = style)
            }
            if (selectedId == s0.id) {
                drawRect(color = Color.White.copy(alpha = 0.8f), topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                drawCircle(color = Color.White, radius = 10f, center = Offset(left + w, top + h))
            }
        }
    }
}

@Composable
fun Model3DOverlay(models: List<Model3DItem>) {
    if (models.isEmpty()) return
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    var nodes by remember(models) { mutableStateOf(listOf<ModelNode>()) }

    LaunchedEffect(models.map { it.filePath + it.scale + it.rotX + it.rotY + it.rotZ }) {
        val loaded = withContext(Dispatchers.Main) {
            models.mapNotNull { m ->
                try {
                    val f = File(m.filePath)
                    if (!f.exists()) return@mapNotNull null
                    val instance = modelLoader.createModelInstance("file://${f.absolutePath}")
                        ?: modelLoader.createModelInstance(f.absolutePath)
                        ?: return@mapNotNull null
                    ModelNode(modelInstance = instance, scaleToUnits = m.scale.coerceAtLeast(0.1f)).apply {
                        position = Position(m.posX, m.posY, m.posZ)
                        rotation = Rotation(m.rotX, m.rotY, m.rotZ)
                    }
                } catch (_: Throwable) { null }
            }
        }
        nodes = loaded
    }

    Scene(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        childNodes = nodes,
        onViewCreated = { scene.skybox = null }
    )
}
