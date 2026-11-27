package com.vandal.camrip.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.StringWriter

fun Project.toXml(): String {
    val serializer = Xml.newSerializer()
    val writer = StringWriter()
    serializer.setOutput(writer)
    serializer.startDocument("UTF-8", true)
    serializer.startTag("", "project")
    serializer.attribute("", "id", id.toString())
    serializer.attribute("", "name", name)
    serializer.attribute("", "createdAt", createdAt.toString())
    serializer.attribute("", "updatedAt", updatedAt.toString())
    serializer.attribute("", "aspectRatio", aspectRatio)
    serializer.attribute("", "exportQuality", exportQuality)

    clips.forEach { c ->
        serializer.startTag("", "clip")
        serializer.attribute("", "id", c.id.toString())
        serializer.attribute("", "uri", c.uri)
        serializer.attribute("", "startMs", c.startMs.toString())
        serializer.attribute("", "endMs", c.endMs.toString())
        serializer.attribute("", "durationMs", c.durationMs.toString())
        serializer.attribute("", "label", c.label)
        serializer.attribute("", "groupId", c.groupId ?: "")
        serializer.attribute("", "offsetX", c.offsetX.toString())
        serializer.attribute("", "offsetY", c.offsetY.toString())
        serializer.attribute("", "scale", c.scale.toString())
        serializer.attribute("", "rotation", c.rotation.toString())
        serializer.attribute("", "volume", c.volume.toString())
        serializer.attribute("", "speed", c.speed.toString())
        c.keyframes.forEach { k ->
            serializer.startTag("", "kf"); serializer.attribute("", "timeMs", k.timeMs.toString()); serializer.attribute("", "offsetX", k.offsetX.toString()); serializer.attribute("", "offsetY", k.offsetY.toString()); serializer.attribute("", "scale", k.scale.toString()); serializer.attribute("", "rotation", k.rotation.toString()); serializer.attribute("", "volume", k.volume.toString()); serializer.attribute("", "speed", k.speed.toString()); serializer.endTag("", "kf")
        }
        serializer.endTag("", "clip")
    }
    effects.forEach { e ->
        serializer.startTag("", "effect")
        serializer.attribute("", "id", e.id.toString())
        serializer.attribute("", "clipId", e.clipId.toString())
        serializer.attribute("", "type", e.type)
        serializer.attribute("", "intensity", e.intensity.toString())
        serializer.attribute("", "angleX", e.angleX.toString())
        serializer.attribute("", "angleY", e.angleY.toString())
        serializer.attribute("", "angleZ", e.angleZ.toString())
        serializer.attribute("", "scale", e.scale.toString())
        serializer.attribute("", "speed", e.speed.toString())
        serializer.attribute("", "groupId", e.groupId ?: "")
        serializer.attribute("", "startMs", e.startMs.toString())
        serializer.endTag("", "effect")
    }
    models3d.forEach { m ->
        serializer.startTag("", "model3d")
        serializer.attribute("", "id", m.id.toString())
        serializer.attribute("", "clipId", m.clipId.toString())
        serializer.attribute("", "name", m.name)
        serializer.attribute("", "filePath", m.filePath)
        serializer.attribute("", "posX", m.posX.toString())
        serializer.attribute("", "posY", m.posY.toString())
        serializer.attribute("", "posZ", m.posZ.toString())
        serializer.attribute("", "rotX", m.rotX.toString())
        serializer.attribute("", "rotY", m.rotY.toString())
        serializer.attribute("", "rotZ", m.rotZ.toString())
        serializer.attribute("", "scale", m.scale.toString())
        serializer.attribute("", "groupId", m.groupId ?: "")
        serializer.attribute("", "startMs", m.startMs.toString())
        serializer.endTag("", "model3d")
    }
    shapes.forEach { s ->
        serializer.startTag("", "shape")
        serializer.attribute("", "id", s.id.toString())
        serializer.attribute("", "clipId", s.clipId.toString())
        serializer.attribute("", "type", s.type)
        serializer.attribute("", "x", s.x.toString())
        serializer.attribute("", "y", s.y.toString())
        serializer.attribute("", "w", s.w.toString())
        serializer.attribute("", "h", s.h.toString())
        serializer.attribute("", "color", s.color.toString())
        serializer.attribute("", "stroke", s.stroke.toString())
        serializer.attribute("", "startMs", s.startMs.toString())
        serializer.attribute("", "endMs", s.endMs.toString())
        serializer.attribute("", "groupId", s.groupId ?: "")
        serializer.attribute("", "filled", s.filled.toString())
        s.keyframes.forEach { k ->
            serializer.startTag("", "skf"); serializer.attribute("", "timeMs", k.timeMs.toString()); serializer.attribute("", "x", k.x.toString()); serializer.attribute("", "y", k.y.toString()); serializer.attribute("", "w", k.w.toString()); serializer.attribute("", "h", k.h.toString()); serializer.endTag("", "skf")
        }
        serializer.endTag("", "shape")
    }
    serializer.endTag("", "project")
    serializer.endDocument()
    return writer.toString()
}

fun parseProjectXml(input: InputStream): Project? {
    return try {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        var event = parser.eventType
        var id = 0L; var name = "Импорт"; var createdAt = System.currentTimeMillis(); var updatedAt = System.currentTimeMillis()
        var aspectRatio = "16:9"; var exportQuality = "high"
        val clips = mutableListOf<Clip>()
        val effects = mutableListOf<EffectItem>()
        val models = mutableListOf<Model3DItem>()
        val shapes = mutableListOf<ShapeItem>()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "project" -> {
                        id = parser.getAttributeValue("", "id")?.toLongOrNull() ?: System.currentTimeMillis()
                        name = parser.getAttributeValue("", "name") ?: "Импорт"
                        createdAt = parser.getAttributeValue("", "createdAt")?.toLongOrNull() ?: System.currentTimeMillis()
                        updatedAt = parser.getAttributeValue("", "updatedAt")?.toLongOrNull() ?: System.currentTimeMillis()
                        aspectRatio = parser.getAttributeValue("", "aspectRatio") ?: "16:9"
                        exportQuality = parser.getAttributeValue("", "exportQuality") ?: "high"
                    }
                    "clip" -> clips += Clip(
                        id = parser.getAttributeValue("", "id")?.toLongOrNull() ?: System.currentTimeMillis(),
                        uri = parser.getAttributeValue("", "uri") ?: "",
                        startMs = parser.getAttributeValue("", "startMs")?.toLongOrNull() ?: 0L,
                        endMs = parser.getAttributeValue("", "endMs")?.toLongOrNull() ?: 0L,
                        durationMs = parser.getAttributeValue("", "durationMs")?.toLongOrNull() ?: 0L,
                        label = parser.getAttributeValue("", "label") ?: "Клип",
                        groupId = parser.getAttributeValue("", "groupId").let { if (it.isNullOrBlank()) null else it },
                        offsetX = parser.getAttributeValue("", "offsetX")?.toFloatOrNull() ?: 0f,
                        offsetY = parser.getAttributeValue("", "offsetY")?.toFloatOrNull() ?: 0f,
                        scale = parser.getAttributeValue("", "scale")?.toFloatOrNull() ?: 1f,
                        rotation = parser.getAttributeValue("", "rotation")?.toFloatOrNull() ?: 0f,
                        volume = parser.getAttributeValue("", "volume")?.toFloatOrNull() ?: 1f,
                        speed = parser.getAttributeValue("", "speed")?.toFloatOrNull() ?: 1f,
                    )
                    "effect" -> effects += EffectItem(
                        id = parser.getAttributeValue("", "id")?.toLongOrNull() ?: System.currentTimeMillis(),
                        clipId = parser.getAttributeValue("", "clipId")?.toLongOrNull() ?: 0L,
                        type = parser.getAttributeValue("", "type") ?: "brightness",
                        intensity = parser.getAttributeValue("", "intensity")?.toFloatOrNull() ?: 0.5f,
                        angleX = parser.getAttributeValue("", "angleX")?.toFloatOrNull() ?: 0f,
                        angleY = parser.getAttributeValue("", "angleY")?.toFloatOrNull() ?: 0f,
                        angleZ = parser.getAttributeValue("", "angleZ")?.toFloatOrNull() ?: 0f,
                        scale = parser.getAttributeValue("", "scale")?.toFloatOrNull() ?: 1f,
                        speed = parser.getAttributeValue("", "speed")?.toFloatOrNull() ?: 1f,
                        groupId = parser.getAttributeValue("", "groupId").let { if (it.isNullOrBlank()) null else it },
                        startMs = parser.getAttributeValue("", "startMs")?.toLongOrNull() ?: 0L,
                    )
                    "model3d" -> models += Model3DItem(
                        id = parser.getAttributeValue("", "id")?.toLongOrNull() ?: System.currentTimeMillis(),
                        clipId = parser.getAttributeValue("", "clipId")?.toLongOrNull() ?: 0L,
                        name = parser.getAttributeValue("", "name") ?: "3D",
                        filePath = parser.getAttributeValue("", "filePath") ?: "",
                        posX = parser.getAttributeValue("", "posX")?.toFloatOrNull() ?: 0f,
                        posY = parser.getAttributeValue("", "posY")?.toFloatOrNull() ?: 0f,
                        posZ = parser.getAttributeValue("", "posZ")?.toFloatOrNull() ?: -2f,
                        rotX = parser.getAttributeValue("", "rotX")?.toFloatOrNull() ?: 0f,
                        rotY = parser.getAttributeValue("", "rotY")?.toFloatOrNull() ?: 0f,
                        rotZ = parser.getAttributeValue("", "rotZ")?.toFloatOrNull() ?: 0f,
                        scale = parser.getAttributeValue("", "scale")?.toFloatOrNull() ?: 1f,
                        groupId = parser.getAttributeValue("", "groupId").let { if (it.isNullOrBlank()) null else it },
                        startMs = parser.getAttributeValue("", "startMs")?.toLongOrNull() ?: 0L,
                    )
                    "shape" -> shapes += ShapeItem(
                        id = parser.getAttributeValue("", "id")?.toLongOrNull() ?: System.currentTimeMillis(),
                        clipId = parser.getAttributeValue("", "clipId")?.toLongOrNull() ?: 0L,
                        type = parser.getAttributeValue("", "type") ?: "rect",
                        x = parser.getAttributeValue("", "x")?.toFloatOrNull() ?: 0.2f,
                        y = parser.getAttributeValue("", "y")?.toFloatOrNull() ?: 0.2f,
                        w = parser.getAttributeValue("", "w")?.toFloatOrNull() ?: 0.3f,
                        h = parser.getAttributeValue("", "h")?.toFloatOrNull() ?: 0.2f,
                        color = parser.getAttributeValue("", "color")?.toLongOrNull() ?: 0xFFFF4FD8,
                        stroke = parser.getAttributeValue("", "stroke")?.toFloatOrNull() ?: 8f,
                        startMs = parser.getAttributeValue("", "startMs")?.toLongOrNull() ?: 0L,
                        endMs = parser.getAttributeValue("", "endMs")?.toLongOrNull() ?: 0L,
                        groupId = parser.getAttributeValue("", "groupId").let { if (it.isNullOrBlank()) null else it },
                        filled = parser.getAttributeValue("", "filled")?.toBooleanStrictOrNull() ?: false,
                    )
                    "kf" -> if (clips.isNotEmpty()) clips[clips.lastIndex] = clips.last().copy(keyframes = clips.last().keyframes + Keyframe(parser.getAttributeValue("", "timeMs")?.toLongOrNull() ?: 0L, parser.getAttributeValue("", "offsetX")?.toFloatOrNull() ?: 0f, parser.getAttributeValue("", "offsetY")?.toFloatOrNull() ?: 0f, parser.getAttributeValue("", "scale")?.toFloatOrNull() ?: 1f, parser.getAttributeValue("", "rotation")?.toFloatOrNull() ?: 0f, parser.getAttributeValue("", "volume")?.toFloatOrNull() ?: 1f, parser.getAttributeValue("", "speed")?.toFloatOrNull() ?: 1f))
                    "skf" -> if (shapes.isNotEmpty()) shapes[shapes.lastIndex] = shapes.last().copy(keyframes = shapes.last().keyframes + ShapeKeyframe(parser.getAttributeValue("", "timeMs")?.toLongOrNull() ?: 0L, parser.getAttributeValue("", "x")?.toFloatOrNull() ?: 0.2f, parser.getAttributeValue("", "y")?.toFloatOrNull() ?: 0.2f, parser.getAttributeValue("", "w")?.toFloatOrNull() ?: 0.3f, parser.getAttributeValue("", "h")?.toFloatOrNull() ?: 0.2f))
                }
            }
            event = parser.next()
        }
        Project(id, name, createdAt, updatedAt, clips, effects, models, shapes, aspectRatio, exportQuality)
    } catch (_: Exception) { null }
}
