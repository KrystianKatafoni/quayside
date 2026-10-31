package dev.quayside.projects.impl

import dev.quayside.projects.api.Project
import dev.quayside.projects.api.aProject
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/**
 * `data` of `projects.json` (ADR 0009). Uses a plain [Json] without `ignoreUnknownKeys`: the
 * serializer itself must skip unknown keys and reject every invalid entry, so `JsonStore` moves
 * the file aside as `.broken`.
 */
class ProjectListSerializerTest {
    private val json = Json

    private fun decode(data: String): List<Project> = json.decodeFromString(ProjectListSerializer, data)

    @Test
    fun `empty project array decodes to an empty list`() {
        decode("""{"projects": []}""").shouldBeEmpty()
    }

    @Test
    fun `two valid entries decode in stored order`() {
        decode(
            """{"projects": [
                {"id": "p2", "dir": "/Users/test/zeta", "displayName": "Zeta"},
                {"id": "p1", "dir": "/Users/test/alpha", "displayName": "Alpha"}
            ]}""",
        ) shouldContainExactly
            listOf(
                aProject(id = "p2", dir = Path.of("/Users/test/zeta"), displayName = "Zeta"),
                aProject(id = "p1", dir = Path.of("/Users/test/alpha"), displayName = "Alpha"),
            )
    }

    @Test
    fun `unknown key in an entry is skipped`() {
        decode("""{"projects": [{"id": "p1", "dir": "/a", "displayName": "A", "color": "red"}]}""") shouldContainExactly
            listOf(aProject(id = "p1", dir = Path.of("/a"), displayName = "A"))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidData")
    fun `invalid data is a SerializationException`(
        case: String,
        data: String,
    ) {
        withClue(case) { shouldThrow<SerializationException> { decode(data) } }
    }

    @Test
    fun `encoding writes the documented layout`() {
        val element =
            json.encodeToJsonElement(
                ProjectListSerializer,
                listOf(aProject(id = "p1", dir = Path.of("/Users/test/alpha"), displayName = "Alpha")),
            )

        element shouldBe
            JsonObject(
                mapOf(
                    "projects" to
                        JsonArray(
                            listOf(
                                JsonObject(
                                    mapOf(
                                        "id" to JsonPrimitive("p1"),
                                        "dir" to JsonPrimitive("/Users/test/alpha"),
                                        "displayName" to JsonPrimitive("Alpha"),
                                    ),
                                ),
                            ),
                        ),
                ),
            )
    }

    @Test
    fun `encoding an empty list writes an empty project array`() {
        json.encodeToString(ProjectListSerializer, emptyList()) shouldBe """{"projects":[]}"""
    }

    @Test
    fun `encode then decode keeps a list with emoji and spaces in names`() {
        // ASCII directories only: a non-UTF-8 sun.jnu.encoding cannot hold emoji in a Path (plan, risks).
        val projects =
            listOf(
                aProject(
                    id = "3f6c1d2e-uuid",
                    dir = Path.of("/Users/test/my project"),
                    displayName = "  My 🚀 project",
                ),
                aProject(id = "p2", dir = Path.of("/Users/test/b"), displayName = "Zażółć gęślą jaźń"),
            )

        decode(json.encodeToString(ProjectListSerializer, projects)) shouldContainExactly projects
    }

    companion object {
        private fun entry(
            id: String = "\"p1\"",
            dir: String = "\"/a\"",
            displayName: String = "\"A\"",
        ): String = """{"id": $id, "dir": $dir, "displayName": $displayName}"""

        private fun projects(vararg entries: String): String = """{"projects": [${entries.joinToString()}]}"""

        @JvmStatic
        fun invalidData(): List<Arguments> =
            listOf(
                arguments("array instead of an object", "[]"),
                arguments("missing projects", "{}"),
                arguments("projects is not an array", """{"projects": {}}"""),
                arguments("projects is a string", """{"projects": "x"}"""),
                arguments("entry is not an object", """{"projects": ["p1"]}"""),
                arguments("missing id", """{"projects": [{"dir": "/a", "displayName": "A"}]}"""),
                arguments("empty id", projects(entry(id = "\"\""))),
                arguments("missing dir", """{"projects": [{"id": "p1", "displayName": "A"}]}"""),
                arguments("relative dir", projects(entry(dir = "\"rel/a\""))),
                arguments("unparseable dir", projects(entry(dir = "\"/a\\u0000b\""))),
                arguments("missing displayName", """{"projects": [{"id": "p1", "dir": "/a"}]}"""),
                arguments("blank displayName", projects(entry(displayName = "\" \""))),
                arguments("repeated id", projects(entry(dir = "\"/a\""), entry(dir = "\"/b\""))),
                arguments("repeated dir", projects(entry(id = "\"p1\""), entry(id = "\"p2\""))),
            )
    }
}
