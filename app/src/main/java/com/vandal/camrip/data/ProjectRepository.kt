package com.vandal.camrip.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProjectRepository(private val context: Context) {
    private val file: File get() = File(context.filesDir, "projects.json")

    @Synchronized
    fun loadProjects(): List<Project> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i -> arr.getJSONObject(i).toProject() }.sortedByDescending { it.updatedAt }
        } catch (_: Exception) { emptyList() }
    }

    fun getProject(id: Long): Project? = loadProjects().firstOrNull { it.id == id }

    @Synchronized
    fun saveProject(project: Project) {
        val list = loadProjects().toMutableList()
        val idx = list.indexOfFirst { it.id == project.id }
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        if (idx >= 0) list[idx] = updated else list.add(0, updated)
        saveAll(list)
    }

    @Synchronized
    fun deleteProject(id: Long) { saveAll(loadProjects().filterNot { it.id == id }) }

    private fun saveAll(list: List<Project>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString())
    }
}

private fun Project.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("name", name); put("createdAt", createdAt); put("updatedAt", updatedAt); put("aspectRatio", aspectRatio); put("exportQuality", exportQuality)
    put("clips", JSONArray().apply { clips.forEach { c ->
        put(JSONObject().apply {
            put("id", c.id); put("uri", c.uri); put("startMs", c.startMs); put("endMs", c.endMs); put("durationMs", c.durationMs); put("label", c.label)
            put("groupId", c.groupId ?: "")
            put("offsetX", c.offsetX.toDouble()); put("offsetY", c.offsetY.toDouble()); put("scale", c.scale.toDouble()); put("rotation", c.rotation.toDouble())
            put("volume", c.volume.toDouble()); put("speed", c.speed.toDouble())
            put("keyframes", JSONArray().apply { c.keyframes.forEach { k -> put(JSONObject().apply { put("timeMs", k.timeMs); put("offsetX", k.offsetX.toDouble()); put("offsetY", k.offsetY.toDouble()); put("scale", k.scale.toDouble()); put("rotation", k.rotation.toDouble()); put("volume", k.volume.toDouble()); put("speed", k.speed.toDouble()) }) } })
        })
    }})
    put("effects", JSONArray().apply { effects.forEach { e ->
        put(JSONObject().apply {
            put("id", e.id); put("clipId", e.clipId); put("type", e.type)
            put("intensity", e.intensity.toDouble()); put("angleX", e.angleX.toDouble()); put("angleY", e.angleY.toDouble()); put("angleZ", e.angleZ.toDouble())
            put("scale", e.scale.toDouble()); put("speed", e.speed.toDouble()); put("groupId", e.groupId ?: ""); put("startMs", e.startMs)
        })
    }})
    put("models3d", JSONArray().apply { models3d.forEach { m ->
        put(JSONObject().apply {
            put("id", m.id); put("clipId", m.clipId); put("name", m.name); put("filePath", m.filePath)
            put("posX", m.posX.toDouble()); put("posY", m.posY.toDouble()); put("posZ", m.posZ.toDouble())
            put("rotX", m.rotX.toDouble()); put("rotY", m.rotY.toDouble()); put("rotZ", m.rotZ.toDouble()); put("scale", m.scale.toDouble())
            put("groupId", m.groupId ?: ""); put("startMs", m.startMs)
        })
    }})
    put("shapes", JSONArray().apply { shapes.forEach { s ->
        put(JSONObject().apply {
            put("id", s.id); put("clipId", s.clipId); put("type", s.type)
            put("x", s.x.toDouble()); put("y", s.y.toDouble()); put("w", s.w.toDouble()); put("h", s.h.toDouble())
            put("color", s.color); put("stroke", s.stroke.toDouble()); put("startMs", s.startMs); put("endMs", s.endMs); put("groupId", s.groupId ?: ""); put("filled", s.filled)
            put("keyframes", JSONArray().apply { s.keyframes.forEach { k -> put(JSONObject().apply { put("timeMs", k.timeMs); put("x", k.x.toDouble()); put("y", k.y.toDouble()); put("w", k.w.toDouble()); put("h", k.h.toDouble()) }) } })
        })
    }})
}

private fun JSONObject.toProject(): Project {
    val clipsArr = optJSONArray("clips") ?: JSONArray()
    val effectsArr = optJSONArray("effects") ?: JSONArray()
    val modelsArr = optJSONArray("models3d") ?: JSONArray()
    val shapesArr = optJSONArray("shapes") ?: JSONArray()
    return Project(
        id = optLong("id"), name = optString("name"), createdAt = optLong("createdAt"), updatedAt = optLong("updatedAt"), aspectRatio = optString("aspectRatio", "16:9"), exportQuality = optString("exportQuality", "high"),
        clips = (0 until clipsArr.length()).map { i -> clipsArr.getJSONObject(i).let { c ->
            val kfArr = c.optJSONArray("keyframes") ?: JSONArray()
            Clip(c.optLong("id"), c.optString("uri"), c.optLong("startMs"), c.optLong("endMs"), c.optLong("durationMs"), c.optString("label", "Клип"), c.optString("groupId").ifBlank { null }, c.optDouble("offsetX", 0.0).toFloat(), c.optDouble("offsetY", 0.0).toFloat(), c.optDouble("scale", 1.0).toFloat(), c.optDouble("rotation", 0.0).toFloat(), c.optDouble("volume", 1.0).toFloat(), c.optDouble("speed", 1.0).toFloat(), (0 until kfArr.length()).map { j -> kfArr.getJSONObject(j).let { k -> Keyframe(k.optLong("timeMs"), k.optDouble("offsetX", 0.0).toFloat(), k.optDouble("offsetY", 0.0).toFloat(), k.optDouble("scale", 1.0).toFloat(), k.optDouble("rotation", 0.0).toFloat(), k.optDouble("volume", 1.0).toFloat(), k.optDouble("speed", 1.0).toFloat()) } })
        } },
        effects = (0 until effectsArr.length()).map { i -> effectsArr.getJSONObject(i).let { e ->
            EffectItem(e.optLong("id"), e.optLong("clipId"), e.optString("type"), e.optDouble("intensity", 0.5).toFloat(), e.optDouble("angleX", 0.0).toFloat(), e.optDouble("angleY", 0.0).toFloat(), e.optDouble("angleZ", 0.0).toFloat(), e.optDouble("scale", 1.0).toFloat(), e.optDouble("speed", 1.0).toFloat(), e.optString("groupId").ifBlank { null }, e.optLong("startMs", 0L))
        } },
        models3d = (0 until modelsArr.length()).map { i -> modelsArr.getJSONObject(i).let { m ->
            Model3DItem(m.optLong("id"), m.optLong("clipId"), m.optString("name"), m.optString("filePath"), m.optDouble("posX", 0.0).toFloat(), m.optDouble("posY", 0.0).toFloat(), m.optDouble("posZ", -2.0).toFloat(), m.optDouble("rotX", 0.0).toFloat(), m.optDouble("rotY", 0.0).toFloat(), m.optDouble("rotZ", 0.0).toFloat(), m.optDouble("scale", 1.0).toFloat(), m.optString("groupId").ifBlank { null }, m.optLong("startMs", 0L))
        } },
        shapes = (0 until shapesArr.length()).map { i -> shapesArr.getJSONObject(i).let { s ->
            val skArr = s.optJSONArray("keyframes") ?: JSONArray()
            ShapeItem(s.optLong("id"), s.optLong("clipId"), s.optString("type", "rect"), s.optDouble("x", 0.2).toFloat(), s.optDouble("y", 0.2).toFloat(), s.optDouble("w", 0.3).toFloat(), s.optDouble("h", 0.2).toFloat(), s.optLong("color", 0xFFFF4FD8), s.optDouble("stroke", 8.0).toFloat(), s.optLong("startMs", 0L), s.optLong("endMs", 0L), s.optString("groupId").ifBlank { null }, s.optBoolean("filled", false), (0 until skArr.length()).map { j -> skArr.getJSONObject(j).let { k -> ShapeKeyframe(k.optLong("timeMs"), k.optDouble("x", 0.2).toFloat(), k.optDouble("y", 0.2).toFloat(), k.optDouble("w", 0.3).toFloat(), k.optDouble("h", 0.2).toFloat()) } })
        } }
    )
}
