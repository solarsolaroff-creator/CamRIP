package com.vandal.camrip.ui.effects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vandal.camrip.data.EffectItem

data class EffectDef(val type: String, val title: String, val category: String, val hasAngles: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsScreen(
    clipId: Long,
    onApply: (EffectItem) -> Unit,
    onBack: () -> Unit
) {
    val categories = listOf("3D-трансформации", "Искажения", "Свет и цвет", "Камера/шейки")
    var selectedCategory by remember { mutableStateOf(categories.first()) }
    var selected by remember { mutableStateOf<EffectDef?>(null) }
    var intensity by remember { mutableStateOf(0.5f) }
    var angleX by remember { mutableStateOf(0f) }
    var angleY by remember { mutableStateOf(0f) }
    var angleZ by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }
    var speed by remember { mutableStateOf(1f) }

    val effects = remember {
        listOf(
            EffectDef("rotate3d", "3D-поворот X/Y/Z", "3D-трансформации", true),
            EffectDef("perspective", "Перспектива", "3D-трансформации", true),
            EffectDef("cube", "3D-куб", "3D-трансформации", true),
            EffectDef("parallax", "Параллакс", "3D-трансформации", true),
            EffectDef("wave", "Волна", "Искажения"),
            EffectDef("fisheye", "Рыбий глаз", "Искажения"),
            EffectDef("glitch", "Глитч/Warp", "Искажения"),
            EffectDef("chromatic", "Хром. аберрация", "Свет и цвет"),
            EffectDef("brightness", "Яркость", "Свет и цвет"),
            EffectDef("contrast", "Контраст", "Свет и цвет"),
            EffectDef("grayscale", "Ч/Б", "Свет и цвет"),
            EffectDef("shake", "Тряска камеры", "Камера/шейки"),
            EffectDef("zoom", "Зум-пульс", "Камера/шейки"),
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Эффекты") }, navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }) }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScrollableTabRow(selectedTabIndex = categories.indexOf(selectedCategory), edgePadding = 0.dp) {
                categories.forEach { cat ->
                    Tab(selected = cat == selectedCategory, onClick = { selectedCategory = cat }, text = { Text(cat, maxLines = 1) })
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(effects.filter { it.category == selectedCategory }, key = { it.type }) { effect ->
                    ElevatedCard(
                        onClick = { selected = effect },
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (selected?.type == effect.type) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(effect.title, fontWeight = FontWeight.SemiBold)
                            Text(effect.category, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            selected?.let { effect ->
                HorizontalDivider()
                Text("Параметры: ${effect.title}", fontWeight = FontWeight.SemiBold)
                Text("Интенсивность: ${"%.2f".format(intensity)}")
                Slider(value = intensity, onValueChange = { intensity = it }, valueRange = 0f..1f)
                if (effect.hasAngles) {
                    Text("Угол X: ${angleX.toInt()}°")
                    Slider(value = angleX, onValueChange = { angleX = it }, valueRange = -180f..180f)
                    Text("Угол Y: ${angleY.toInt()}°")
                    Slider(value = angleY, onValueChange = { angleY = it }, valueRange = -180f..180f)
                    Text("Угол Z: ${angleZ.toInt()}°")
                    Slider(value = angleZ, onValueChange = { angleZ = it }, valueRange = -180f..180f)
                    Text("Масштаб: ${"%.2f".format(scale)}")
                    Slider(value = scale, onValueChange = { scale = it }, valueRange = 0.2f..3f)
                }
                Text("Скорость: ${"%.2f".format(speed)}")
                Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.1f..3f)
                Button(
                    onClick = {
                        onApply(
                            EffectItem(
                                id = System.currentTimeMillis(),
                                clipId = clipId,
                                type = effect.type,
                                intensity = intensity,
                                angleX = angleX, angleY = angleY, angleZ = angleZ,
                                scale = scale, speed = speed
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Применить") }
            }
        }
    }
}
