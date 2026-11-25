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
            MaterialTheme(colorScheme = if (themeName == "light") light else dark, shapes = DesignTokens.Shapes, typography = Typography()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavGraph()
                }
            }
        }
    }
}
