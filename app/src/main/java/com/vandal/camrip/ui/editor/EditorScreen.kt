package com.vandal.camrip.ui.editor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import com.vandal.camrip.data.Clip
import com.vandal.camrip.data.EffectItem
import com.vandal.camrip.data.Model3DItem
import com.vandal.camrip.data.Project
import com.vandal.camrip.data.ProjectRepository
import com.vandal.camrip.data.SettingsRepository
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    projectId: Long,
    repo: ProjectRepository,
    settings: SettingsRepository,
    appliedEffect: EffectItem?,
    onBack: () -> Unit,
    onOpenEffects: (Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf(repo.getProject(projectId) ?: Project(projectId, "proj_${System.currentTimeMillis().toString(36)}", System.currentTimeMillis(), System.currentTimeMillis())) }
    var selectedClipId by remember { mutableStateOf(project.clips.firstOrNull()?.id) }
    var showSheet by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var playheadMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var exporting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var selectedModel3dId by remember { mutableStateOf<Long?>(null) }
    var selectedItem by remember { mutableStateOf<Pair<String, Long>?>(null) } // type,id
    var groupPick by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var contextMenuItem by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var timelineZoom by remember { mutableStateOf(1f) }
    val latestProject by rememberUpdatedState(project)

    val selectedClip = project.clips.firstOrNull { it.id == selectedClipId } ?: project.clips.firstOrNull()
    val clipEffects = project.effects.filter { it.clipId == selectedClip?.id }
    val clipModels = project.models3d.filter { it.clipId == selectedClip?.id }
    val clipShapes = project.shapes.filter { it.clipId == selectedClip?.id }

    LaunchedEffect(appliedEffect?.id) {
        val e = appliedEffect ?: return@LaunchedEffect
        project = project.copy(effects = project.effects.filterNot { it.clipId == e.clipId && it.type == e.type } + e)
        repo.saveProject(project)
        status = "Эффект применён: ${e.type}"
    }

    val previewEffects = remember(clipEffects) { clipEffects.flatMap { it.toMedia3Effects() } }

    val player = remember(selectedClip?.uri, clipEffects.map { it.id to it.type to it.intensity to it.angleZ to it.scale }) {
        selectedClip?.let {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(it.uri)))
                setVideoEffects(previewEffects)
                prepare()
                playWhenReady = false
            }
        }
    }
    DisposableEffect(player) { onDispose { player?.release() } }

    LaunchedEffect(player, isPlaying) {
        player?.playWhenReady = isPlaying
        while (isPlaying) {
            playheadMs = player?.currentPosition ?: 0L
            val d = player?.duration ?: 0L
            if (d > 0) {
                durationMs = d
                val sc = selectedClip
                if (sc != null && sc.durationMs != d) {
                    project = project.copy(clips = project.clips.map { if (it.id == sc.id) it.copy(durationMs = d) else it })
                }
            }
            delay(100)
        }
    }

    fun saveNow() { repo.saveProject(project); project = repo.getProject(projectId) ?: project }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val clip = Clip(id = System.currentTimeMillis(), uri = uri.toString(), label = "Клип ${project.clips.size + 1}")
            project = project.copy(clips = project.clips + clip)
            selectedClipId = clip.id
            repo.saveProject(project)
            status = "Видео добавлено"
        }
    }

    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && selectedClip != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { importModelToFilesDir(context, uri) }
                if (saved != null) {
                    val item = Model3DItem(
                        id = System.currentTimeMillis(), clipId = selectedClip.id,
                        name = saved.name, filePath = saved.absolutePath,
                        posZ = -1.5f, scale = 1.8f
                    )
                    project = project.copy(models3d = project.models3d + item)
                    repo.saveProject(project)
                    status = "3D модель импортирована: ${saved.name}"
                } else status = "Не удалось импортировать 3D модель"
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Добавить", style = MaterialTheme.typography.titleLarge)
                Button(onClick = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)); showSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Добавить видео") }
                Button(onClick = { selectedClip?.let { onOpenEffects(it.id) }; showSheet = false }, enabled = selectedClip != null, modifier = Modifier.fillMaxWidth()) { Text("Эффекты") }
                Button(onClick = { pickModel.launch("*/*"); showSheet = false }, enabled = selectedClip != null, modifier = Modifier.fillMaxWidth()) { Text("Импорт 3D-модели (.glb/.gltf)") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val c = selectedClip ?: return@Button
                        project = project.copy(shapes = project.shapes + ShapeItem(System.currentTimeMillis(), c.id, "rect", 0.2f, 0.2f, 0.35f, 0.2f, 0xFFFF4FD8, 8f, playheadMs, 0L))
                        repo.saveProject(project); showSheet = false
                    }, enabled = selectedClip != null, modifier = Modifier.weight(1f)) { Text("Прямоуг.") }
                    Button(onClick = {
                        val c = selectedClip ?: return@Button
                        project = project.copy(shapes = project.shapes + ShapeItem(System.currentTimeMillis(), c.id, "circle", 0.35f, 0.3f, 0.25f, 0.25f, 0xFF7CFFB2, 8f, playheadMs, 0L))
                        repo.saveProject(project); showSheet = false
                    }, enabled = selectedClip != null, modifier = Modifier.weight(1f)) { Text("Круг") }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(project.name, maxLines = 1) },
                navigationIcon = { IconButton(onClick = { repo.saveProject(project); onBack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") } },
                actions = {
                    Button(onClick = {
                        val clip = selectedClip ?: run { status = "Нет клипа для экспорта"; return@Button }
                        exporting = true; status = "Экспорт..."
                        exportClip(context, clip, clipEffects, clipShapes) { ok, msg ->
                            exporting = false; status = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }, enabled = !exporting) { Text("Экспорт") }
                }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showSheet = true }, shape = CircleShape) { Icon(Icons.Default.Add, contentDescription = "Добавить") } }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
                if (player != null) {
                    val clipTransform = selectedClip
                    val agslMod = rememberAgslPreviewModifier(clipEffects.map { it.type }, clipEffects.firstOrNull()?.intensity ?: 0.5f)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(agslMod)
                            .graphicsLayer {
                                translationX = clipTransform?.offsetX ?: 0f
                                translationY = clipTransform?.offsetY ?: 0f
                                scaleX = clipTransform?.scale ?: 1f
                                scaleY = clipTransform?.scale ?: 1f
                                rotationZ = clipTransform?.rotation ?: 0f
                            }
                            .pointerInput(selectedClip?.id) {
                                detectDragGestures(
                                    onDragEnd = { repo.saveProject(latestProject) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val c = selectedClip ?: return@detectDragGestures
                                        project = project.copy(clips = project.clips.map {
                                            if (it.id == c.id) it.copy(offsetX = it.offsetX + dragAmount.x, offsetY = it.offsetY + dragAmount.y) else it
                                        })
                                    }
                                )
                            }
                    ) {
                        AndroidView(
                            factory = { PlayerView(it).apply { this.player = player; useController = false } },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Model3DOverlay(models = clipModels)
                    ShapeOverlay(shapes = clipShapes, playheadMs = playheadMs)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Добавьте видео через +", color = Color.White) }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { isPlaying = !isPlaying }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null) }
                Text(formatMs(playheadMs) + " / " + formatMs(durationMs))
            }
            if (status.isNotBlank()) Text(status, modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

            selectedClip?.let { c ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Видео", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = {
                        project = project.copy(clips = project.clips.map { if (it.id == c.id) it.copy(scale = (it.scale - 0.1f).coerceAtLeast(0.2f)) else it }); repo.saveProject(project)
                    }) { Text("-") }
                    Text("${"%.1f".format(c.scale)}x", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = {
                        project = project.copy(clips = project.clips.map { if (it.id == c.id) it.copy(scale = (it.scale + 0.1f).coerceAtMost(3f)) else it }); repo.saveProject(project)
                    }) { Text("+") }
                    TextButton(onClick = {
                        project = project.copy(clips = project.clips.map { if (it.id == c.id) it.copy(offsetX = 0f, offsetY = 0f, scale = 1f, rotation = 0f) else it }); repo.saveProject(project)
                    }) { Text("Сброс") }
                }
            }

            Column(
                Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 430.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("Видеодорожка (Video Track)", style = MaterialTheme.typography.labelLarge); Text("Playhead ${formatMs(playheadMs)} • Snap 250ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { timelineZoom = (timelineZoom - 0.25f).coerceAtLeast(0.5f) }) { Text("-") }
                        Text("${"%.1f".format(timelineZoom)}x", style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { timelineZoom = (timelineZoom + 0.25f).coerceAtMost(3f) }) { Text("+") }
                    }
                }
                val totalMs = durationMs.coerceAtLeast(10_000L)
                val pxPerMs = 0.12f * timelineZoom
                val timelineWidth = (totalMs * pxPerMs).dp
                val dens = androidx.compose.ui.platform.LocalDensity.current.density

                // ruler
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Column {
                        Row(Modifier.width(timelineWidth).height(24.dp)) {
                            val marks = (totalMs / 1000L).toInt().coerceAtLeast(1)
                            for (s in 0..marks) {
                                Box(Modifier.width((1000 * pxPerMs).dp).height(24.dp)) {
                                    Text("${s}s", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Box(Modifier.width(timelineWidth).height(250.dp).background(Color(0xFF101018), RoundedCornerShape(12.dp))) {
                            // grid
                            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                                val step = 1000f * pxPerMs * density
                                var x = 0f
                                while (x < size.width) { drawLine(Color(0xFF2A2A3A), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1f); x += step }
                                val ph = playheadMs * pxPerMs * density
                                drawLine(Color(0xFFFF4FD8), androidx.compose.ui.geometry.Offset(ph, 0f), androidx.compose.ui.geometry.Offset(ph, size.height), 4f)
                                drawCircle(Color(0xFFFF4FD8), radius=6f, center=androidx.compose.ui.geometry.Offset(ph, 8f))
                            }
                            Column(Modifier.matchParentSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // video track
                                Text("Видео дорожка", color = Color(0xFFB8D8FF), style = MaterialTheme.typography.labelSmall)
                                Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                                    project.clips.forEachIndexed { idx, clip ->
                                        val dur = ((if (clip.endMs > 0) clip.endMs else (clip.durationMs.takeIf { it > 0 } ?: 4000L)) - clip.startMs).coerceAtLeast(500L); val w = (dur * pxPerMs).dp
                                        val sel = selectedItem == ("clip" to clip.id) || selectedClipId == clip.id
                                        Box(
                                            Modifier.padding(end = 4.dp).width(w).fillMaxHeight().background(if (sel) MaterialTheme.colorScheme.primary else Color(0xFF3A6EA5), RoundedCornerShape(8.dp))
                                                .combinedClickable(onClick = { selectedClipId = clip.id; selectedItem = "clip" to clip.id }, onLongClick = { contextMenuItem = "clip" to clip.id })
                                                .pointerInput(clip.id) { detectDragGestures { c, drag -> c.consume(); project = moveTimelineItem(project, "clip", clip.id, (drag.x / (pxPerMs * dens)).toLong(), playheadMs) } },
                                            contentAlignment = Alignment.Center
                                        ) { Text(clip.label, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = Color.White) }
                                    }
                                }
                                // effects track
                                Text("Эффекты", color = Color(0xFFD8B8FF), style = MaterialTheme.typography.labelSmall)
                                Row(Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                                    clipEffects.forEach { e ->
                                        val left = (e.startMs.coerceAtLeast(0L) * pxPerMs).dp
                                        val sel = selectedItem == ("effect" to e.id)
                                        if (left.value > 0f) Spacer(Modifier.width(left))
                                        Box(
                                            Modifier.width(88.dp).fillMaxHeight().background(if (sel) MaterialTheme.colorScheme.secondary else Color(0xFF7A4FA5), RoundedCornerShape(8.dp))
                                                .combinedClickable(onClick = { selectedItem = "effect" to e.id }, onLongClick = { contextMenuItem = "effect" to e.id })
                                                .pointerInput(e.id) { detectDragGestures { c, drag -> c.consume(); project = moveTimelineItem(project, "effect", e.id, (drag.x / (pxPerMs * dens)).toLong(), playheadMs); repo.saveProject(project) } },
                                            contentAlignment = Alignment.Center
                                        ) { Text(e.type, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = Color.White) }
                                    }
                                }
                                // 3d track
                                Text("3D объекты", color = Color(0xFFB8FFE0), style = MaterialTheme.typography.labelSmall)
                                Row(Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                                    clipModels.forEach { m ->
                                        val left = (m.startMs.coerceAtLeast(0L) * pxPerMs).dp
                                        val sel = selectedItem == ("model3d" to m.id)
                                        if (left.value > 0f) Spacer(Modifier.width(left))
                                        Box(
                                            Modifier.width(88.dp).fillMaxHeight().background(if (sel) MaterialTheme.colorScheme.tertiary else Color(0xFF2F8A6B), RoundedCornerShape(8.dp))
                                                .combinedClickable(onClick = { selectedItem = "model3d" to m.id; selectedModel3dId = m.id }, onLongClick = { contextMenuItem = "model3d" to m.id })
                                                .pointerInput(m.id) { detectDragGestures { c, drag -> c.consume(); project = moveTimelineItem(project, "model3d", m.id, (drag.x / (pxPerMs * dens)).toLong(), playheadMs); repo.saveProject(project) } },
                                            contentAlignment = Alignment.Center
                                        ) { Text(m.name.take(8), maxLines = 1, style = MaterialTheme.typography.labelSmall, color = Color.White) }
                                    }
                                }
                                // shapes track
                                Text("Фигуры", color = Color(0xFFFFC8E8), style = MaterialTheme.typography.labelSmall)
                                Row(Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                                    clipShapes.forEach { s ->
                                        val left = (s.startMs.coerceAtLeast(0L) * pxPerMs).dp
                                        val sel = selectedItem == ("shape" to s.id)
                                        if (left.value > 0f) Spacer(Modifier.width(left))
                                        Box(
                                            Modifier.width(88.dp).fillMaxHeight().background(if (sel) MaterialTheme.colorScheme.primary else Color(0xFFB04F8A), RoundedCornerShape(8.dp))
                                                .combinedClickable(onClick = { selectedItem = "shape" to s.id }, onLongClick = { contextMenuItem = "shape" to s.id })
                                                .pointerInput(s.id) { detectDragGestures { c, drag -> c.consume(); project = moveTimelineItem(project, "shape", s.id, (drag.x / (pxPerMs * dens)).toLong(), playheadMs); repo.saveProject(project) } },
                                            contentAlignment = Alignment.Center
                                        ) { Text(s.type, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = Color.White) }
                                    }
                                }
                            }
                        }
                    }
                }

                selectedItem?.let { (type, id) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Переместить:", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { project = moveTimelineItem(project, type, id, -500, playheadMs); repo.saveProject(project) }) { Text("←") }
                        TextButton(onClick = { project = moveTimelineItem(project, type, id, 500, playheadMs); repo.saveProject(project) }) { Text("→") }
                    }
                }

                if (selectedItem?.first == "clip") {
                    val clip = project.clips.firstOrNull { it.id == selectedItem?.second }
                    if (clip != null) {
                        fun updClip(mut: (Clip) -> Clip) { project = project.copy(clips = project.clips.map { if (it.id == clip.id) mut(it) else it }); repo.saveProject(project) }
                        Text("Обрезка (Trim): ${formatMs(clip.startMs)} - ${if (clip.endMs > 0) formatMs(clip.endMs) else "конец"}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { updClip { it.copy(startMs = (it.startMs - 250).coerceAtLeast(0L)) } }) { Text("In-") }
                            TextButton(onClick = { updClip { it.copy(startMs = (it.startMs + 250).coerceAtLeast(0L)) } }) { Text("In+") }
                            TextButton(onClick = { val e = if (itEnd(clip, durationMs) - 250 > clip.startMs) itEnd(clip, durationMs) - 250 else itEnd(clip, durationMs); updClip { c -> c.copy(endMs = e) } }) { Text("Out-") }
                            TextButton(onClick = { updClip { c -> c.copy(endMs = (itEnd(c, durationMs) + 250).coerceAtLeast(c.startMs + 250)) } }) { Text("Out+") }
                        }
                    }
                }

                val selEffect = if (selectedItem?.first == "effect") project.effects.firstOrNull { it.id == selectedItem?.second } else null
                if (selEffect != null) {
                    fun updateEffect(mut: (EffectItem) -> EffectItem) { project = project.copy(effects = project.effects.map { if (it.id == selEffect.id) mut(it) else it }); repo.saveProject(project) }
                    HorizontalDivider()
                    Text("Эффект: ${selEffect.type}", style = MaterialTheme.typography.titleSmall)
                    Text("Интенсивность: ${"%.2f".format(selEffect.intensity)}")
                    Slider(value = selEffect.intensity, onValueChange = { v -> updateEffect { it.copy(intensity = v) } }, valueRange = 0f..1f)
                    Text("Угол Z: ${selEffect.angleZ.toInt()}°")
                    Slider(value = selEffect.angleZ, onValueChange = { v -> updateEffect { it.copy(angleZ = v) } }, valueRange = -180f..180f)
                    Text("Масштаб: ${"%.2f".format(selEffect.scale)}")
                    Slider(value = selEffect.scale, onValueChange = { v -> updateEffect { it.copy(scale = v) } }, valueRange = 0.2f..3f)
                }

                val selShape = if (selectedItem?.first == "shape") project.shapes.firstOrNull { it.id == selectedItem?.second } else null
                if (selShape != null) {
                    fun updateShape(mut: (ShapeItem) -> ShapeItem) { project = project.copy(shapes = project.shapes.map { if (it.id == selShape.id) mut(it) else it }); repo.saveProject(project) }
                    HorizontalDivider()
                    Text("Фигура: ${selShape.type}", style = MaterialTheme.typography.titleSmall)
                    Text("X: ${"%.2f".format(selShape.x)}"); Slider(value = selShape.x, onValueChange = { v -> updateShape { it.copy(x = v) } }, valueRange = 0f..1f)
                    Text("Y: ${"%.2f".format(selShape.y)}"); Slider(value = selShape.y, onValueChange = { v -> updateShape { it.copy(y = v) } }, valueRange = 0f..1f)
                    Text("Ширина: ${"%.2f".format(selShape.w)}"); Slider(value = selShape.w, onValueChange = { v -> updateShape { it.copy(w = v) } }, valueRange = 0.05f..1f)
                    Text("Высота: ${"%.2f".format(selShape.h)}"); Slider(value = selShape.h, onValueChange = { v -> updateShape { it.copy(h = v) } }, valueRange = 0.05f..1f)
                }

                if (clipModels.isNotEmpty()) {
                    val sel3d = clipModels.firstOrNull { it.id == selectedModel3dId } ?: clipModels.first()
                    fun updateModel(mut: (Model3DItem) -> Model3DItem) { project = project.copy(models3d = project.models3d.map { if (it.id == sel3d.id) mut(it) else it }); repo.saveProject(project) }
                    HorizontalDivider()
                    Text("3D: ${sel3d.name}", style = MaterialTheme.typography.titleSmall)
                    Text("Масштаб: ${"%.2f".format(sel3d.scale)}")
                    Slider(value = sel3d.scale, onValueChange = { v -> updateModel { it.copy(scale = v) } }, valueRange = 0.2f..5f)
                    Text("Поворот Y: ${sel3d.rotY.toInt()}°")
                    Slider(value = sel3d.rotY, onValueChange = { v -> updateModel { it.copy(rotY = v) } }, valueRange = -180f..180f)
                }
            }

            contextMenuItem?.let { (type, id) ->
                AlertDialog(
                    onDismissRequest = { contextMenuItem = null },
                    title = { Text("Объект: $type") },
                    text = { Text("Зажатие: можно удалить, выбрать для группы или сгруппировать с выбранным.") },
                    confirmButton = {
                        TextButton(onClick = {
                            if (groupPick != null && groupPick != (type to id)) {
                                val gid = "g_${System.currentTimeMillis()}"
                                project = project.copy(
                                    clips = project.clips.map { if ((type == "clip" && it.id == id) || (groupPick!!.first == "clip" && it.id == groupPick!!.second)) it.copy(groupId = gid) else it },
                                    effects = project.effects.map { if ((type == "effect" && it.id == id) || (groupPick!!.first == "effect" && it.id == groupPick!!.second)) it.copy(groupId = gid) else it },
                                    models3d = project.models3d.map { if ((type == "model3d" && it.id == id) || (groupPick!!.first == "model3d" && it.id == groupPick!!.second)) it.copy(groupId = gid) else it },
                                    shapes = project.shapes.map { if ((type == "shape" && it.id == id) || (groupPick!!.first == "shape" && it.id == groupPick!!.second)) it.copy(groupId = gid) else it }
                                )
                                repo.saveProject(project); groupPick = null; status = "Объекты сгруппированы"
                            } else {
                                groupPick = type to id; status = "Выбрано для группы"
                            }
                            contextMenuItem = null
                        }) { Text(if (groupPick != null && groupPick != (type to id)) "Сгруппировать" else "Выбрать для группы") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                val newProject = when (type) {
                                    "clip" -> project.copy(clips = project.clips.filterNot { it.id == id }, effects = project.effects.filterNot { it.clipId == id }, models3d = project.models3d.filterNot { it.clipId == id }, shapes = project.shapes.filterNot { it.clipId == id })
                                    "effect" -> project.copy(effects = project.effects.filterNot { it.id == id })
                                    "model3d" -> project.copy(models3d = project.models3d.filterNot { it.id == id })
                                    "shape" -> project.copy(shapes = project.shapes.filterNot { it.id == id })
                                    else -> project
                                }
                                project = newProject
                                if (type == "clip" && selectedClipId == id) selectedClipId = newProject.clips.firstOrNull()?.id
                                if (selectedItem == (type to id)) selectedItem = null
                                repo.saveProject(newProject); contextMenuItem = null; status = "Удалено"
                            }) { Text("Удалить") }
                            TextButton(onClick = { contextMenuItem = null }) { Text("Закрыть") }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ShapeOverlay(shapes: List<ShapeItem>, playheadMs: Long) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        shapes.forEach { s ->
            if (s.endMs > 0 && (playheadMs < s.startMs || playheadMs > s.endMs)) return@forEach
            val color = Color(s.color.toInt())
            val left = s.x * size.width
            val top = s.y * size.height
            val w = s.w * size.width
            val h = s.h * size.height
            when (s.type) {
                "circle" -> drawOval(color = color, topLeft = androidx.compose.ui.geometry.Offset(left, top), size = androidx.compose.ui.geometry.Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(width = s.stroke))
                else -> drawRect(color = color, topLeft = androidx.compose.ui.geometry.Offset(left, top), size = androidx.compose.ui.geometry.Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(width = s.stroke))
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

@androidx.annotation.OptIn(UnstableApi::class)
fun EffectItem.toMedia3Effects(): List<Effect> = when (type) {
    "grayscale" -> listOf(RgbFilter.createGrayscaleFilter())
    "brightness" -> listOf(Brightness(((intensity - 0.5f) * 1.2f).coerceIn(-1f, 1f)))
    "contrast" -> listOf(Contrast(((intensity - 0.5f) * 1.6f).coerceIn(-1f, 1f)))
    "fade" -> listOf(Brightness((-intensity * 1.0f).coerceIn(-1f, 1f)))
    "punch" -> listOf(Contrast((intensity * 1.0f).coerceIn(-1f, 1f)), Brightness((intensity * 0.15f).coerceIn(-1f, 1f)))
    "glitch" -> listOf(RgbFilter.createGrayscaleFilter(), Contrast((intensity * 0.9f).coerceIn(-1f, 1f)), Brightness((intensity * 0.1f).coerceIn(-1f, 1f)))
    "chromatic" -> listOf(Contrast((intensity * 0.5f).coerceIn(-1f, 1f)), Brightness((intensity * 0.08f).coerceIn(-1f, 1f)))
    "rotate3d", "perspective", "cube", "parallax", "flip", "tilt" ->
        listOf(ScaleAndRotateTransformation.Builder().setRotationDegrees(angleZ).build())
    "zoom", "zoom3d" -> listOf(ScaleAndRotateTransformation.Builder().setScale(scale.coerceAtLeast(0.1f), scale.coerceAtLeast(0.1f)).build())
    "shake" -> listOf(ScaleAndRotateTransformation.Builder().setRotationDegrees((intensity * 10f)).build())
    "wave", "fisheye", "pixelate" -> listOf(Contrast((intensity * 0.35f).coerceIn(-1f, 1f)), Brightness((intensity * 0.05f).coerceIn(-1f, 1f)))
    else -> emptyList()
}

@androidx.annotation.OptIn(UnstableApi::class)
fun exportClip(context: Context, clip: Clip, effects: List<EffectItem>, shapes: List<ShapeItem>, onDone: (Boolean, String) -> Unit) {
    val outFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
    val baseEffects = mutableListOf<Effect>()
    if (clip.scale != 1f || clip.rotation != 0f) {
        baseEffects += ScaleAndRotateTransformation.Builder().setScale(clip.scale.coerceAtLeast(0.1f), clip.scale.coerceAtLeast(0.1f)).setRotationDegrees(clip.rotation).build()
    }
    val videoEffects = mutableListOf<Effect>()
    videoEffects += baseEffects
    videoEffects += effects.flatMap { it.toMedia3Effects() }
    if (shapes.isNotEmpty()) {
        try {
            val overlay = object : androidx.media3.effect.BitmapOverlay() {
                override fun getBitmap(presentationTimeUs: Long): android.graphics.Bitmap {
                    val bmp = android.graphics.Bitmap.createBitmap(1080, 1920, android.graphics.Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(bmp)
                    val p = android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; isAntiAlias = true }
                    val tMs = presentationTimeUs / 1000L
                    shapes.forEach { s ->
                        if (s.endMs > 0 && (tMs < s.startMs || tMs > s.endMs)) return@forEach
                        p.color = s.color.toInt(); p.strokeWidth = s.stroke
                        val l = s.x * 1080f; val top = s.y * 1920f; val r = (s.x + s.w) * 1080f; val b = (s.y + s.h) * 1920f
                        if (s.type == "circle") c.drawOval(l, top, r, b, p) else c.drawRect(l, top, r, b, p)
                    }
                    return bmp
                }
            }
            videoEffects += androidx.media3.effect.OverlayEffect(listOf(overlay))
        } catch (_: Throwable) {}
    }
    val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(clip.uri))
    if (clip.startMs > 0 || clip.endMs > 0) {
        val clipBuilder = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs.coerceAtLeast(0L))
        if (clip.endMs > 0) clipBuilder.setEndPositionMs(clip.endMs)
        mediaItemBuilder.setClippingConfiguration(clipBuilder.build())
    }
    val edited = EditedMediaItem.Builder(mediaItemBuilder.build()).setEffects(Effects(emptyList(), videoEffects)).build()
    val transformer = Transformer.Builder(context).addListener(object : Transformer.Listener {
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            val ok = saveToGallery(context, outFile)
            outFile.delete()
            onDone(ok, if (ok) "Сохранено в Movies/CamRIP" else "Не удалось сохранить в галерею")
        }
        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
            outFile.delete()
            onDone(false, "Ошибка экспорта: ${exportException.errorCodeName}: ${exportException.message ?: ""}")
        }
    }).build()
    transformer.start(edited, outFile.absolutePath)
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
