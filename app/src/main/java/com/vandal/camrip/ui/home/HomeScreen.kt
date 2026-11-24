package com.vandal.camrip.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vandal.camrip.data.Project
import com.vandal.camrip.data.ProjectRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: ProjectRepository,
    onOpenProject: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    var projects by remember { mutableStateOf(repo.loadProjects()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CamRIP") },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Настройки") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val p = Project(id = System.currentTimeMillis(), name = "Проект ${projects.size + 1}", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
                    repo.saveProject(p)
                    projects = repo.loadProjects()
                    onOpenProject(p.id)
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Новый проект") }
            )
        }
    ) { pad ->
        if (projects.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Нет проектов. Нажмите «Новый проект».")
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
                    ElevatedCard(onClick = { onOpenProject(project.id) }) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(project.name, fontWeight = FontWeight.SemiBold)
                            Text("${project.clips.size} клипов • ${project.effects.size} эффектов", style = MaterialTheme.typography.bodySmall)
                            Text(formatDate(project.updatedAt), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(ts: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ts))
