package com.vandal.camrip.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object DesignTokens {
    val Primary = Color(0xFFFF4FD8)
    val Secondary = Color(0xFF7CFFB2)
    val Tertiary = Color(0xFF7AA2FF)
    val BackgroundDark = Color(0xFF0A0A12)
    val SurfaceDark = Color(0xFF151525)
    val SurfaceVariantDark = Color(0xFF1E1E33)
    val OnSurfaceDark = Color(0xFFF0F0FF)

    val Shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )
}
