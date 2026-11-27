package com.vandal.camrip.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vandal.camrip.data.Project
import com.vandal.camrip.data.ProjectRepository
import com.vandal.camrip.data.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: ProjectRepository,
    onOpenProject: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit = {},
    settings: SettingsRepository? = null
) {
    var projects by remember { mutableStateOf(repo.loadProjects()) }
    LaunchedEffect(Unit) { projects = repo.loadProjects() }
    val asciiBg by (settings?.asciiBg ?: kotlinx.coroutines.flow.flowOf("off")).collectAsState(initial = "off")
    val asciiBgUrl by (settings?.asciiBgUrl ?: kotlinx.coroutines.flow.flowOf("")).collectAsState(initial = "")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("CamRIP", fontWeight = FontWeight.Bold); Text("Material 3 видеоредактор", style = MaterialTheme.typography.labelSmall) } },
                actions = {
                    TextButton(onClick = onOpenExport) { Text("XML") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Настройки") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val p = Project(id = System.currentTimeMillis(), name = randomProjectName(), createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
                    repo.saveProject(p)
                    projects = repo.loadProjects()
                    onOpenProject(p.id)
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Новый проект") }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize()) {
            when (asciiBg) {
                "builtin", "on" -> AsciiKaleidoscopeBackground(Modifier.fillMaxSize())
                "url" -> if (asciiBgUrl.isNotBlank()) AndroidView(factory = { ctx -> android.webkit.WebView(ctx).apply { this.settings.javaScriptEnabled = true; this.settings.domStorageEnabled = true; setBackgroundColor(android.graphics.Color.TRANSPARENT); isClickable = false; isFocusable = false; loadUrl(asciiBgUrl) } }, update = { if (it.url != asciiBgUrl) it.loadUrl(asciiBgUrl) }, modifier = Modifier.fillMaxSize())
            }
        if (projects.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Нет проектов", style = MaterialTheme.typography.titleLarge)
                    Text("Нажмите «Новый проект», чтобы начать монтаж")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.padding(pad),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects, key = { it.id }) { project ->
                    ElevatedCard(onClick = { onOpenProject(project.id) }, shape = RoundedCornerShape(18.dp)) {
                        Column {
                            Box(Modifier.fillMaxWidth().height(72.dp).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha=0.35f), MaterialTheme.colorScheme.secondary.copy(alpha=0.25f)))))
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(project.name, fontWeight = FontWeight.SemiBold)
                                Text("${project.clips.size} клипов • ${project.effects.size} эффектов • ${project.models3d.size} 3D", style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(formatDate(project.updatedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.7f))
                                    AssistChip(onClick = {
                                        val options = listOf("16:9", "9:16", "1:1", "4:3", "4:5")
                                        val next = options[(options.indexOf(project.aspectRatio).let { if (it < 0) 0 else it } + 1) % options.size]
                                        val updated = project.copy(aspectRatio = next, updatedAt = System.currentTimeMillis())
                                        repo.saveProject(updated)
                                        projects = repo.loadProjects()
                                    }, label = { Text(project.aspectRatio) })
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

private fun formatDate(ts: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ts))

private fun randomProjectName(): String {
    val chars = ('a'..'z') + ('0'..'9')
    return (1..(10..16).random()).map { chars.random() }.joinToString("")
}
