package com.vandal.camrip.data

data class Project(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val clips: List<Clip> = emptyList(),
    val effects: List<EffectItem> = emptyList(),
    val models3d: List<Model3DItem> = emptyList()
)

data class Clip(
    val id: Long,
    val uri: String,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val durationMs: Long = 0L,
    val label: String = "Клип"
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
    val speed: Float = 1f
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
    val scale: Float = 1f
)
