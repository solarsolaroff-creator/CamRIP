package com.vandal.camrip.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vandal.camrip.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val quality by settings.quality.collectAsState(initial = "1080p")
    val theme by settings.theme.collectAsState(initial = "dark")
    var msg by remember { mutableStateOf("") }

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
            OutlinedButton(onClick = { settings.clearCache(); msg = "Кэш очищен" }) { Text("Очистить кэш") }
            if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.primary)
            Text("Аккаунты и авторизация отсутствуют. Все данные хранятся локально на устройстве.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
