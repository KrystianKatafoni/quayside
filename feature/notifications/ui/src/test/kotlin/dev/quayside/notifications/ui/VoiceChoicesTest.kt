package dev.quayside.notifications.ui

import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceOption
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
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

    companion object {
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
