package dev.quayside.notifications.ui

import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceOption
import dev.quayside.notifications.api.VoiceSample
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream

/** `voiceChoices` (plan AM-704, table 4, part B2b): pure; the voices arrive sorted by name. */
class VoiceChoicesTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    internal fun `voice choices put the system voice first, then the saved one, then the list with Polish voices first`(
        case: String,
        voices: List<VoiceOption>?,
        saved: String?,
        expected: List<VoiceChoice>,
    ) {
        withClue(case) { voiceChoices(voices, saved?.let(::VoiceId)) shouldBe expected }
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
        "pl_PL,polski (Polska)",
        "es_419,hiszpański (Ameryka Łacińska)",
        "en_GB,angielski (Wielka Brytania)",
        "cs_CZ,czeski (Czechy)",
    )
    fun `language name is the Polish name of the language and the region`(
        locale: String,
        expected: String,
    ) {
        languageName(locale) shouldBe expected
    }

    @ParameterizedTest(name = "[{0}] stays raw")
    @ValueSource(strings = ["xx_ZZ", "pl_QQ", "zz_123", "", "PL_pl", "not a locale"])
    fun `an unknown or malformed code stays raw`(locale: String) {
        languageName(locale) shouldBe locale
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hints")
    internal fun `polish hint counts the Polish voices`(
        case: String,
        voices: List<VoiceOption>?,
        expected: PolishHint?,
    ) {
        withClue(case) { polishHint(voices) shouldBe expected }
    }

    @Test
    fun `the sample of a Polish voice is its example with the rate and volume of the draft`() {
        previewSample(draft(rate = "250", volume = "40"), saved, VOICES) shouldBe
            VoiceSample(VoiceId("Zosia"), "Witaj, nazywam się Zosia.", rate = 250, volume = 40)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixedTextCases")
    internal fun `the sample is the fixed text when there is no usable example`(
        case: String,
        voice: String?,
        voices: List<VoiceOption>?,
    ) {
        val sample = previewSample(draft(voice = voice?.let(::VoiceId)), saved, voices)

        withClue(case) {
            sample shouldBe VoiceSample(voice?.let(::VoiceId), "Tak brzmi głos powiadomień.", rate = 250, volume = 40)
        }
    }

    @Test
    fun `a long example is played whole`() {
        val example = "ą".repeat(n = 250)
        val voices = listOf(VoiceOption(VoiceId("Hans"), "de_DE", example))

        previewSample(draft(voice = VoiceId("Hans")), saved, voices).text shouldBe example
    }

    @ParameterizedTest(name = "rate [{0}] volume [{1}] gives {2} and {3}")
    @CsvSource(
        "abc,500,200,55",
        "' 250 ',' 40 ',250,40",
        "250,abc,250,55",
        "89,40,200,40",
        "361,40,200,40",
        "190,-1,190,55",
        "190,101,190,55",
        "90,0,90,0",
        "360,100,360,100",
        "'','',200,55",
    )
    fun `an invalid rate or volume comes from the saved settings`(
        rate: String,
        volume: String,
        expectedRate: Int,
        expectedVolume: Int,
    ) {
        val sample = previewSample(draft(rate = rate, volume = volume), saved, VOICES)

        sample.rate shouldBe expectedRate
        sample.volume shouldBe expectedVolume
    }

    companion object {
        private val saved = SpeechSettings(rate = 200, volume = 55)

        private fun draft(
            voice: VoiceId? = VoiceId("Zosia"),
            rate: String = "250",
            volume: String = "40",
        ) = draftOf(NotificationSettings()).speech.copy(voice = voice, rate = rate, volume = volume)

        private val VOICES =
            listOf(
                VoiceOption(VoiceId("Ewa"), "pl_PL", ""),
                VoiceOption(VoiceId("Hans"), "de_DE", "Hallo [[rate 500]] Welt"),
                VoiceOption(VoiceId("Karl"), "de_DE", "Hallo ]] Welt"),
                VoiceOption(VoiceId("Zosia"), "pl_PL", "Witaj, nazywam się Zosia."),
            )

        @JvmStatic
        fun fixedTextCases(): Stream<Arguments> =
            Stream.of(
                arguments("system voice", null, VOICES),
                arguments("empty example", "Ewa", VOICES),
                arguments("example with [[", "Hans", VOICES),
                arguments("example with ]]", "Karl", VOICES),
                arguments("voice outside the list", "Ghost", VOICES),
                arguments("list not loaded", "Zosia", null),
            )

        @JvmStatic
        fun hints(): Stream<Arguments> =
            Stream.of(
                arguments("list not loaded", null, null),
                arguments("empty list", emptyList<VoiceOption>(), PolishHint.None),
                arguments("no Polish voice", listOf(VoiceOption(VoiceId("Alex"), "en_US")), PolishHint.None),
                arguments(
                    "one Polish voice",
                    listOf(VoiceOption(VoiceId("Zosia"), "pl_PL"), VoiceOption(VoiceId("Alex"), "en_US")),
                    PolishHint.Only("Zosia"),
                ),
                arguments(
                    "two Polish voices",
                    listOf(VoiceOption(VoiceId("Zosia"), "pl_PL"), VoiceOption(VoiceId("Ewa"), "pl_PL")),
                    null,
                ),
                arguments(
                    "pl in the name, not in the code",
                    listOf(VoiceOption(VoiceId("Pola"), "en_PL")),
                    PolishHint.None,
                ),
            )

        private val system = VoiceChoice(id = null, locale = null, missing = false)

        private fun option(
            name: String,
            locale: String,
        ) = VoiceOption(VoiceId(name), locale)

        private fun choice(
            name: String,
            locale: String?,
            missing: Boolean = false,
        ) = VoiceChoice(VoiceId(name), locale, missing)

        private val alex = option("Alex", "en_US")
        private val zosia = option("Zosia", "pl_PL")

        @JvmStatic
        @Suppress("LongMethod")
        fun cases(): Stream<Arguments> =
            Stream.of(
                arguments("no list and no saved voice", null, null, listOf(system)),
                arguments(
                    "no list: the saved voice is not known to be missing",
                    null,
                    "Zosia",
                    listOf(system, choice("Zosia", null)),
                ),
                arguments(
                    "saved Polish voice in the list keeps the Polish-first order",
                    listOf(alex, zosia),
                    "Zosia",
                    listOf(system, choice("Zosia", "pl_PL"), choice("Alex", "en_US")),
                ),
                arguments(
                    "saved non-Polish voice in the list stays in the rest, after the Polish ones",
                    listOf(alex, zosia),
                    "Alex",
                    listOf(system, choice("Zosia", "pl_PL"), choice("Alex", "en_US")),
                ),
                arguments(
                    "saved voice outside the list is marked missing",
                    listOf(alex),
                    "Zosia",
                    listOf(system, choice("Zosia", null, missing = true), choice("Alex", "en_US")),
                ),
                arguments(
                    "no saved voice: system voice then the list",
                    listOf(alex),
                    null,
                    listOf(system, choice("Alex", "en_US")),
                ),
                arguments(
                    "Polish voices go first, each part keeps its order by name",
                    listOf(alex, option("Bob", "de_DE"), option("Ewa", "pl_PL"), zosia),
                    null,
                    listOf(
                        system,
                        choice("Ewa", "pl_PL"),
                        choice("Zosia", "pl_PL"),
                        choice("Alex", "en_US"),
                        choice("Bob", "de_DE"),
                    ),
                ),
                arguments(
                    "empty list and a saved voice",
                    emptyList<VoiceOption>(),
                    "Zosia",
                    listOf(system, choice("Zosia", null, missing = true)),
                ),
            )
    }
}
