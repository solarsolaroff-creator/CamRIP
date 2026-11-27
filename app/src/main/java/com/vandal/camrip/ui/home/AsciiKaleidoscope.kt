package com.vandal.camrip.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun AsciiKaleidoscopeBackground(modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) { delay(66); time += 0.066f }
    }
    val chars = " .'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$"
    val palette = listOf(0xFFFF0000.toInt(),0xFFFF8800.toInt(),0xFFFFFF00.toInt(),0xFF88FF00.toInt(),0xFF00FF00.toInt(),0xFF00FF88.toInt(),0xFF00FFFF.toInt(),0xFF0088FF.toInt(),0xFF8800FF.toInt(),0xFFFF00FF.toInt())

    Canvas(modifier.fillMaxSize()) {
        val cols = (size.width / 14f).toInt().coerceIn(20, 60)
        val rows = (size.height / 18f).toInt().coerceIn(10, 40)
        val cellW = size.width / cols
        val cellH = size.height / rows
        val paint = android.graphics.Paint().apply {
            textSize = cellH * 0.9f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
            alpha = 90
        }
        val t = time
        val angle = t * 0.35f
        val cosA = cos(angle); val sinA = sin(angle)
        val sym = 6
        val pi2 = (2.0 * PI).toFloat()
        val slice = pi2 / sym.toFloat()
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val nx = (x - cols / 2f) / (cols / 2f) * 0.8f
                val ny = (y - rows / 2f) / (rows / 2f) * 0.8f
                val rx = nx * cosA - ny * sinA
                val ry = nx * sinA + ny * cosA
                val r = sqrt(rx * rx + ry * ry)
                var a = atan2(ry, rx)
                a = ((a % pi2) + pi2) % pi2
                var sa = a % slice
                if (((a / slice).toInt() % 2) == 1) sa = slice - sa
                val px = cos(sa) * r
                val py = sin(sa) * r
                val v = pattern(px, py, t)
                val ci = (v * (chars.length - 1)).toInt().coerceIn(0, chars.length - 1)
                val pi = (v * (palette.size - 1)).toInt().coerceIn(0, palette.size - 1)
                paint.color = palette[pi]
                drawIntoCanvas { c -> c.nativeCanvas.drawText(chars[ci].toString(), x * cellW, y * cellH + cellH * 0.8f, paint) }
            }
        }
    }
}

private fun pattern(x: Float, y: Float, t: Float): Float {
    val r = sqrt(x*x + y*y)
    val a = atan2(y, x)
    val v = (sin(x * 3f + t) + sin(y * 3f + t * 1.3f) + sin((x + y) * 2.1f + t * 0.8f) + sin(r * 4f - t * 1.5f) + sin(a * 3f + r * 2f - t)) / 5f
    return ((v + 1f) / 2f).coerceIn(0f, 1f)
}
