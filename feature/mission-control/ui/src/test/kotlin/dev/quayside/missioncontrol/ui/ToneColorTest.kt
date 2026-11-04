package dev.quayside.missioncontrol.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** Tones of Mission Control take their colours from the tokens (docs/plans/AM-806.md, table 1). */
class ToneColorTest {
    private val tokens = SampleTokens.colors()

    @Test
    fun `source tokens differ so that a wrong token is noticed`() {
        val sources = listOf(tokens.status.warn, tokens.status.work, tokens.status.ok, tokens.status.err)
        (sources + tokens.content.muted + tokens.content.accent).toSet().size shouldBe 6
    }

    @ParameterizedTest(name = "{0} is drawn in {1}")
    @MethodSource("table")
    internal fun `tone is drawn in the token of its meaning`(
        tone: Tone,
        token: String,
    ) {
        tone.color(tokens) shouldBe pick(tokens, token)
    }

    @ParameterizedTest(name = "{0} tints with {1}")
    @MethodSource("table")
    @OptIn(ExperimentalTestApi::class)
    internal fun `tint reads the token colour of the tone from the theme`(
        tone: Tone,
        token: String,
    ) = runComposeUiTest {
        // Given content under the plex theme, when it asks for the tint of the tone
        var tint: Color? = null
        setContent { AmTestTheme { tint = tone.tint() } }

        // Then it gets the colour of the token of that tone
        tint shouldBe pick(tokens, token).toColor()
    }

    companion object {
        private fun pick(
            tokens: ColorTokens,
            name: String,
        ): Rgba =
            when (name) {
                "warn" -> tokens.status.warn
                "work" -> tokens.status.work
                "ok" -> tokens.status.ok
                "err" -> tokens.status.err
                else -> tokens.content.muted
            }

        @JvmStatic
        fun table(): Stream<Arguments> =
            Stream.of(
                Arguments.of(Tone.WARN, "warn"),
                Arguments.of(Tone.WORK, "work"),
                Arguments.of(Tone.OK, "ok"),
                Arguments.of(Tone.ERR, "err"),
                Arguments.of(Tone.DIM, "muted"),
            )
    }
}
