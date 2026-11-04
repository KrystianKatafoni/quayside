package dev.quayside.notifications.ui

import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.notifications.api.NotificationKind
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** The bar of a popup card takes its colour from the tokens (docs/plans/AM-806.md, table 1). */
class PopupBarColorTest {
    private val tokens = SampleTokens.colors()

    @Test
    fun `source tokens differ so that a wrong token is noticed`() {
        val sources = listOf(tokens.status.warn, tokens.status.work, tokens.status.ok, tokens.status.err)
        (sources + tokens.content.muted + tokens.content.accent).toSet().size shouldBe 6
    }

    @Test
    fun `table covers every kind`() {
        table().map { it.get()[0] }.toList().toSet() shouldBe NotificationKind.entries.toSet()
    }

    @ParameterizedTest(name = "{0} is drawn in {1}")
    @MethodSource("table")
    fun `popup bar is drawn in the token of its meaning`(
        kind: NotificationKind,
        token: String,
    ) {
        kind.barColor(tokens) shouldBe pick(tokens, token)
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
                "accent" -> tokens.content.accent
                else -> tokens.content.muted
            }

        @JvmStatic
        fun table(): Stream<Arguments> =
            Stream.of(
                Arguments.of(NotificationKind.NEEDS_INPUT, "warn"),
                Arguments.of(NotificationKind.PERMISSION, "warn"),
                Arguments.of(NotificationKind.FAILED, "err"),
                Arguments.of(NotificationKind.TURN_ENDED, "ok"),
                Arguments.of(NotificationKind.PULL_REQUEST, "accent"),
                Arguments.of(NotificationKind.RUNNING_LONG, "work"),
                Arguments.of(NotificationKind.TERMINAL_BELL, "muted"),
            )
    }
}
