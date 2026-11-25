package com.vandal.camrip.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vandal.camrip.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val quality by settings.quality.collectAsState(initial = "1080p")
    val theme by settings.theme.collectAsState(initial = "dark")
    val accent by settings.accent.collectAsState(initial = "pink")
    var msg by remember { mutableStateOf("") }
    val accents = listOf("pink" to Color(0xFFFF4FD8), "green" to Color(0xFF7CFFB2), "blue" to Color(0xFF7AA2FF), "orange" to Color(0xFFFFA94F))

    Scaffold(topBar = { TopAppBar(title = { Text("Настройки") }, navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Локальные параметры", style = MaterialTheme.typography.titleMedium)
            Text("Качество экспорта по умолчанию")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("720p", "1080p").forEach { q ->
                    FilterChip(selected = quality == q, onClick = { scope.launch { settings.setQuality(q) } }, label = { Text(q) })
                }
            }
            Text("Тема")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark", "light").forEach { t ->
                    FilterChip(selected = theme == t, onClick = { scope.launch { settings.setTheme(t) } }, label = { Text(t) })
                }
            }
            Text("Цвет акцента")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                accents.forEach { (name, color) ->
                    Column {
                        Box(Modifier.size(36.dp).background(color, CircleShape).clickable { scope.launch { settings.setAccent(name) } })
                        if (accent == name) Text("•", color = color)
                    }
                }
            }
            OutlinedButton(onClick = { settings.clearCache(); msg = "Кэш очищен" }) { Text("Очистить кэш") }
            if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.primary)
            Text("Аккаунты и авторизация отсутствуют. Все данные хранятся локально.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
