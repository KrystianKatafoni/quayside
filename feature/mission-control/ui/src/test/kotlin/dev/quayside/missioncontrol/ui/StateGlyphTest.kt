package dev.quayside.missioncontrol.ui

import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Glyph, tone and state text of every session state (FR-S2, docs/05 table). */
internal class StateGlyphTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("glyphs")
    fun `each state has the glyph and tone of the docs table`(
        state: SessionState,
        expected: Glyph,
    ) {
        StateGlyph.of(state) shouldBe expected
    }

    companion object {
        @JvmStatic
        fun glyphs(): List<Arguments> =
            listOf(
                arguments(SessionState.AwaitingInput("Which branch?"), Glyph("?", Tone.WARN, "state.awaitingInput")),
                arguments(SessionState.AwaitingInput(null), Glyph("?", Tone.WARN, "state.awaitingInput")),
                arguments(SessionState.AwaitingPermission("Bash"), Glyph("!", Tone.WARN, "state.awaitingPermission")),
                arguments(SessionState.AwaitingPermission(null), Glyph("!", Tone.WARN, "state.awaitingPermission")),
                arguments(SessionState.Working, Glyph("~", Tone.WORK, "state.working")),
                arguments(SessionState.Done, Glyph("✓", Tone.OK, "state.done")),
                arguments(SessionState.Failed, Glyph("✕", Tone.ERR, "state.failed")),
                arguments(SessionState.Stopped, Glyph("○", Tone.DIM, "state.stopped")),
            )
    }
}
