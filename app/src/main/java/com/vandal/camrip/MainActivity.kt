package com.vandal.camrip

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.vandal.camrip.data.SettingsRepository
import com.vandal.camrip.navigation.AppNavGraph
import com.vandal.camrip.ui.theme.DesignTokens

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val settings = remember { SettingsRepository(context) }
            val accentName by settings.accent.collectAsState(initial = "pink")
            val themeName by settings.theme.collectAsState(initial = "dark")
            val fontPath by settings.fontPath.collectAsState(initial = "")
            val dynamic = accentName == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val accent = when (accentName) {
                "green" -> DesignTokens.Secondary
                "blue" -> DesignTokens.Tertiary
                "orange" -> Color(0xFFFFA94F)
                else -> DesignTokens.Primary
            }
            val dark = if (dynamic) dynamicDarkColorScheme(context) else darkColorScheme(
                primary = accent, secondary = DesignTokens.Secondary, tertiary = DesignTokens.Tertiary,
                background = DesignTokens.BackgroundDark, surface = DesignTokens.SurfaceDark, surfaceVariant = DesignTokens.SurfaceVariantDark, onSurface = DesignTokens.OnSurfaceDark
            )
            val light = if (dynamic) dynamicLightColorScheme(context) else lightColorScheme(primary = accent, secondary = Color(0xFF2D8A5F), background = Color(0xFFF7F7FC), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111122))
            val typography = remember(fontPath) {
                val file = java.io.File(fontPath)
                if (fontPath.isNotBlank() && file.exists()) {
                    try {
                        val tf = android.graphics.Typeface.createFromFile(file)
                        val ff = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Typeface(tf))
                        Typography(
                            displayLarge = Typography().displayLarge.copy(fontFamily = ff),
                            displayMedium = Typography().displayMedium.copy(fontFamily = ff),
                            displaySmall = Typography().displaySmall.copy(fontFamily = ff),
                            headlineLarge = Typography().headlineLarge.copy(fontFamily = ff),
                            headlineMedium = Typography().headlineMedium.copy(fontFamily = ff),
                            headlineSmall = Typography().headlineSmall.copy(fontFamily = ff),
                            titleLarge = Typography().titleLarge.copy(fontFamily = ff),
                            titleMedium = Typography().titleMedium.copy(fontFamily = ff),
                            titleSmall = Typography().titleSmall.copy(fontFamily = ff),
                            bodyLarge = Typography().bodyLarge.copy(fontFamily = ff),
                            bodyMedium = Typography().bodyMedium.copy(fontFamily = ff),
                            bodySmall = Typography().bodySmall.copy(fontFamily = ff),
                            labelLarge = Typography().labelLarge.copy(fontFamily = ff),
                            labelMedium = Typography().labelMedium.copy(fontFamily = ff),
                            labelSmall = Typography().labelSmall.copy(fontFamily = ff),
                        )
                    } catch (_: Exception) { Typography() }
                } else Typography()
            }
            MaterialTheme(colorScheme = if (themeName == "light") light else dark, shapes = DesignTokens.Shapes, typography = typography) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConnectivityGate { AppNavGraph() }
                }
            }
        }
    }
}

@Composable
fun ConnectivityGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var checking by remember { mutableStateOf(true) }
    var timeout by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    LaunchedEffect(attempt) {
        checking = true; timeout = false
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 5000) {
            if (isOnline()) { checking = false; timeout = false; return@LaunchedEffect }
            delay(300)
        }
        checking = false
        timeout = !isOnline()
    }

    if (!checking && !timeout) {
        content()
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            ElevatedCard(Modifier.padding(24.dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (timeout) "Ошибка подключения" else "Подключение", style = MaterialTheme.typography.titleLarge)
                    if (!timeout) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        val t = rememberInfiniteTransition(label = "shapes")
                        val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "r")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(18.dp).rotate(r * 0.2f).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                            Box(Modifier.size(18.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                            Box(Modifier.size(18.dp).rotate(45f + r * 0.1f).background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(3.dp)))
                        }
                        Text("Загрузка...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Timeout", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineSmall)
                        Text("Не удалось загрузиться. Проверь интернет и попробуй снова.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { attempt++ }) { Text("Повторить") }
                            OutlinedButton(onClick = { timeout = false; checking = false }) { Text("Офлайн") }
                        }
                    }
                }
            }
        }
    }
}
