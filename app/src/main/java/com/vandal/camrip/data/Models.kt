package com.vandal.camrip.data

data class Project(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val clips: List<Clip> = emptyList(),
    val effects: List<EffectItem> = emptyList(),
    val models3d: List<Model3DItem> = emptyList(),
    val shapes: List<ShapeItem> = emptyList(),
    val aspectRatio: String = "16:9",
    val exportQuality: String = "high"
)

data class Clip(
    val id: Long,
    val uri: String,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val durationMs: Long = 0L,
    val label: String = "Клип",
    val groupId: String? = null,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val volume: Float = 1f
)

data class EffectItem(
    val id: Long,
    val clipId: Long,
    val type: String,
    val intensity: Float = 0.5f,
    val angleX: Float = 0f,
    val angleY: Float = 0f,
    val angleZ: Float = 0f,
    val scale: Float = 1f,
    val speed: Float = 1f,
    val groupId: String? = null,
    val startMs: Long = 0L
)

data class Model3DItem(
    val id: Long,
    val clipId: Long,
    val name: String,
    val filePath: String,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val posZ: Float = -2f,
    val rotX: Float = 0f,
    val rotY: Float = 0f,
    val rotZ: Float = 0f,
    val scale: Float = 1f,
    val groupId: String? = null,
    val startMs: Long = 0L
)

data class ShapeItem(
    val id: Long,
    val clipId: Long,
    val type: String, // rect, circle, line, triangle
    val x: Float = 0.2f,
    val y: Float = 0.2f,
    val w: Float = 0.3f,
    val h: Float = 0.2f,
    val color: Long = 0xFFFF4FD8,
    val stroke: Float = 8f,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val groupId: String? = null,
    val filled: Boolean = false
)
