package dev.quayside.app

import dev.quayside.terminal.api.ShellProfile
import dev.quayside.terminal.api.StartDirectory
import dev.quayside.terminal.api.TerminalSettings
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `saved settings reach the terminal with typography font`() =
        runTest {
            val source = MutableStateFlow(TerminalSettings(fontSize = 13))
            val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

            val mapped = typographySettings(source, "IBM Plex Mono", scope)

            mapped.value shouldBe TerminalSettings(fontSize = 13, fontFamily = "IBM Plex Mono")

            source.value = TerminalSettings(fontSize = 20)
            mapped.value shouldBe TerminalSettings(fontSize = 20, fontFamily = "IBM Plex Mono")

            source.value = TerminalSettings(fontSize = 20, fontFamily = "Menlo")
            mapped.value shouldBe TerminalSettings(fontSize = 20, fontFamily = "Menlo")
            scope.cancel()
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
