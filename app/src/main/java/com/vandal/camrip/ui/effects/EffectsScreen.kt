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

    val effects = remember {
        listOf(
            EffectDef("rotate3d", "3D-поворот", "3D-трансформации", true),
            EffectDef("perspective", "Перспектива", "3D-трансформации", true),
            EffectDef("zoom3d", "3D Зум", "3D-трансформации", true),
            EffectDef("glitch", "Глитч", "Искажения"),
            EffectDef("chromatic", "Хром. аберрация", "Искажения"),
            EffectDef("wave", "Волна", "Искажения"),
            EffectDef("fisheye", "Рыбий глаз", "Искажения"),
            EffectDef("brightness", "Яркость", "Свет и цвет"),
            EffectDef("contrast", "Контраст", "Свет и цвет"),
            EffectDef("grayscale", "Ч/Б", "Свет и цвет"),
            EffectDef("fade", "Затемнение", "Свет и цвет"),
            EffectDef("punch", "Панч", "Свет и цвет"),
            EffectDef("shake", "Тряска камеры", "Камера/шейки"),
            EffectDef("zoom", "Зум-пульс", "Камера/шейки"),
            EffectDef("tilt", "Наклон камеры", "Камера/шейки"),
        )
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Эффекты") }, navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }) }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Нажмите на эффект — он добавится на дорожку. Настроить можно в редакторе.", style = MaterialTheme.typography.bodySmall)
            ScrollableTabRow(selectedTabIndex = categories.indexOf(selectedCategory), edgePadding = 0.dp) {
                categories.forEach { cat ->
                    Tab(selected = cat == selectedCategory, onClick = { selectedCategory = cat }, text = { Text(cat, maxLines = 1) })
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(effects.filter { it.category == selectedCategory }, key = { it.type }) { effect ->
                    ElevatedCard(onClick = {
                        onApply(
                            EffectItem(
                                id = System.currentTimeMillis(),
                                clipId = clipId,
                                type = effect.type,
                                intensity = 0.75f,
                                angleX = 0f, angleY = 0f, angleZ = if (effect.hasAngles) 20f else 0f,
                                scale = 1.2f, speed = 1f
                            )
                        )
                    }) {
                        Column(Modifier.padding(14.dp)) {
                            Text(effect.title, fontWeight = FontWeight.SemiBold)
                            Text(effect.category, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
