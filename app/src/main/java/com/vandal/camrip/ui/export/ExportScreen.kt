package com.vandal.camrip.ui.export

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vandal.camrip.data.ProjectRepository
import com.vandal.camrip.data.parseProjectXml
import com.vandal.camrip.data.toXml
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(repo: ProjectRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    var projects by remember { mutableStateOf(repo.loadProjects()) }
    var status by remember { mutableStateOf("") }
    var pendingExportId by remember { mutableStateOf<Long?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/xml")) { uri: Uri? ->
        val pid = pendingExportId ?: return@rememberLauncherForActivityResult
        val project = repo.loadProjects().firstOrNull { it.id == pid } ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(project.toXml().toByteArray()) }
                status = "Проект экспортирован: ${project.name}"
            } catch (e: Exception) { status = "Ошибка экспорта: ${e.message}" }
        }
        pendingExportId = null
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val project = context.contentResolver.openInputStream(uri)?.use { parseProjectXml(it) }
                if (project != null) {
                    val existing = project.copy(id = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(), name = project.name + " (импорт)")
                    repo.saveProject(existing)
                    projects = repo.loadProjects()
                    val missing = existing.clips.count { c -> c.uri.isBlank() || (!c.uri.startsWith("content://") && !File(c.uri.removePrefix("file://")).exists()) }
                    status = if (missing > 0) "Импортировано. Видео недоступно: $missing (эффекты/фигуры сохранены)" else "Проект импортирован: ${existing.name}"
                } else status = "Не удалось прочитать XML проекта"
            } catch (e: Exception) { status = "Ошибка импорта: ${e.message}" }
        }
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Экспорт проектов") }, navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Импорт/экспорт проекта в XML", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Эффекты, фигуры, 3D и настройки сохраняются. Видео появится, если файл есть на устройстве.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { importLauncher.launch(arrayOf("text/xml", "application/xml", "*/*")) }) { Text("Импортировать XML") }
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
            item { HorizontalDivider(); Text("Экспортировать проект", style = MaterialTheme.typography.titleSmall) }
            items(projects, key = { it.id }) { project ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(project.name, fontWeight = FontWeight.Medium)
                            Text("${project.clips.size} клипов • ${project.effects.size} эффектов • ${project.shapes.size} фигур", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = {
                            pendingExportId = project.id
                            exportLauncher.launch("${project.name}.xml")
                        }) { Text("XML") }
                    }
                }
            }
            if (projects.isEmpty()) item { Text("Нет проектов для экспорта", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
