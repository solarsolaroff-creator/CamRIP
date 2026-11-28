package com.vandal.camrip

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
            val uiStyle by settings.uiStyle.collectAsState(initial = "material")
            val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && uiStyle == "material" && accentName !in listOf("green", "blue", "orange")
            val accent = when (accentName) {
                "green" -> DesignTokens.Secondary
                "blue" -> DesignTokens.Tertiary
                "orange" -> Color(0xFFFFA94F)
                else -> DesignTokens.Primary
            }
            val dark = when {
                dynamic -> dynamicDarkColorScheme(context)
                uiStyle == "metro" -> darkColorScheme(primary = Color(0xFF00AEF0), secondary = Color(0xFF00AEF0), tertiary = Color(0xFF7A7A7A), background = Color(0xFF101010), surface = Color(0xFF1A1A1A), surfaceVariant = Color(0xFF242424), onSurface = Color(0xFFFFFFFF))
                uiStyle == "kitkat" -> darkColorScheme(primary = Color(0xFF33B5E5), secondary = Color(0xFF009688), tertiary = Color(0xFF99CC00), background = Color(0xFF212121), surface = Color(0xFF303030), surfaceVariant = Color(0xFF424242), onSurface = Color(0xFFFFFFFF))
                uiStyle == "oneui" -> darkColorScheme(primary = Color(0xFF3DDC84), secondary = Color(0xFF7CF7B2), tertiary = Color(0xFFB0B0B0), background = Color(0xFF000000), surface = Color(0xFF1C1C1E), surfaceVariant = Color(0xFF2C2C2E), onSurface = Color(0xFFF5F5F5))
                uiStyle == "xp" -> darkColorScheme(primary = Color(0xFF245EDB), secondary = Color(0xFF3FA142), tertiary = Color(0xFFE9C46A), background = Color(0xFF0F1F3A), surface = Color(0xFF1A3157), surfaceVariant = Color(0xFF274472), onSurface = Color(0xFFF2F6FF))
                else -> darkColorScheme(primary = accent, secondary = DesignTokens.Secondary, tertiary = DesignTokens.Tertiary, background = DesignTokens.BackgroundDark, surface = DesignTokens.SurfaceDark, surfaceVariant = DesignTokens.SurfaceVariantDark, onSurface = DesignTokens.OnSurfaceDark)
            }
            val light = when {
                dynamic -> dynamicLightColorScheme(context)
                uiStyle == "metro" -> lightColorScheme(primary = Color(0xFF0078D7), secondary = Color(0xFF00AEF0), background = Color(0xFFF2F2F2), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE6E6E6), onSurface = Color(0xFF111111))
                uiStyle == "kitkat" -> lightColorScheme(primary = Color(0xFF009688), secondary = Color(0xFF33B5E5), background = Color(0xFFECEFF1), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFCFD8DC), onSurface = Color(0xFF212121))
                uiStyle == "oneui" -> lightColorScheme(primary = Color(0xFF1F8F4E), secondary = Color(0xFF3DDC84), background = Color(0xFFF5F5F5), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE8E8E8), onSurface = Color(0xFF111111))
                uiStyle == "xp" -> lightColorScheme(primary = Color(0xFF245EDB), secondary = Color(0xFF3FA142), tertiary = Color(0xFFD97706), background = Color(0xFFECE9D8), surface = Color(0xFFF7F5EE), surfaceVariant = Color(0xFFD4D0C8), onSurface = Color(0xFF1B1B1B))
                else -> lightColorScheme(primary = accent, secondary = Color(0xFF2D8A5F), background = Color(0xFFF7F7FC), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111122))
            }
            val shapes = when (uiStyle) {
                "metro" -> Shapes(extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(0.dp), medium = RoundedCornerShape(0.dp), large = RoundedCornerShape(0.dp), extraLarge = RoundedCornerShape(0.dp))
                "kitkat" -> Shapes(extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(2.dp), medium = RoundedCornerShape(2.dp), large = RoundedCornerShape(4.dp), extraLarge = RoundedCornerShape(4.dp))
                "oneui" -> Shapes(extraSmall = RoundedCornerShape(14.dp), small = RoundedCornerShape(18.dp), medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp))
                "xp" -> Shapes(extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(3.dp), medium = RoundedCornerShape(4.dp), large = RoundedCornerShape(6.dp), extraLarge = RoundedCornerShape(8.dp))
                else -> DesignTokens.Shapes
            }
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
            MaterialTheme(colorScheme = if (themeName == "light") light else dark, shapes = shapes, typography = typography) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavGraph()
                }
            }
        }
    }
}
