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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.RgbFilter
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import io.github.sceneview.Scene
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF7CFFB2),
                secondary = Color(0xFF7AA2FF),
                background = Color(0xFF0B0B10),
                surface = Color(0xFF171722),
                onSurface = Color(0xFFEAEAF2)
            )) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    EditorApp()
                }
            }
        }
    }
}

enum class VideoFx(val title: String) {
    NONE("Нет"),
    GRAYSCALE("Ч/Б"),
    BRIGHTNESS("Яркость"),
    CONTRAST("Контраст"),
    GLITCH("Глитч"),
    BLUR("Размытие")
}

data class SavedModel(val name: String, val file: File)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var durationMs by remember { mutableStateOf(0L) }
    var playheadMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedFx by remember { mutableStateOf(VideoFx.NONE) }
    var showSheet by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    var savedModels by remember { mutableStateOf(loadSavedModels(context)) }
    var selectedModel by remember { mutableStateOf<SavedModel?>(null) }
    var modelNodes by remember { mutableStateOf(listOf<ModelNode>()) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            videoUri = uri
            exportStatus = "Видео выбрано"
        }
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { importModelToFilesDir(context, uri) }
                if (saved != null) {
                    savedModels = loadSavedModels(context)
                    exportStatus = "3D-модель сохранена: ${saved.name}"
                } else {
                    exportStatus = "Не удалось импортировать модель"
                }
            }
        }
    }

    val exoPlayer = remember(videoUri, selectedFx) {
        videoUri?.let { uri ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                setVideoEffects(buildPreviewEffects(selectedFx))
                prepare()
                playWhenReady = false
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val d = player?.duration ?: 0L
                if (d > 0) durationMs = d
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            playheadMs = exoPlayer?.currentPosition ?: 0L
            val d = exoPlayer?.duration ?: 0L
            if (d > 0) durationMs = d
            delay(100)
        }
    }

    LaunchedEffect(selectedModel) {
        val m = selectedModel ?: return@LaunchedEffect
        val node = withContext(Dispatchers.IO) {
            try {
                modelLoader.createModelInstance(m.file)?.let { instance ->
                    ModelNode(modelInstance = instance, scaleToUnits = 1.0f).apply {
                        position = io.github.sceneview.math.Position(z = -2.0f)
                    }
                }
            } catch (_: Exception) { null }
        }
        if (node != null) modelNodes = modelNodes + node
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            BottomToolsSheet(
                selectedFx = selectedFx,
                onFx = { selectedFx = it },
                onPickVideo = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                onImportModel = { modelPicker.launch("*/*") },
                models = savedModels,
                onSelectModel = { selectedModel = it; showSheet = false }
            )
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Настройки") },
            text = { Text("CamRIP v1.0.0\n3D: SceneView/Filament\nЭкспорт: Media3 Transformer") },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("OK") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CamRIP") },
                actions = {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "Настройки") }
                    Button(
                        onClick = {
                            val uri = videoUri ?: run { exportStatus = "Сначала выберите видео"; return@Button }
                            exporting = true
                            exportStatus = "Экспорт..."
                            exportWithTransformer(context, uri, selectedFx) { ok, msg ->
                                exporting = false
                                exportStatus = msg
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !exporting
                    ) { Text("Export") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showSheet = true }, shape = CircleShape) {
                Icon(Icons.Default.Add, contentDescription = "Добавить")
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black, RoundedCornerShape(16.dp))
            ) {
                if (exoPlayer != null) {
                    AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true } },
                        update = { it.player = exoPlayer },
                        modifier = Modifier.fillMaxSize()
                    )
                    Scene(
                        modifier = Modifier.fillMaxSize(),
                        engine = engine,
                        modelLoader = modelLoader,
                        childNodes = modelNodes,
                        onViewCreated = { this.scene.skybox = null }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Выберите видео через +", color = Color(0xFFAAAAAA))
                    }
                }
            }

            if (exportStatus.isNotBlank()) {
                Text(exportStatus, color = Color(0xFFB0B0C0))
            }

            TimelineBar(
                durationMs = durationMs,
                playheadMs = playheadMs,
                onSeek = { ms ->
                    playheadMs = ms
                    exoPlayer?.seekTo(ms)
                },
                isPlaying = isPlaying,
                onPlayPause = { if (exoPlayer?.isPlaying == true) exoPlayer.pause() else exoPlayer?.play() }
            )
        }
    }
}

@Composable
fun BottomToolsSheet(
    selectedFx: VideoFx,
    onFx: (VideoFx) -> Unit,
    onPickVideo: () -> Unit,
    onImportModel: () -> Unit,
    models: List<SavedModel>,
    onSelectModel: (SavedModel) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Инструменты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Button(onClick = onPickVideo, modifier = Modifier.fillMaxWidth()) { Text("Добавить видео") }

        Text("Эффекты", fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VideoFx.entries) { fx ->
                FilterChip(selected = selectedFx == fx, onClick = { onFx(fx) }, label = { Text(fx.title) })
            }
        }

        Text("3D модели (.glb/.gltf)", fontWeight = FontWeight.SemiBold)
        Button(onClick = onImportModel) { Text("Импорт 3D модели") }
        if (models.isEmpty()) {
            Text("Сохранённых моделей нет", color = Color(0xFFAAAAAA))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                models.forEach { m ->
                    Card(onClick = { onSelectModel(m) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Text(m.name, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun TimelineBar(durationMs: Long, playheadMs: Long, onSeek: (Long) -> Unit, isPlaying: Boolean, onPlayPause: () -> Unit) {
    val total = durationMs.coerceAtLeast(1L)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Таймлайн", fontWeight = FontWeight.SemiBold)
            Text("${formatMs(playheadMs)} / ${formatMs(durationMs)}", color = Color(0xFFB0B0C0))
        }
        Slider(value = playheadMs.toFloat(), onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..total.toFloat())
        Box(Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF101018), RoundedCornerShape(8.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                val step = 20f
                var x = 0f
                while (x < size.width) {
                    drawLine(Color(0xFF3A3A4A), Offset(x, 0f), Offset(x, size.height), 2f)
                    x += step
                }
                val px = (playheadMs.toFloat() / total.toFloat()) * size.width
                drawLine(Color(0xFFFF4FD8), Offset(px, 0f), Offset(px, size.height), 4f)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlayPause) { Text(if (isPlaying) "Пауза" else "Старт") }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
fun buildPreviewEffects(fx: VideoFx): List<Effect> = when (fx) {
    VideoFx.NONE -> emptyList()
    VideoFx.GRAYSCALE -> listOf(RgbFilter.createGrayscaleFilter())
    VideoFx.BRIGHTNESS -> listOf(Brightness(0.2f))
    VideoFx.CONTRAST -> listOf(Contrast(0.3f))
    VideoFx.GLITCH -> listOf(RgbFilter.createGrayscaleFilter(), Contrast(0.4f))
    VideoFx.BLUR -> emptyList()
}

@androidx.annotation.OptIn(UnstableApi::class)
fun exportWithTransformer(context: Context, uri: Uri, fx: VideoFx, onDone: (Boolean, String) -> Unit) {
    val outFile = File(context.cacheDir, "camrip_export_${System.currentTimeMillis()}.mp4")
    val effects = when (fx) {
        VideoFx.NONE -> Effects(emptyList(), emptyList())
        VideoFx.GRAYSCALE -> Effects(emptyList(), listOf(RgbFilter.createGrayscaleFilter()))
        VideoFx.BRIGHTNESS -> Effects(emptyList(), listOf(Brightness(0.2f)))
        VideoFx.CONTRAST -> Effects(emptyList(), listOf(Contrast(0.3f)))
        VideoFx.GLITCH -> Effects(emptyList(), listOf(RgbFilter.createGrayscaleFilter(), Contrast(0.4f)))
        VideoFx.BLUR -> Effects(emptyList(), emptyList())
    }
    val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(effects).build()
    val transformer = Transformer.Builder(context)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                val ok = saveVideoToMovies(context, outFile)
                outFile.delete()
                onDone(ok, if (ok) "Экспорт сохранён в галерею" else "Рендер завершён, но сохранить не удалось")
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                outFile.delete()
                onDone(false, "Ошибка экспорта: ${exportException.message ?: exportException}")
            }
        })
        .build()
    transformer.start(edited, outFile.absolutePath)
}

fun saveVideoToMovies(context: Context, file: File): Boolean {
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
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return@try false
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return@try false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0); resolver.update(uri, values, null, null)
        }
        true
    } catch (_: Exception) { false }
}

fun importModelToFilesDir(context: Context, uri: Uri): SavedModel? {
    return try {
        val name = (uri.lastPathSegment ?: "model.glb").substringAfterLast('/').let { if (it.endsWith(".glb") || it.endsWith(".gltf")) it else "$it.glb" }
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val out = File(dir, name)
        context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(out).use { input.copyTo(it) } } ?: return@try null
        SavedModel(out.name, out)
    } catch (_: Exception) { null }
}

fun loadSavedModels(context: Context): List<SavedModel> {
    val dir = File(context.filesDir, "models")
    return dir.listFiles()?.filter { it.isFile && (it.name.endsWith(".glb") || it.name.endsWith(".gltf")) }?.map { SavedModel(it.name, it) } ?: emptyList()
}

fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}
