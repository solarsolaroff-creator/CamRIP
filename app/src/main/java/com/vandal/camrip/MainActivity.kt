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
                "green" -> Color(0xFF7CFFB2)
                "blue" -> Color(0xFF7AA2FF)
                "orange" -> Color(0xFFFFA94F)
                else -> Color(0xFFFF4FD8)
            }
            val dark = if (dynamic) dynamicDarkColorScheme(context) else darkColorScheme(
                primary = accent, secondary = Color(0xFF7CFFB2), tertiary = Color(0xFF7AA2FF),
                background = Color(0xFF0A0A12), surface = Color(0xFF151525), surfaceVariant = Color(0xFF1E1E33), onSurface = Color(0xFFF0F0FF)
            )
            val light = if (dynamic) dynamicLightColorScheme(context) else lightColorScheme(primary = accent, secondary = Color(0xFF2D8A5F), background = Color(0xFFF7F7FC), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111122))
            val shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(28.dp)
            )
            MaterialTheme(colorScheme = if (themeName == "light") light else dark, shapes = shapes, typography = Typography()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavGraph()
                }
            }
        }
    }
}
