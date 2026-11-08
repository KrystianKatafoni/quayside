package dev.quayside.projects.impl

import dev.quayside.projects.api.Project
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/**
 * The optional `subfolders` key of an entry in `projects.json` (ADR 0026, FR-P7). The serializer does not look at
 * the disk: a name of a directory that is gone is still a valid entry.
 */
class ProjectListSerializerSubfoldersTest {
    private val json = Json

    private fun decode(data: String): List<Project> = json.decodeFromString(ProjectListSerializer, data)

    private fun entry(subfolders: String?): String {
        val key = subfolders?.let { """, "subfolders": $it""" }.orEmpty()
        return """{"projects": [{"id": "p1", "dir": "/a", "displayName": "A"$key}]}"""
    }

    private fun encodedEntry(project: Project): JsonObject =
        json
            .encodeToJsonElement(ProjectListSerializer, listOf(project))
            .jsonObject
            .getValue("projects")
            .jsonArray
            .single()
            .jsonObject

    @Test
    fun `entry without subfolders writes no subfolders key`() {
        encodedEntry(aProject(id = "p1", dir = Path.of("/a"), displayName = "A")).keys shouldBe
            setOf("id", "dir", "displayName")
    }

    @Test
    fun `entry with subfolders writes them as an array of names`() {
        val project = aProject(id = "p1", dir = Path.of("/a"), subfolders = subfolderNames("docs", "soft"))

        encodedEntry(project).getValue("subfolders") shouldBe
            JsonArray(listOf(JsonPrimitive("docs"), JsonPrimitive("soft")))
    }

    @Test
    fun `subfolders round trip`() {
        val projects =
            listOf(
                aProject(id = "p1", dir = Path.of("/a"), subfolders = subfolderNames("Anna", "bob", "Carl")),
                aProject(id = "p2", dir = Path.of("/b")),
                aProject(id = "p3", dir = Path.of("/c"), subfolders = subfolderNames("my project", "zażółć")),
            )

        decode(json.encodeToString(ProjectListSerializer, projects)) shouldContainExactly projects
    }

    @Test
    fun `subfolders are read sorted ignoring case`() {
        decode(entry("""["bob", "Anna", "Carl"]""")).single().subfolders shouldBe
            subfolderNames("Anna", "bob", "Carl")
    }

    @Test
    fun `names equal ignoring case are ordered by code point`() {
        decode(entry("""["b", "B", "a"]""")).single().subfolders shouldBe subfolderNames("a", "B", "b")
    }

    @Test
    fun `entry without the key and an empty array both mean no group`() {
        decode(entry(null)).single().subfolders shouldBe emptyList()
        decode(entry("[]")).single().subfolders shouldBe emptyList()
    }

    @Test
    fun `a subfolder that does not exist on disk is still a valid entry`() {
        decode(entry("""["no-such-directory-anywhere"]""")).single().subfolders shouldBe
            subfolderNames("no-such-directory-anywhere")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSubfolders")
    fun `invalid subfolders are a SerializationException`(
        case: String,
        subfolders: String,
    ) {
        withClue(case) { shouldThrow<SerializationException> { decode(entry(subfolders)) } }
    }

    @Test
    fun `blank or repeated subfolder marks the file broken`() {
        shouldThrow<SerializationException> { decode(entry("""["soft", " "]""")) }
        shouldThrow<SerializationException> { decode(entry("""["soft", "docs", "soft"]""")) }
    }

    @Test
    fun `the error never quotes a subfolder name`() {
        val thrown = shouldThrow<SerializationException> { decode(entry("""["jane-secret", "jane-secret"]""")) }

        thrown.message.orEmpty() shouldNotContain "jane-secret"
    }

    companion object {
        @JvmStatic
        fun invalidSubfolders(): List<Arguments> =
            listOf(
                arguments("a string instead of an array", "\"soft\""),
                arguments("an object", "{}"),
                arguments("null", "null"),
                arguments("a number element", "[1]"),
                arguments("a null element", "[null]"),
                arguments("an object element", "[{}]"),
                arguments("an array element", "[[\"soft\"]]"),
                arguments("an empty name", "[\"\"]"),
                arguments("a blank name", "[\"   \"]"),
                arguments("a name with a slash", "[\"a/b\"]"),
                arguments("only a slash", "[\"/\"]"),
                arguments("a dot", "[\".\"]"),
                arguments("two dots", "[\"..\"]"),
                arguments("a leading dot", "[\".git\"]"),
                arguments("a repeated name", "[\"soft\", \"soft\"]"),
            )
    }
}
