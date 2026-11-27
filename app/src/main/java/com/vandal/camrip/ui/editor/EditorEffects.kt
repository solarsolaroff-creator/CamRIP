package com.vandal.camrip.ui.editor

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import com.vandal.camrip.data.EffectItem

@androidx.annotation.OptIn(UnstableApi::class)
fun EffectItem.toMedia3Effects(): List<Effect> = when (type) {
    "grayscale" -> listOf(RgbFilter.createGrayscaleFilter())
    "brightness" -> listOf(Brightness(((intensity - 0.5f) * 1.2f).coerceIn(-1f, 1f)))
    "contrast" -> listOf(Contrast(((intensity - 0.5f) * 1.6f).coerceIn(-1f, 1f)))
    "fade" -> listOf(Brightness((-intensity * 1.0f).coerceIn(-1f, 1f)))
    "punch" -> listOf(Contrast((intensity * 1.0f).coerceIn(-1f, 1f)), Brightness((intensity * 0.15f).coerceIn(-1f, 1f)))
    "gaussian_blur" -> listOf(GaussianBlur((intensity * 20f).coerceAtLeast(0.1f)))
    "hsl_hue" -> listOf(HslAdjustment.Builder().adjustHue(intensity * 360f).build())
    "hsl_saturation" -> listOf(HslAdjustment.Builder().adjustSaturation((intensity - 0.5f) * 200f).build())
    "hsl_lightness" -> listOf(HslAdjustment.Builder().adjustLightness((intensity - 0.5f) * 100f).build())
    "rgb_warm" -> listOf(RgbAdjustment.Builder().setRedScale(1f + intensity * 0.5f).setGreenScale(1f + intensity * 0.1f).setBlueScale((1f - intensity * 0.3f).coerceAtLeast(0f)).build())
    "rgb_cool" -> listOf(RgbAdjustment.Builder().setRedScale((1f - intensity * 0.3f).coerceAtLeast(0f)).setGreenScale(1f).setBlueScale(1f + intensity * 0.5f).build())
    "rotate3d", "perspective", "cube", "parallax", "flip", "tilt" ->
        listOf(ScaleAndRotateTransformation.Builder().setRotationDegrees(angleZ).build())
    "zoom", "zoom3d" -> listOf(ScaleAndRotateTransformation.Builder().setScale(scale.coerceAtLeast(0.1f), scale.coerceAtLeast(0.1f)).build())
    "shake" -> listOf(ScaleAndRotateTransformation.Builder().setRotationDegrees((intensity * 10f)).build())
    else -> emptyList()
}
