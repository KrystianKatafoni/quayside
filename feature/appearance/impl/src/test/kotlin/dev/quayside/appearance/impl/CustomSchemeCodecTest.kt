package dev.quayside.appearance.impl

import dev.quayside.appearance.api.SampleSchemes
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeMode
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class CustomSchemeCodecTest {
    private val mine = SampleSchemes.mine()

    private fun edited(change: (MutableMap<String, JsonElement>) -> Unit): JsonObject {
        val map = encodeScheme(mine).toMutableMap()
        change(map)
        return JsonObject(map)
    }

    @Test
    fun `scheme survives encode and parse`() {
        parseScheme(encodeScheme(mine)) shouldBe mine
    }

    @Test
    fun `encoded scheme lists key, name, mode, radius and eight colours`() {
        val obj = encodeScheme(mine)

        obj["key"] shouldBe JsonPrimitive("mine")
        obj["name"] shouldBe JsonPrimitive("Mine")
        obj["mode"] shouldBe JsonPrimitive("DARK")
        obj["radius"] shouldBe JsonPrimitive(6)
        (obj["colors"] as JsonObject).keys shouldBe
            setOf("bg", "panel", "text", "accent", "warn", "ok", "err", "work")
        (obj["colors"] as JsonObject)["text"] shouldBe JsonPrimitive("#EDEDED")
    }

    @Test
    fun `unknown keys are ignored`() {
        parseScheme(edited { it["extra"] = JsonPrimitive("x") }) shouldBe mine
    }

    @Test
    fun `every mode is read`() {
        ThemeMode.entries.forEach { mode ->
            parseScheme(edited { it["mode"] = JsonPrimitive(mode.name) })?.mode shouldBe mode
        }
    }

    @Test
    fun `radius at both ends of the range is read`() {
        parseScheme(edited { it["radius"] = JsonPrimitive(0) })?.radius shouldBe 0
        parseScheme(edited { it["radius"] = JsonPrimitive(24) })?.radius shouldBe 24
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSchemes")
    fun `invalid scheme is null`(
        @Suppress("UnusedParameter") label: String,
        element: JsonElement,
    ) {
        parseScheme(element) shouldBe null
    }

    @Test
    fun `missing customSchemes key gives none and is not invalid`() {
        decodeSchemes(buildJsonObject {}) shouldBe DecodedSchemes(emptyList(), invalid = false)
    }

    @Test
    fun `valid schemes are read in order`() {
        val other = mine.copy(id = SchemeId("other"))
        val data = buildJsonObject { put("customSchemes", encodeSchemes(listOf(mine, other))) }

        decodeSchemes(data) shouldBe DecodedSchemes(listOf(mine, other), invalid = false)
    }

    @Test
    fun `invalid element is skipped and reported`() {
        val data =
            buildJsonObject { put("customSchemes", JsonArray(listOf(JsonPrimitive(1), encodeScheme(mine)))) }

        decodeSchemes(data) shouldBe DecodedSchemes(listOf(mine), invalid = true)
    }

    @Test
    fun `customSchemes that is not an array is reported`() {
        decodeSchemes(buildJsonObject { put("customSchemes", JsonPrimitive("x")) }) shouldBe
            DecodedSchemes(emptyList(), invalid = true)
    }

    companion object {
        private val valid = encodeScheme(SampleSchemes.mine())

        private fun without(key: String): JsonObject = JsonObject(valid.filterKeys { it != key })

        private fun with(
            key: String,
            value: JsonElement,
        ): JsonObject = JsonObject(valid + (key to value))

        private fun color(
            column: String,
            value: JsonElement?,
        ): JsonObject {
            val colors = (valid["colors"] as JsonObject).toMutableMap()
            if (value == null) colors.remove(column) else colors[column] = value
            return with("colors", JsonObject(colors))
        }

        @JvmStatic
        @Suppress("LongMethod")
        fun invalidSchemes(): Stream<Arguments> =
            Stream.of(
                Arguments.of("not an object", JsonPrimitive("x")),
                Arguments.of("no key", without("key")),
                Arguments.of("key with upper case", with("key", JsonPrimitive("Mine"))),
                Arguments.of("empty key", with("key", JsonPrimitive(""))),
                Arguments.of("key that is a number", with("key", JsonPrimitive(5))),
                Arguments.of("no name", without("name")),
                Arguments.of("blank name", with("name", JsonPrimitive(" "))),
                Arguments.of("name of 41 characters", with("name", JsonPrimitive("x".repeat(41)))),
                Arguments.of("unknown mode", with("mode", JsonPrimitive("SEPIA"))),
                Arguments.of("no radius", without("radius")),
                Arguments.of("radius as text", with("radius", JsonPrimitive("6"))),
                Arguments.of("negative radius", with("radius", JsonPrimitive(-1))),
                Arguments.of("radius 25", with("radius", JsonPrimitive(25))),
                Arguments.of("no colours", without("colors")),
                Arguments.of("colours that are not an object", with("colors", JsonPrimitive("x"))),
                Arguments.of("missing colour", color("work", null)),
                Arguments.of("colour with alpha", color("bg", JsonPrimitive("#11111180"))),
                Arguments.of("colour without hash", color("bg", JsonPrimitive("111111"))),
                Arguments.of("colour that is a number", color("bg", JsonPrimitive(1))),
            )
    }
}
