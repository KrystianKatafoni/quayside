package dev.quayside.terminal.impl.replyspike

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

private const val ESC = "\u001b"
private val PASTE = KeyVariant(text = TextKeys.PASTE)
private val TYPE = KeyVariant(text = TextKeys.TYPE)
private val DIGIT = KeyVariant(choice = ChoiceKeys.DIGIT)
private val ARROWS = KeyVariant(choice = ChoiceKeys.ARROWS)

/** Table 1 of the AM-507 plan. */
internal class ReplyKeysTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    @Suppress("UNUSED_PARAMETER")
    fun `keys of a reply`(
        name: String,
        reply: Reply,
        variant: KeyVariant,
        expected: List<String>,
    ) {
        ReplyKeys.keys(reply, variant) shouldBe expected
    }

    @Test
    fun `option index below one is rejected`() {
        shouldThrow<IllegalArgumentException> { ReplyKeys.keys(Reply.Option(0), DIGIT) }
    }

    @Test
    fun `empty text is rejected`() {
        shouldThrow<IllegalArgumentException> { ReplyKeys.keys(Reply.Text(""), PASTE) }
    }

    companion object {
        @JvmStatic
        @Suppress("LongMethod")
        fun table(): Stream<Arguments> =
            Stream.of(
                Arguments.of("text pasted", Reply.Text("tak"), PASTE, listOf("$ESC[200~tak$ESC[201~", "\r")),
                Arguments.of("text typed", Reply.Text("tak"), TYPE, listOf("t", "a", "k", "\r")),
                Arguments.of(
                    "newline stays in a paste",
                    Reply.Text("a\nb"),
                    PASTE,
                    listOf("$ESC[200~a\nb$ESC[201~", "\r"),
                ),
                Arguments.of("newline typed as a space", Reply.Text("a\nb"), TYPE, listOf("a", " ", "b", "\r")),
                Arguments.of(
                    "ESC and CR cannot inject keys into a paste",
                    Reply.Text("x$ESC[201~\ry"),
                    PASTE,
                    listOf("$ESC[200~x[201~y$ESC[201~", "\r"),
                ),
                Arguments.of(
                    "ESC and CR cannot inject keys when typed",
                    Reply.Text("x$ESC\ry"),
                    TYPE,
                    listOf("x", "y", "\r"),
                ),
                Arguments.of("option by digit", Reply.Option(3), DIGIT, listOf("3")),
                Arguments.of("option by arrows", Reply.Option(3), ARROWS, listOf("$ESC[B", "$ESC[B", "\r")),
                Arguments.of("first option by arrows is just enter", Reply.Option(1), ARROWS, listOf("\r")),
                Arguments.of("permission allow", Reply.Permission(true), DIGIT, listOf("1")),
                Arguments.of("permission deny", Reply.Permission(false), DIGIT, listOf(ESC)),
            )
    }
}
