package dev.quayside.terminal.impl.replyspike

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

private val SGR = Regex("\u001b\\[[0-9;]*m")
private val HOME_AND_CLEAR = Regex("\u001b\\[2J\u001b\\[H")

/**
 * Rows of a recorded frame. The frames of `reply-spike/` only use clear-screen, SGR colors and newlines, so the
 * rows are the plain lines. The end-to-end rendering through the emulator is covered by `HiddenReplyTest`.
 */
private fun framed(name: String): List<String> =
    replySpikeResource(name).replace(HOME_AND_CLEAR, "").replace(SGR, "").lines()

/** Frame recorded from the real TUI (anonymized), plain text. */
private fun plain(name: String): List<String> = replySpikeResource(name).lines()

/** Table 2 of the AM-507 plan. */
internal class ScreenClassifierTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("frames")
    @Suppress("UNUSED_PARAMETER")
    fun `classifies the frame`(
        name: String,
        rows: List<String>,
        expected: Prompt,
    ) {
        ScreenClassifier.classify(rows) shouldBe expected
    }

    @Test
    fun `trailing spaces do not matter for the markers`() {
        ScreenClassifier.classify(framed("options.ansi")) shouldBe
            ScreenClassifier.classify(framed("options.ansi").map { "$it  " })
    }

    companion object {
        @JvmStatic
        fun frames(): Stream<Arguments> =
            Stream.of(
                Arguments.of("text question with the input box", framed("text.ansi"), Prompt.Text),
                Arguments.of("numbered options with the select footer", framed("options.ansi"), Prompt.Options(3)),
                Arguments.of("permission for Bash", framed("permission.ansi"), Prompt.Permission("Bash")),
                Arguments.of("real TUI: idle input box", plain("real-text.txt"), Prompt.Text),
                Arguments.of("real TUI: options without its own rows", plain("real-options.txt"), Prompt.Options(3)),
                Arguments.of("real TUI: permission for Bash", plain("real-permission.txt"), Prompt.Permission("Bash")),
                Arguments.of("input box that holds the reply", framed("text-filled.ansi"), Prompt.TextFilled),
                Arguments.of("empty input box with a spinner", framed("text-sent.ansi"), Prompt.Busy),
                Arguments.of("empty input box without a spinner", framed("text-sent-quiet.ansi"), Prompt.Text),
                Arguments.of("empty screen", emptyList<String>(), Prompt.None),
                Arguments.of("blank rows", List(40) { "" }, Prompt.None),
                Arguments.of("spinner only", listOf("", "✻ Thinking… (3s)", ""), Prompt.Busy),
                Arguments.of(
                    "numbered list without input box, footer or permission question",
                    listOf("Mam trzy pomysły:", "1. pierwszy", "2. drugi", "3. trzeci"),
                    Prompt.Unknown,
                ),
            )
    }
}
