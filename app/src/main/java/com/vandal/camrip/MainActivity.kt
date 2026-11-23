package com.vandal.camrip

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF7CFFB2),
                secondary = Color(0xFFFF4FD8),
                tertiary = Color(0xFF7AA2FF),
                background = Color(0xFF09090E),
                surface = Color(0xFF151520)
            )) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CamRipApp()
                }
            }
        }
    }
}

enum class GlitchEffect(val title: String) {
    RGB("RGB Shift"),
    VHS("VHS"),
    DATAMOSH("Datamosh"),
    PIXEL("Pixelate"),
    SCAN("Scanlines"),
    WAVE("Wave"),
    INVERT("Invert Pulse"),
    NOISE("Noise Storm"),
    SHAKE("Shake"),
    ZOOM_PULSE("Zoom Pulse"),
    CHROMA("Chroma"),
    BLUR_PULSE("Blur Pulse"),
    EXPOSURE("Exposure")
}

enum class Easing(val title: String) {
    LINEAR("Линейно"),
    EASE_IN("Плавно вх."),
    EASE_OUT("Плавно вых."),
    EASE_IN_OUT("Плавно"),
    STEP("Ступени"),
    WAVE("Волна")
}

data class Keyframe(val timeSec: Float, val intensity: Float, val easing: Easing = Easing.LINEAR) // intensity 0..1

enum class M3Shape(val title: String) {
    CIRCLE("Circle"), SQUARE("Square"), PILL("Pill"), ARCH("Arch"), DIAMOND("Diamond"),
    PENTAGON("Pentagon"), CLOVER4("Clover4"), CLOVER8("Clover8"), BURST("Burst"),
    SOFT_BURST("SoftBurst"), FLOWER("Flower"), COOKIE6("Cookie6"), COOKIE12("Cookie12"),
    BOOM("Boom"), SOFT_BOOM("SoftBoom"), PUFFY("Puffy"), BUN("Bun"), WAVY("Wavy"), BLOB("Blob")
}

data class TimelineItem(
    val id: Long = System.currentTimeMillis(),
    val type: String, // video, shape, text, audio, vector, media
    val label: String,
    val startSec: Float = 0f,
    val durationSec: Float = 3f,
    var x: Float = 0f,
    var y: Float = 0f,
    var scale: Float = 1f,
    var rotation: Float = 0f,
    var color: Color = Color(0xFF7CFFB2),
    var shape: M3Shape? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamRipApp() {
    val context = LocalContext.current
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var durationMs by remember { mutableStateOf(10_000L) }
    var trimRange by remember { mutableStateOf(0f..10f) }
    var effect by remember { mutableStateOf(GlitchEffect.RGB) }
    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("Выберите видео для начала") }
    var playhead by remember { mutableStateOf(0f) }

    var keyframes by remember { mutableStateOf(listOf(Keyframe(0f, 1f), Keyframe(10f, 1f))) }
    var selectedKf by remember { mutableStateOf(0) }
    var points by remember { mutableStateOf(listOf<Offset>()) }
    var selectedPoint by remember { mutableStateOf(-1) }
    var shapeClosed by remember { mutableStateOf(false) }
    var selectedShape by remember { mutableStateOf<M3Shape?>(null) }
    var objColor by remember { mutableStateOf(Color(0xFF7CFFB2)) }

    var subtitleText by remember { mutableStateOf("") }
    var subtitleIsSpeech by remember { mutableStateOf(true) }

    val tabs = listOf("Видео", "Медиа", "Аудио", "Фигуры", "Вектор")
    var selectedTab by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf(listOf<TimelineItem>()) }
    var selectedItemId by remember { mutableStateOf<Long?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            videoUri = uri
            val dur = queryDuration(context, uri)
            durationMs = max(dur, 1000L)
            val sec = durationMs / 1000f
            trimRange = 0f..sec
            keyframes = listOf(Keyframe(0f, 1f), Keyframe(sec, 1f))
            selectedKf = 0
            playhead = 0f
            status = "Видео загружено"
            if (items.none { it.type == "video" }) {
                items = listOf(TimelineItem(type = "video", label = "Видео", startSec = 0f, durationSec = sec)) + items
            }
        }
    }

    val selectedItem = items.firstOrNull { it.id == selectedItemId }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) })
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (videoUri != null) {
                VideoPreview(uri = videoUri!!)
            } else {
                Box(Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF101018), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Text("Превью видео", color = Color(0xFF8888AA))
                }
            }

            when (selectedTab) {
                0 -> {
                    Text("Видео дорожка / монтаж", fontWeight = FontWeight.SemiBold)
                    Text("Обрезка: ${trimRange.start.format1()}s — ${trimRange.endInclusive.format1()}s")
                    RangeSlider(value = trimRange, onValueChange = { trimRange = it }, valueRange = 0f..(durationMs / 1000f), enabled = !exporting)
                    Text("Эффект", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlitchEffect.entries.forEach { e ->
                            FilterChip(selected = effect == e, onClick = { effect = e }, label = { Text(e.title) }, enabled = !exporting)
                        }
                    }
                }
                1 -> {
                    Text("Медиа", fontWeight = FontWeight.SemiBold)
                    Text("Добавьте видео/картинки через + внизу. (Импорт медиа можно расширить)", color = Color(0xFFB0B0C0))
                    Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, enabled = !exporting) { Text("Выбрать видео") }
                }
                2 -> {
                    Text("Аудио", fontWeight = FontWeight.SemiBold)
                    Text("Аудио-дорожка: скоро. Пока используется звук исходного видео.", color = Color(0xFFB0B0C0))
                }
                3 -> {
                    Text("Фигуры", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        M3Shape.entries.forEach { s ->
                            FilterChip(selected = selectedShape == s, onClick = {
                                selectedShape = s
                                val newItem = TimelineItem(type = "shape", label = s.title, startSec = playhead, shape = s, color = objColor)
                                items = items + newItem
                                selectedItemId = newItem.id
                            }, label = { Text(s.title) })
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(160.dp).background(Color(0xFF0E0E14), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        val shape = selectedShape
                        if (shape != null) Canvas(Modifier.size(120.dp)) { drawM3Shape(shape, objColor, objColor.copy(alpha = 0.25f)) } else Text("Выберите фигуру", color = Color(0xFF8888AA))
                    }
                }
                4 -> {
                    Text("Векторный карандаш", fontWeight = FontWeight.SemiBold)
                    Text("Тап — добавить точку. Перетаскивание — двигать.", color = Color(0xFFB0B0C0))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { points = points + Offset(180f + points.size * 20f, 140f); selectedPoint = points.lastIndex }, enabled = !exporting) { Text("+ Точка") }
                        Button(onClick = { shapeClosed = !shapeClosed }, enabled = !exporting && points.size >= 3) { Text(if (shapeClosed) "Открыть" else "Замкнуть") }
                        OutlinedButton(onClick = { points = emptyList(); selectedPoint = -1; shapeClosed = false }, enabled = !exporting) { Text("Очистить") }
                    }
                    VectorCanvas(points = points, selected = selectedPoint, closed = shapeClosed, color = objColor,
                        onSelect = { selectedPoint = it },
                        onMove = { idx, pos -> points = points.toMutableList().also { it[idx] = pos } },
                        onAdd = { pos -> points = points + pos; selectedPoint = points.lastIndex })
                    Button(onClick = {
                        val newItem = TimelineItem(type = "vector", label = "Вектор", startSec = playhead, color = objColor)
                        items = items + newItem
                        selectedItemId = newItem.id
                    }, enabled = points.isNotEmpty()) { Text("Добавить на таймлайн") }
                }
            }

            if (selectedItem != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Свойства: ${selectedItem.label}", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = true, onClick = {}, label = { Text("Переместить") })
                            FilterChip(selected = false, onClick = { status = "Эффекты объекта: в разработке" }, label = { Text("Эффекты") })
                        }
                        Text("X: ${selectedItem.x.roundToInt()}  Y: ${selectedItem.y.roundToInt()}")
                        Slider(value = selectedItem.x, onValueChange = { v -> items = items.map { if (it.id == selectedItem.id) it.copy(x = v) else it } }, valueRange = -300f..300f)
                        Slider(value = selectedItem.y, onValueChange = { v -> items = items.map { if (it.id == selectedItem.id) it.copy(y = v) else it } }, valueRange = -300f..300f)
                        Text("Масштаб: ${String.format("%.2f", selectedItem.scale)}x")
                        Slider(value = selectedItem.scale, onValueChange = { v -> items = items.map { if (it.id == selectedItem.id) it.copy(scale = v) else it } }, valueRange = 0.2f..3f)
                        Text("Поворот: ${selectedItem.rotation.roundToInt()}°")
                        Slider(value = selectedItem.rotation, onValueChange = { v -> items = items.map { if (it.id == selectedItem.id) it.copy(rotation = v) else it } }, valueRange = -720f..720f)
                        Text("Цвет", color = Color(0xFFB0B0C0))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(Color(0xFF7CFFB2), Color(0xFFFF4FD8), Color(0xFF7AA2FF), Color(0xFFFFFFFF), Color(0xFFFFE066), Color(0xFFFF5A5A)).forEach { c ->
                                FilterChip(selected = selectedItem.color == c, onClick = {
                                    objColor = c
                                    items = items.map { if (it.id == selectedItem.id) it.copy(color = c) else it }
                                }, label = { Text("●") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.35f)))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { items = items.filterNot { it.id == selectedItem.id }; selectedItemId = null }) { Text("Удалить") }
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Keyframes / Кривые", fontWeight = FontWeight.SemiBold)
                        Text("Выбранный: #${selectedKf + 1} / ${keyframes.size}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { val t = playhead.coerceIn(trimRange.start, trimRange.endInclusive); keyframes = (keyframes + Keyframe(t, 1f, Easing.LINEAR)).sortedBy { it.timeSec }; selectedKf = keyframes.indexOfLast { it.timeSec <= t }.coerceAtLeast(0) }, enabled = !exporting) { Text("+ Keyframe") }
                            Button(onClick = { if (keyframes.size > 2) { keyframes = keyframes.toMutableList().also { it.removeAt(selectedKf.coerceIn(0, it.lastIndex)) }; selectedKf = selectedKf.coerceAtMost(keyframes.lastIndex) } }, enabled = !exporting && keyframes.size > 2) { Text("Удалить") }
                        }
                        if (keyframes.isNotEmpty()) {
                            val kf = keyframes[selectedKf.coerceIn(0, keyframes.lastIndex)]
                            Text("Время: ${kf.timeSec.format1()}s  Интенсивность: ${(kf.intensity * 100).roundToInt()}%")
                            Slider(value = kf.timeSec, onValueChange = { v -> keyframes = keyframes.toMutableList().also { it[selectedKf] = it[selectedKf].copy(timeSec = v) }.sortedBy { it.timeSec } }, valueRange = trimRange.start..trimRange.endInclusive, enabled = !exporting)
                            Slider(value = kf.intensity, onValueChange = { v -> keyframes = keyframes.toMutableList().also { it[selectedKf] = it[selectedKf].copy(intensity = v) } }, enabled = !exporting)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Easing.entries.forEach { e -> FilterChip(selected = kf.easing == e, onClick = { keyframes = keyframes.toMutableList().also { it[selectedKf] = it[selectedKf].copy(easing = e) } }, label = { Text(e.title) }, enabled = !exporting) }
                            }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Субтитры", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = subtitleText, onValueChange = { subtitleText = it }, label = { Text("Текст субтитра") }, modifier = Modifier.fillMaxWidth(), enabled = !exporting)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = subtitleIsSpeech, onClick = { subtitleIsSpeech = true }, label = { Text("Речь [- текст]") }, enabled = !exporting)
                        FilterChip(selected = !subtitleIsSpeech, onClick = { subtitleIsSpeech = false }, label = { Text("Обычный [текст]") }, enabled = !exporting)
                    }
                }
            }

            Button(onClick = {
                val uri = videoUri ?: return@Button
                exporting = true; progress = 0f; status = "Рендеринг..."
                val sub = subtitleText.trim().let { if (it.isBlank()) "" else if (subtitleIsSpeech) "[- $it]" else "[$it]" }
                exportVideo(context, uri, trimRange.start.toDouble(), trimRange.endInclusive.toDouble(), effect, keyframes, sub,
                    onProgress = { p -> progress = p },
                    onDone = { _, msg -> exporting = false; status = msg; Toast.makeText(context, msg, Toast.LENGTH_LONG).show() })
            }, enabled = !exporting && videoUri != null, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("Экспорт в MP4") }

            if (exporting) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("${(progress * 100).roundToInt()}%")
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Text(status, modifier = Modifier.padding(12.dp), color = Color(0xFFDADAE5))
            }
        }

        // Bottom timeline
        Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Таймлайн", fontWeight = FontWeight.SemiBold)
                    Text("${playhead.format1()}s / ${(durationMs/1000f).format1()}s", color = Color(0xFFB0B0C0))
                }
                Slider(value = playhead, onValueChange = { playhead = it }, valueRange = 0f..(durationMs / 1000f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { playhead = (playhead - 10f).coerceAtLeast(0f) }) { Text("-10с") }
                    OutlinedButton(onClick = { playhead = (playhead + 10f).coerceAtMost(durationMs / 1000f) }) { Text("+10с") }
                    Button(onClick = {
                        val it = TimelineItem(type = "shape", label = selectedShape?.title ?: "Фигура", startSec = playhead, shape = selectedShape ?: M3Shape.CIRCLE, color = objColor)
                        items = items + it; selectedItemId = it.id; selectedTab = 3
                    }) { Text("+") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { item ->
                        FilterChip(selected = selectedItemId == item.id, onClick = { selectedItemId = if (selectedItemId == item.id) null else item.id }, label = { Text("${item.label} @${item.startSec.format1()}s") })
                    }
                }
            }
        }
    }
}
@Composable
fun VectorCanvas(points: List<Offset>, selected: Int, closed: Boolean, color: Color, onSelect: (Int) -> Unit, onMove: (Int, Offset) -> Unit, onAdd: (Offset) -> Unit) {
    Box(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF0E0E14), RoundedCornerShape(12.dp))) {
        Canvas(Modifier.fillMaxSize()
            .pointerInput(points, selected, closed) {
                detectTapGestures { tap ->
                    val idx = points.indexOfFirst { (it - tap).getDistance() < 40f }
                    if (idx >= 0) onSelect(idx) else onAdd(tap)
                }
            }
            .pointerInput(points, selected) {
                detectDragGestures(onDragStart = { pos ->
                    val idx = points.indexOfFirst { (it - pos).getDistance() < 50f }
                    if (idx >= 0) onSelect(idx)
                }) { change, drag ->
                    val idx = selected
                    if (idx in points.indices) onMove(idx, points[idx] + drag)
                    change.consume()
                }
            }
        ) {
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                    if (closed && points.size >= 3) close()
                }
                drawPath(path, color, style = Stroke(width = 4f))
                if (closed && points.size >= 3) drawPath(path, color.copy(alpha = 0.15f))
            }
            points.forEachIndexed { i, p ->
                drawCircle(if (i == selected) Color(0xFFFF4FD8) else color, radius = 12f, center = p)
                drawCircle(Color.White, radius = 4f, center = p)
            }
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawM3Shape(shape: M3Shape, stroke: Color, fill: Color) {
    val c = Offset(size.width/2, size.height/2)
    val r = size.minDimension * 0.42f
    val path = Path()
    fun poly(n: Int, rot: Float = -90f, radius: Float = r) {
        for (i in 0 until n) {
            val a = Math.toRadians((rot + i * 360f / n).toDouble())
            val x = c.x + radius * cos(a).toFloat()
            val y = c.y + radius * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
    fun star(points: Int, inner: Float) {
        for (i in 0 until points * 2) {
            val rad = if (i % 2 == 0) r else r * inner
            val a = Math.toRadians((-90 + i * 180f / points).toDouble())
            val x = c.x + rad * cos(a).toFloat()
            val y = c.y + rad * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
    when (shape) {
        M3Shape.CIRCLE -> { drawCircle(fill, r, c); drawCircle(stroke, r, c, style = Stroke(6f)); return }
        M3Shape.SQUARE -> { path.moveTo(c.x-r,c.y-r); path.lineTo(c.x+r,c.y-r); path.lineTo(c.x+r,c.y+r); path.lineTo(c.x-r,c.y+r); path.close() }
        M3Shape.PILL -> { drawRoundRect(fill, topLeft = Offset(c.x-r, c.y-r*0.6f), size = Size(r*2, r*1.2f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)); drawRoundRect(stroke, topLeft = Offset(c.x-r, c.y-r*0.6f), size = Size(r*2, r*1.2f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), style = Stroke(6f)); return }
        M3Shape.ARCH -> { path.addArc(androidx.compose.ui.geometry.Rect(c.x-r,c.y-r,c.x+r,c.y+r), 180f, 180f); path.lineTo(c.x+r,c.y+r); path.lineTo(c.x-r,c.y+r); path.close() }
        M3Shape.DIAMOND -> poly(4, 0f)
        M3Shape.PENTAGON -> poly(5)
        M3Shape.CLOVER4 -> star(4, 0.45f)
        M3Shape.CLOVER8 -> star(8, 0.65f)
        M3Shape.BURST -> star(12, 0.55f)
        M3Shape.SOFT_BURST -> star(10, 0.72f)
        M3Shape.FLOWER -> star(8, 0.58f)
        M3Shape.COOKIE6 -> star(6, 0.78f)
        M3Shape.COOKIE12 -> star(12, 0.85f)
        M3Shape.BOOM -> star(8, 0.35f)
        M3Shape.SOFT_BOOM -> star(6, 0.5f)
        M3Shape.PUFFY -> star(16, 0.8f)
        M3Shape.BUN -> { path.addOval(androidx.compose.ui.geometry.Rect(c.x-r,c.y-r*0.75f,c.x+r,c.y+r*0.75f)) }
        M3Shape.WAVY -> {
            for (i in 0..360 step 6) {
                val a = Math.toRadians(i.toDouble())
                val rr = r * (0.82f + 0.18f * sin(i * 6 * Math.PI / 180.0).toFloat())
                val x = c.x + rr * cos(a).toFloat(); val y = c.y + rr * sin(a).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        M3Shape.BLOB -> {
            for (i in 0..360 step 8) {
                val a = Math.toRadians(i.toDouble())
                val rr = r * (0.78f + 0.12f * sin(i * 3 * Math.PI / 180.0).toFloat() + 0.1f * cos(i * 5 * Math.PI / 180.0).toFloat())
                val x = c.x + rr * cos(a).toFloat(); val y = c.y + rr * sin(a).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
    }
    drawPath(path, fill)
    drawPath(path, stroke, style = Stroke(6f))
}

@Composable
fun VideoPreview(uri: Uri) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(uri)); prepare(); playWhenReady = false }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true } },
        modifier = Modifier.fillMaxWidth().height(260.dp).background(Color.Black, RoundedCornerShape(12.dp))
    )
}

fun queryDuration(context: Context, uri: Uri): Long = try {
    context.contentResolver.query(uri, arrayOf(MediaStore.Video.Media.DURATION), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else 10_000L } ?: 10_000L
} catch (_: Exception) { 10_000L }

fun intensityExpr(kfs: List<Keyframe>, base: Double): String {
    if (kfs.isEmpty()) return base.toString()
    if (kfs.size == 1) return (base * kfs[0].intensity).toString()
    val sorted = kfs.sortedBy { it.timeSec }
    var expr = "${base * sorted.last().intensity}"
    for (i in sorted.size - 2 downTo 0) {
        val a = sorted[i]; val b = sorted[i + 1]
        val dt = (b.timeSec - a.timeSec).coerceAtLeast(0.001f)
        val p = "((t-${a.timeSec})/$dt)"
        val eased = when (a.easing) {
            Easing.LINEAR -> p
            Easing.EASE_IN -> "($p*$p)"
            Easing.EASE_OUT -> "(1-(1-$p)*(1-$p))"
            Easing.EASE_IN_OUT -> "($p*$p*(3-2*$p))"
            Easing.STEP -> "0"
            Easing.WAVE -> "(0.5-0.5*cos(PI*$p))"
        }
        val v = "$base*(${a.intensity}+(${b.intensity}-${a.intensity})*$eased)"
        expr = "if(between(t,${a.timeSec},${b.timeSec}),$v,$expr)"
    }
    return "if(lt(t,${sorted.first().timeSec}),${base * sorted.first().intensity},$expr)"
}

fun exportVideo(context: Context, uri: Uri, startSec: Double, endSec: Double, effect: GlitchEffect, kfs: List<Keyframe>, subtitle: String, onProgress: (Float) -> Unit, onDone: (Boolean, String) -> Unit) {
    val input = copyUriToCache(context, uri) ?: run { onDone(false, "Не удалось прочитать видео"); return }
    val outFile = File(context.cacheDir, "camrip_${System.currentTimeMillis()}.mp4")
    val dur = max(0.5, endSec - startSec)
    val amp = intensityExpr(kfs.map { it.copy(timeSec = (it.timeSec - startSec.toFloat()).coerceAtLeast(0f)) }, 1.0)
    val filter = when (effect) {
        GlitchEffect.RGB -> "split=3[r][g][b];[r]lutrgb=g=0:b=0[rr];[g]lutrgb=r=0:b=0[gg];[b]lutrgb=r=0:g=0[bb];[rr][gg]blend=all_mode='addition'[rg];[rg][bb]blend=all_mode='addition',rgbashift=rh='8*${amp}':gh='-6*${amp}':bh='4*${amp}'"
        GlitchEffect.VHS -> "noise=alls='20*${amp}':allf=t+u,drawgrid=w=iw:h=2:t=1:c=white@0.08,eq=contrast='1+0.2*${amp}':saturation='1+0.4*${amp}'"
        GlitchEffect.DATAMOSH -> "lagfun=decay='0.9+0.08*${amp}',tblend=all_mode='difference128',eq=contrast='1+0.3*${amp}':saturation='1+0.5*${amp}'"
        GlitchEffect.PIXEL -> "scale=iw/'(8+24*(1-${amp}))':ih/'(8+24*(1-${amp}))':flags=neighbor,scale=iw*'(8+24*(1-${amp}))':ih*'(8+24*(1-${amp}))':flags=neighbor"
        GlitchEffect.SCAN -> "drawgrid=w=iw:h='2+2*${amp}':t=1:c=black@0.25,noise=alls='10*${amp}':allf=t"
        GlitchEffect.WAVE -> "geq=lum='lum(X+10*sin(2*PI*Y/80+T*3)*${amp},Y)':cb='cb(X,Y)':cr='cr(X,Y)'"
        GlitchEffect.INVERT -> "lutrgb=r='if(mod(floor(T*4*${amp}),2),255-val,val)':g='if(mod(floor(T*4*${amp}),2),255-val,val)':b='if(mod(floor(T*4*${amp}),2),255-val,val)'"
        GlitchEffect.NOISE -> "noise=alls='60*${amp}':allf=t+u,eq=contrast='1+0.4*${amp}'"
        GlitchEffect.SHAKE -> "crop=iw-40:ih-40:'20+10*sin(t*30)*${amp}':'20+10*cos(t*25)*${amp}'"
        GlitchEffect.ZOOM_PULSE -> "scale=iw*(1+0.08*sin(t*6)*${amp}):ih*(1+0.08*sin(t*6)*${amp}),crop=iw:ih"
        GlitchEffect.CHROMA -> "rgbashift=rh='12*${amp}':gh='-8*${amp}':bh='6*${amp}',eq=saturation='1+0.3*${amp}'"
        GlitchEffect.BLUR_PULSE -> "boxblur=luma_radius='min(20,2+10*abs(sin(t*3))*${amp})':luma_power=1"
        GlitchEffect.EXPOSURE -> "eq=brightness='0.08*sin(t*4)*${amp}':contrast='1+0.15*${amp}'"
    }
    val vf = if (subtitle.isBlank()) filter else {
        val escaped = subtitle
            .replace("\\", "\\\\")
            .replace(":", "\\:")
            .replace("'", "\\'")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace(",", "\\,")
            .replace(";", "\\;")
        "$filter,drawtext=text='$escaped':fontfile=/system/fonts/Roboto-Regular.ttf:fontsize=36:fontcolor=white:borderw=2:bordercolor=black:x=(w-text_w)/2:y=h-th-40"
    }
    val cmd = "-y -ss $startSec -t $dur -i ${quote(input.absolutePath)} -vf ${quote(vf)} -map 0:v:0 -map 0:a? -c:v mpeg4 -q:v 3 -c:a aac -b:a 128k ${quote(outFile.absolutePath)}"

    FFmpegKitConfig.enableStatisticsCallback { stat -> onProgress((stat.time / (dur * 1000.0)).toFloat().coerceIn(0f, 1f)) }
    FFmpegKit.executeAsync(cmd) { session ->
        FFmpegKitConfig.enableStatisticsCallback(null)
        val rc = session.returnCode
        if (ReturnCode.isSuccess(rc) && outFile.exists()) {
            val ok = saveToMovies(context, outFile)
            outFile.delete(); input.delete()
            onDone(ok, if (ok) "Сохранено в галерею (Movies/CamRIP)" else "Рендер готов, но не удалось сохранить")
        } else { input.delete(); onDone(false, "Ошибка рендера: ${session.failStackTrace ?: rc}") }
    }
}

fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
fun Float.format1(): String = String.format("%.1f", this)

fun copyUriToCache(context: Context, uri: Uri): File? {
    return try {
        val f = File(context.cacheDir, "input_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(f).use { out -> input.copyTo(out) } } ?: return null
        f
    } catch (_: Exception) { null }
}

fun saveToMovies(context: Context, file: File): Boolean {
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "CamRIP_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CamRIP")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0); resolver.update(uri, values, null, null) }
        true
    } catch (_: Exception) { false }
}
