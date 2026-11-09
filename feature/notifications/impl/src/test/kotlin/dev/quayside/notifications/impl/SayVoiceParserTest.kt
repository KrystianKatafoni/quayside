package dev.quayside.notifications.impl

import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceOption
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

/** `parseSayVoices` (plan AM-704, table 3): pure, one voice per line of `say -v ?`. */
class SayVoiceParserTest {
    private fun voice(
        name: String,
        locale: String,
        sample: String = "",
    ) = VoiceOption(VoiceId(name), locale, sample)

    @ParameterizedTest(name = "{0}")
    @MethodSource("sayLines")
    fun `parses a say line`(
        line: String,
        name: String,
        locale: String,
    ) {
        parseSayVoices(line).map { it.id.name to it.locale } shouldBe listOf(name to locale)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sampleLines")
    fun `keeps the text after the first hash as the sample`(
        line: String,
        sample: String,
    ) {
        parseSayVoices(line).single().sample shouldBe sample
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(
        strings = [
            "",
            "just some text",
            "Zosia               pl_PL    Witaj, nie ma kratki",
            "Zosia               #  Witaj",
            "   en_US    # x",
        ],
    )
    fun `skips a line it does not understand`(line: String) {
        parseSayVoices(line) shouldBe emptyList()
    }

    @Test
    fun `a bad line does not stop the good ones`() {
        val output = "garbage\nZosia pl_PL # Witaj\n\n   en_US # x\nAlex en_US # Hi\n"

        parseSayVoices(output) shouldBe listOf(voice("Alex", "en_US", "Hi"), voice("Zosia", "pl_PL", "Witaj"))
    }

    @Test
    fun `sorts by name and drops duplicates`() {
        parseSayVoices("bob pl_PL # a\nCarl en_US # b\nAnna de_DE # c") shouldBe
            listOf(voice("Anna", "de_DE", "c"), voice("bob", "pl_PL", "a"), voice("Carl", "en_US", "b"))
        parseSayVoices("Eddy de_DE # a\nEddy pl_PL # b") shouldBe listOf(voice("Eddy", "de_DE", "a"))
    }

    @Test
    fun `the sample stays with the first voice of a name`() {
        val voices = parseSayVoices("Eddy de_DE # a\nEddy pl_PL # b")

        voices.single().sample shouldBe "a"
        voices.single().locale shouldBe "de_DE"
    }

    @Test
    fun `handles windows line ends`() {
        parseSayVoices("Zosia pl_PL # a\r\nAlex en_US # b\r\n") shouldBe
            listOf(voice("Alex", "en_US", "b"), voice("Zosia", "pl_PL", "a"))
    }

    companion object {
        @JvmStatic
        fun sayLines(): List<Arguments> =
            listOf(
                arguments("Zosia               pl_PL    # Witaj, nazywam się Zosia.", "Zosia", "pl_PL"),
                arguments("Bad News            en_US    # Hello #1 here", "Bad News", "en_US"),
                arguments("Eddy (polski (Polska)) pl_PL    # Witaj", "Eddy (polski (Polska))", "pl_PL"),
                arguments("Tingting            zh_CN    # 你好", "Tingting", "zh_CN"),
                arguments("Paulina             es_419   # Hola", "Paulina", "es_419"),
            )

        @JvmStatic
        fun sampleLines(): List<Arguments> =
            listOf(
                arguments("Zosia               pl_PL    # Witaj, nazywam się Zosia.", "Witaj, nazywam się Zosia."),
                arguments("Bad News            en_US    # Hello #1 here", "Hello #1 here"),
                arguments("Eddy (polski (Polska)) pl_PL    #", ""),
                arguments("Tingting            zh_CN    #   你好  ", "你好"),
            )
    }
}
