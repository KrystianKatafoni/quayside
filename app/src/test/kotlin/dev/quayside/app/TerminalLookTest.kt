package dev.quayside.app

import dev.quayside.terminal.api.ShellProfile
import dev.quayside.terminal.api.StartDirectory
import dev.quayside.terminal.api.TerminalSettings
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** `fontFamily == null` means "the font of the typography" (AM-807, table 2); every other setting is untouched. */
class TerminalLookTest {
    /** Every field differs from the default, so a result built from defaults is seen. */
    private val custom =
        TerminalSettings(
            shell = ShellProfile.FISH,
            startDirectory = StartDirectory.HOME,
            fontFamily = null,
            fontSize = 17,
            scrollbackLines = 4_321,
            ligatures = false,
            optionAsMeta = true,
            bellNotification = false,
        )

    @ParameterizedTest(name = "own {0} with typography {1} gives {2}")
    @MethodSource("fonts")
    fun `typography font fills an empty font and own font wins`(
        own: String?,
        typography: String,
        expected: String,
    ) {
        TerminalSettings(fontFamily = own).withTypographyFont(typography).fontFamily shouldBe expected
    }

    @Test
    fun `other settings are kept`() {
        custom.withTypographyFont("IBM Plex Mono") shouldBe custom.copy(fontFamily = "IBM Plex Mono")
        custom.copy(fontFamily = "Menlo").withTypographyFont("IBM Plex Mono") shouldBe custom.copy(fontFamily = "Menlo")
    }

    companion object {
        @JvmStatic
        fun fonts(): Stream<Arguments> =
            Stream.of(
                Arguments.of(null, "IBM Plex Mono", "IBM Plex Mono"),
                Arguments.of("Menlo", "IBM Plex Mono", "Menlo"),
                Arguments.of(null, "Menlo", "Menlo"),
            )
    }
}
