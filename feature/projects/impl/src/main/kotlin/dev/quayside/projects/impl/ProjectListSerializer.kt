package dev.quayside.projects.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectGroups
import dev.quayside.projects.api.ProjectInputRules
import dev.quayside.projects.api.SubfolderName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * `data` of `projects.json`: `{"projects": [{id, dir, displayName, subfolders?}]}` (ADR 0009, 0026).
 *
 * Written by hand on [JsonElement] (no serialization compiler plugin). Unknown keys are skipped; any
 * invalid entry (missing field, blank id or name, relative or unparseable dir, repeated id or dir)
 * throws [SerializationException], so `JsonStore` moves the whole file aside. Exception messages are
 * fixed categories and never quote the input (ENG-C8).
 */
internal object ProjectListSerializer : KSerializer<List<Project>> {
    private const val PROJECTS = "projects"
    private const val ID = "id"
    private const val DIR = "dir"
    private const val DISPLAY_NAME = "displayName"
    private const val SUBFOLDERS = "subfolders"

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: List<Project>,
    ) {
        val json = encoder as? JsonEncoder ?: invalid("only JSON is supported")
        json.encodeJsonElement(
            buildJsonObject {
                putJsonArray(PROJECTS) {
                    value.forEach { project ->
                        addJsonObject {
                            put(ID, project.id.value)
                            put(DIR, project.dir.toString())
                            put(DISPLAY_NAME, project.displayName)
                            if (project.subfolders.isNotEmpty()) {
                                putJsonArray(SUBFOLDERS) { project.subfolders.forEach { add(it.value) } }
                            }
                        }
                    }
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): List<Project> {
        val json = decoder as? JsonDecoder ?: invalid("only JSON is supported")
        val entries =
            ((json.decodeJsonElement() as? JsonObject)?.get(PROJECTS) as? JsonArray) ?: invalid("no $PROJECTS array")
        val projects = entries.map(::project)
        if (projects.distinctBy { it.id }.size != projects.size) invalid("repeated $ID")
        if (projects.distinctBy { it.dir }.size != projects.size) invalid("repeated $DIR")
        return projects
    }

    private fun project(element: JsonElement): Project {
        val entry = element as? JsonObject ?: invalid("entry is not an object")
        val id = entry.text(ID).ifBlank { invalid("blank $ID") }
        val dir = path(entry.text(DIR))
        val name = entry.text(DISPLAY_NAME).ifBlank { invalid("blank $DISPLAY_NAME") }
        return Project(ProjectId(id), dir, name, subfolders(entry))
    }

    /** Absent key = no group; a present key must be an array of distinct valid names, stored in any order. */
    private fun subfolders(entry: JsonObject): List<SubfolderName> {
        val element = entry[SUBFOLDERS] ?: return emptyList()
        val array = element as? JsonArray ?: invalid("$SUBFOLDERS is not an array")
        val names = array.map { subfolder(it) }
        if (names.distinct().size != names.size) invalid("repeated $SUBFOLDERS name")
        return names.sortedWith(ProjectGroups.order)
    }

    private fun subfolder(element: JsonElement): SubfolderName {
        val text =
            (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid("$SUBFOLDERS name is not text")
        return when (val parsed = ProjectInputRules.subfolderName(text)) {
            is Outcome.Ok -> parsed.value
            is Outcome.Err -> invalid("invalid $SUBFOLDERS name")
        }
    }

    private fun JsonObject.text(key: String): String =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalid("missing $key")

    private fun path(text: String): Path {
        val path =
            try {
                Path.of(text)
            } catch (_: InvalidPathException) {
                // The exception message quotes the path: replaced by a fixed category (ENG-C8).
                invalid("unparseable $DIR")
            }
        return if (path.isAbsolute) path else invalid("relative $DIR")
    }

    private fun invalid(reason: String): Nothing = throw SerializationException(reason)
}
