package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.DayNight
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.UiScale
import dev.quayside.core.designtokens.TypographyId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** Reading and writing the flat `data` object of theme.json (ADR 0023, plan AM-804, table "Testy"). */
class ThemeSettingsCodecTest {
    private val catalog = builtInCatalog()

    private fun decode(json: String): DecodedSettings =
        decodeSettings(Json.parseToJsonElement(json).jsonObject, catalog)

    @ParameterizedTest(name = "{0}")
    @MethodSource("readCases")
    fun `decode takes valid fields, defaults the rest and names the invalid keys`(
        name: String,
        json: String,
        expected: AppearanceSettings,
        invalidKeys: String,
    ) {
        val decoded = decode(json)

        withClue(name) {
            decoded.settings shouldBe expected
            decoded.invalid shouldBe invalidKeys.split(",").filter { it.isNotEmpty() }.toSet()
        }
    }

    @Test
    fun `decode never throws for any value type of a known key`() {
        val keys = listOf("scheme", "typography", "uiScale", "density", "followSystem", "dayScheme", "nightScheme")
        val values = listOf("null", "5", "1.5", "true", "[]", "{}", "\"\"", "\"???\"")

        keys.forEach { key ->
            values.forEach { value ->
                val decoded = decode("""{"$key": $value}""")

                (decoded.invalid.size <= 1) shouldBe true
            }
        }
    }

    private val custom =
        AppearanceSettings(
            scheme = SchemeId("graphite"),
            typography = TypographyId("geist"),
            uiScale = UiScale.L,
            density = ListDensity.COMPACT,
            dayNight = DayNight(enabled = true, day = SchemeId("sand"), night = SchemeId("amber")),
        )

    @Test
    fun `encode writes every field under its theme json name`() {
        encodeSettings(custom) shouldBe
            buildJsonObject {
                put("scheme", JsonPrimitive("graphite"))
                put("typography", JsonPrimitive("geist"))
                put("uiScale", JsonPrimitive("L"))
                put("density", JsonPrimitive("COMPACT"))
                put("followSystem", JsonPrimitive(true))
                put("dayScheme", JsonPrimitive("sand"))
                put("nightScheme", JsonPrimitive("amber"))
            }
    }

    @Test
    fun `encode also writes the fields that have default values`() {
        val encoded = encodeSettings(AppearanceSettings())

        encoded.keys shouldBe
            setOf("scheme", "typography", "uiScale", "density", "followSystem", "dayScheme", "nightScheme")
        encoded["followSystem"] shouldBe JsonPrimitive(false)
        encoded["dayScheme"] shouldBe JsonPrimitive("paper")
        encoded["nightScheme"] shouldBe JsonPrimitive("plex")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roundTrips")
    fun `each field survives a round trip`(
        name: String,
        settings: AppearanceSettings,
    ) {
        val decoded = decodeSettings(encodeSettings(settings), catalog)

        withClue(name) {
            decoded.settings shouldBe settings
            decoded.invalid shouldBe emptySet()
            encodeSettings(settings) shouldNotBe encodeSettings(AppearanceSettings())
        }
    }

    @Test
    fun `serializer reads the same as decode`() {
        val element = Json.parseToJsonElement("""{"scheme":"graphite","uiScale":"XL","density":"COMPACT"}""")

        val read = Json.decodeFromJsonElement(ThemeSettingsSerializer(catalog), element)

        read shouldBe AppearanceSettings(scheme = SchemeId("graphite"), density = ListDensity.COMPACT)
    }

    @Test
    fun `serializer writes the same as encode`() {
        Json.encodeToJsonElement(ThemeSettingsSerializer(catalog), custom) shouldBe encodeSettings(custom)
    }

    @Test
    fun `serializer rejects anything that is not an object without quoting it`() {
        val failure =
            shouldThrow<SerializationException> {
                Json.decodeFromJsonElement(ThemeSettingsSerializer(catalog), Json.parseToJsonElement("""["SECRET"]"""))
            }

        failure.message.orEmpty() shouldNotContain "SECRET"
    }

    companion object {
        private val dflt = AppearanceSettings()

        @JvmStatic
        @Suppress("LongMethod") // a table of cases
        fun readCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "full object",
                    """{"scheme":"graphite","typography":"geist","uiScale":"L","density":"COMPACT",
                        |"followSystem":true,"dayScheme":"sand","nightScheme":"amber"}
                    """.trimMargin(),
                    AppearanceSettings(
                        SchemeId("graphite"),
                        TypographyId("geist"),
                        UiScale.L,
                        ListDensity.COMPACT,
                        DayNight(true, SchemeId("sand"), SchemeId("amber")),
                    ),
                    "",
                ),
                Arguments.of("empty object", "{}", dflt, ""),
                Arguments.of("unknown scheme id", """{"scheme":"nie-ma"}""", dflt, "scheme"),
                Arguments.of("scheme in a bad format", """{"scheme":"Plex!"}""", dflt, "scheme"),
                Arguments.of("scheme of a wrong type", """{"scheme":5}""", dflt, "scheme"),
                Arguments.of("unknown typography id", """{"typography":"nope"}""", dflt, "typography"),
                Arguments.of("followSystem as a string", """{"followSystem":"true"}""", dflt, "followSystem"),
                Arguments.of("followSystem as a number", """{"followSystem":1}""", dflt, "followSystem"),
                Arguments.of("unknown uiScale", """{"uiScale":"XL"}""", dflt, "uiScale"),
                Arguments.of("uiScale in lower case", """{"uiScale":"l"}""", dflt, "uiScale"),
                Arguments.of("density as a number", """{"density":1}""", dflt, "density"),
                Arguments.of("unknown density", """{"density":"TIGHT"}""", dflt, "density"),
                Arguments.of("unknown key is ignored", """{"terminal":{"size":14},"extra":1}""", dflt, ""),
                Arguments.of(
                    "dayScheme outside the catalog",
                    """{"dayScheme":"ghost"}""",
                    dflt,
                    "dayScheme",
                ),
                Arguments.of(
                    "nightScheme outside the catalog",
                    """{"nightScheme":"ghost"}""",
                    dflt,
                    "nightScheme",
                ),
                Arguments.of(
                    "one bad field does not spoil the valid ones",
                    """{"scheme":"nie-ma","typography":"geist","uiScale":"XL",
                        |"dayScheme":"sand","density":"COMPACT"}
                    """.trimMargin(),
                    AppearanceSettings(
                        typography = TypographyId("geist"),
                        density = ListDensity.COMPACT,
                        dayNight = DayNight(day = SchemeId("sand")),
                    ),
                    "scheme,uiScale",
                ),
            )

        @JvmStatic
        fun roundTrips(): Stream<Arguments> =
            Stream.of(
                Arguments.of("scheme", dflt.copy(scheme = SchemeId("graphite"))),
                Arguments.of("typography", dflt.copy(typography = TypographyId("jetbrains"))),
                Arguments.of("uiScale", dflt.copy(uiScale = UiScale.S)),
                Arguments.of("density", dflt.copy(density = ListDensity.COMPACT)),
                Arguments.of("followSystem", dflt.copy(dayNight = DayNight(enabled = true))),
                Arguments.of("dayScheme", dflt.copy(dayNight = DayNight(day = SchemeId("sand")))),
                Arguments.of("nightScheme", dflt.copy(dayNight = DayNight(night = SchemeId("amber")))),
            )
    }
}
