package dev.quayside.terminal.ui

import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** The dot of a tab takes its colour from the tokens (docs/plans/AM-806.md, table 1). */
class TabStatusColorTest {
    private val tokens = SampleTokens.colors()

    @Test
    fun `source tokens differ so that a wrong token is noticed`() {
        val sources = listOf(tokens.status.warn, tokens.status.work, tokens.status.ok, tokens.status.err)
        (sources + tokens.content.muted + tokens.content.accent).toSet().size shouldBe 6
    }

    @ParameterizedTest(name = "{0} is drawn in {1}")
    @MethodSource("table")
    fun `tab status is drawn in the token of its meaning`(
        status: TabStatus,
        token: String,
    ) {
        status.color(tokens) shouldBe pick(tokens, token)
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
                Arguments.of(TabStatus.WAITING, "warn"),
                Arguments.of(TabStatus.WORKING, "work"),
                Arguments.of(TabStatus.DONE, "ok"),
                Arguments.of(TabStatus.FAILED, "err"),
                Arguments.of(TabStatus.STOPPED, "muted"),
            )
    }
}
