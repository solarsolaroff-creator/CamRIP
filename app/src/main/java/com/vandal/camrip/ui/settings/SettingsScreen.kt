package com.vandal.camrip.ui.settings

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vandal.camrip.data.SettingsRepository
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: SettingsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val quality by settings.quality.collectAsState(initial = "1080p")
    val theme by settings.theme.collectAsState(initial = "dark")
    val accent by settings.accent.collectAsState(initial = "pink")
    val fontPath by settings.fontPath.collectAsState(initial = "")
    val appIcon by settings.appIcon.collectAsState(initial = "default")
    val uiStyle by settings.uiStyle.collectAsState(initial = "material")
    val asciiBg by settings.asciiBg.collectAsState(initial = "off")
    var msg by remember { mutableStateOf("") }
    val accents = listOf("dynamic" to Color(0xFF6750A4), "pink" to Color(0xFFFF4FD8), "green" to Color(0xFF7CFFB2), "blue" to Color(0xFF7AA2FF), "orange" to Color(0xFFFFA94F))
    val iconOptions = listOf("default" to "Стандарт", "cam" to "Камера", "film" to "Плёнка", "neon" to "Неон", "xp" to "XP")

    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val dir = File(context.filesDir, "fonts").apply { mkdirs() }
                val ext = (context.contentResolver.getType(uri) ?: "").let { if (it.contains("otf")) "otf" else "ttf" }
                val out = File(dir, "custom_font.$ext")
                context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                scope.launch { settings.setFontPath(out.absolutePath) }
                msg = "Шрифт применён"
            } catch (e: Exception) { msg = "Ошибка шрифта: ${e.message}" }
        }
    }

    fun applyIcon(key: String) {
        val pm = context.packageManager
        val pkg = context.packageName
        val all = mapOf(
            "default" to "$pkg.DefaultIconAlias",
            "cam" to "$pkg.CamIconAlias",
            "film" to "$pkg.FilmIconAlias",
            "neon" to "$pkg.NeonIconAlias",
            "xp" to "$pkg.XpIconAlias",
        )
        all.forEach { (k, cls) ->
            val state = if (k == key) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            pm.setComponentEnabledSetting(ComponentName(pkg, cls), state, PackageManager.DONT_KILL_APP)
        }
        scope.launch { settings.setAppIcon(key) }
        msg = "Иконка изменена. Лаунчер может обновить её через несколько секунд."
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Настройки") }, navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
            Text("Стиль интерфейса")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("material" to "Material", "metro" to "Metro", "kitkat" to "KitKat", "oneui" to "One UI").forEach { (key, label) ->
                    FilterChip(selected = uiStyle == key, onClick = { scope.launch { settings.setUiStyle(key) } }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("ASCII фон на главном экране"); Text("Анимированный калейдоскоп, по умолчанию выключен", style = MaterialTheme.typography.bodySmall) }
                Switch(checked = asciiBg == "on", onCheckedChange = { v -> scope.launch { settings.setAsciiBg(if (v) "on" else "off") } })
            }
            Text("Цвет акцента / Material You")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                accents.forEach { (name, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(36.dp).background(color, CircleShape).clickable { scope.launch { settings.setAccent(name) } })
                        Text(if (name == "dynamic") "Dyn" else name.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = if (accent == name) color else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            HorizontalDivider()
            Text("Шрифт", style = MaterialTheme.typography.titleMedium)
            Text(if (fontPath.isBlank()) "Используется системный шрифт" else "Свой шрифт: ${File(fontPath).name}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { fontPicker.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*")) }) { Text("Выбрать шрифт") }
                OutlinedButton(onClick = { scope.launch { settings.setFontPath("") }; msg = "Шрифт сброшен" }, enabled = fontPath.isNotBlank()) { Text("Сбросить") }
            }

            HorizontalDivider()
            Text("Иконка приложения", style = MaterialTheme.typography.titleMedium)
            Text("Выберите иконку для лаунчера", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                iconOptions.forEach { (key, label) ->
                    FilterChip(selected = appIcon == key, onClick = { applyIcon(key) }, label = { Text(label) })
                }
            }
            Text("Своя картинка как иконка лаунчера на Android ограничена системой; доступны встроенные варианты.", style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            OutlinedButton(onClick = { settings.clearCache(); msg = "Кэш очищен" }) { Text("Очистить кэш") }
            if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.primary)
            Text("Аккаунты и авторизация отсутствуют. Все данные хранятся локально.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
