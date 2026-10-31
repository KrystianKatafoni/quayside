package dev.quayside.terminal.impl

import dev.quayside.terminal.api.ShellProfile
import dev.quayside.terminal.api.StartDirectory
import dev.quayside.terminal.api.TerminalSettings
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * `data` of `terminal.json` (ADR 0016, table 4 of docs/plans/AM-508.md). A plain [Json] without
 * `ignoreUnknownKeys`: the serializer itself must skip unknown keys and fix single bad fields.
 */
class TerminalSettingsSerializerTest {
    private fun decode(data: String): TerminalSettings = Json.decodeFromString(TerminalSettingsSerializer, data)

    @ParameterizedTest(name = "{0}")
    @MethodSource("decodings")
    fun `decoding follows table 4`(
        @Suppress("UNUSED_PARAMETER") name: String,
        data: String,
        expected: TerminalSettings,
    ) {
        decode(data) shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notObjects")
    fun `data that is not an object is a SerializationException`(
        case: String,
        data: String,
    ) {
        withClue(case) { shouldThrow<SerializationException> { decode(data) } }
    }

    @Test
    fun `encoding writes the flat layout of ADR 0016`() {
        val element = Json.encodeToJsonElement(TerminalSettingsSerializer, TerminalSettings())

        element shouldBe
            JsonObject(
                mapOf(
                    "shell" to JsonPrimitive("LOGIN_DEFAULT"),
                    "startDirectory" to JsonPrimitive("SESSION_WORKTREE"),
                    "fontFamily" to JsonNull,
                    "fontSize" to JsonPrimitive(13),
                    "scrollbackLines" to JsonPrimitive(100_000),
                    "ligatures" to JsonPrimitive(true),
                    "optionAsMeta" to JsonPrimitive(false),
                ),
            )
    }

    @Test
    fun `encode then decode keeps every field`() {
        val custom =
            TerminalSettings(
                shell = ShellProfile.FISH,
                startDirectory = StartDirectory.HOME,
                fontFamily = "JetBrains Mono",
                fontSize = 15,
                scrollbackLines = 2_000,
                ligatures = false,
                optionAsMeta = true,
            )

        decode(Json.encodeToString(TerminalSettingsSerializer, custom)) shouldBe custom
    }

    companion object {
        private val full =
            TerminalSettings(
                shell = ShellProfile.BASH,
                startDirectory = StartDirectory.PROJECT,
                fontFamily = "Menlo",
                fontSize = 16,
                scrollbackLines = 20_000,
                ligatures = false,
                optionAsMeta = true,
            )

        @JvmStatic
        @Suppress("LongMethod")
        fun decodings(): List<Arguments> =
            listOf(
                arguments(
                    "full object",
                    """{"shell":"BASH","startDirectory":"PROJECT","fontFamily":"Menlo","fontSize":16,
                        "scrollbackLines":20000,"ligatures":false,"optionAsMeta":true}""",
                    full,
                ),
                arguments("empty object gives the defaults", "{}", TerminalSettings()),
                arguments(
                    "unknown key is ignored",
                    """{"fontSize":20,"color":"red"}""",
                    TerminalSettings(fontSize = 20),
                ),
                arguments(
                    "fontSize as a string",
                    """{"fontSize":"13","ligatures":false}""",
                    TerminalSettings(ligatures = false),
                ),
                arguments("unknown shell", """{"shell":"TCSH","fontSize":20}""", TerminalSettings(fontSize = 20)),
                arguments(
                    "scrollbackLines below the range",
                    """{"scrollbackLines":5,"fontSize":20}""",
                    TerminalSettings(fontSize = 20),
                ),
                arguments("fontSize above the range", """{"fontSize":99}""", TerminalSettings()),
                arguments("fontFamily of the wrong type", """{"fontFamily":12}""", TerminalSettings()),
                arguments("fontFamily null", """{"fontFamily":null}""", TerminalSettings()),
                arguments("ligatures of the wrong type", """{"ligatures":"yes"}""", TerminalSettings()),
                arguments("optionAsMeta missing stays off", """{"fontSize":20}""", TerminalSettings(fontSize = 20)),
            )

        @JvmStatic
        fun notObjects(): List<Arguments> =
            listOf(
                arguments("array", "[]"),
                arguments("number", "13"),
                arguments("string", "\"x\""),
                arguments("null", "null"),
            )
    }
}
