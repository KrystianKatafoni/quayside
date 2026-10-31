package dev.agentmanager.commandpalette.ui

import dev.agentmanager.commandpalette.api.ChordKey
import dev.agentmanager.commandpalette.api.KeyChord
import dev.agentmanager.commandpalette.api.KeyModifier
import dev.agentmanager.commandpalette.api.Shortcut
import dev.agentmanager.commandpalette.api.aCommand
import dev.agentmanager.commandpalette.api.letterChord
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The pure rule of the window keys without ⌘ (AM-409 plan, decision 2, table 1). */
internal class ShortcutMatcherTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `step follows the sequence rule`(
        @Suppress("UNUSED_PARAMETER") name: String,
        pending: KeyChord?,
        chord: KeyChord,
        expected: ShortcutStep,
    ) {
        ShortcutMatcher.step(pending, chord, COMMANDS) shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("conflictCases")
    fun `a one chord command wins over the start of a two chord command`(
        @Suppress("UNUSED_PARAMETER") name: String,
        pending: KeyChord?,
        chord: KeyChord,
        expected: ShortcutStep,
    ) {
        ShortcutMatcher.step(pending, chord, COMMANDS + G_ONLY) shouldBe expected
    }

    companion object {
        private val g = letterChord('g')
        private val p = letterChord('p')
        private val j = letterChord('j')
        private val escape = KeyChord(ChordKey.Named.ESCAPE)

        private val J = aCommand(id = "sessions.next", shortcut = Shortcut.of(j))
        private val K = aCommand(id = "sessions.previous", shortcut = Shortcut.of(letterChord('k')))
        private val GP = aCommand(id = "projects.switch", shortcut = Shortcut.of(g, p))
        private val CMD_K = aCommand(id = "palette.open", shortcut = Shortcut.of(letterChord('k', KeyModifier.META)))
        private val G_ONLY = aCommand(id = "test.g", shortcut = Shortcut.of(g))
        private val COMMANDS = listOf(J, K, GP, CMD_K, aCommand(id = "no.shortcut"))

        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                arguments("j runs its command", null, j, ShortcutStep.Run(J)),
                arguments("g starts a sequence", null, g, ShortcutStep.Wait(g)),
                arguments("g then p runs the pair", g, p, ShortcutStep.Run(GP)),
                arguments("g then Escape cancels", g, escape, ShortcutStep.Cancelled),
                arguments("g then j cancels, j does not run", g, j, ShortcutStep.Cancelled),
                arguments("unbound x is not matched", null, letterChord('x'), ShortcutStep.NoMatch),
                arguments("Escape alone is not matched", null, escape, ShortcutStep.NoMatch),
                arguments("cmd K is left to the menu", null, letterChord('k', KeyModifier.META), ShortcutStep.NoMatch),
                arguments("shift J is not j", null, letterChord('j', KeyModifier.SHIFT), ShortcutStep.NoMatch),
            )

        @JvmStatic
        fun conflictCases(): List<Arguments> =
            listOf(
                arguments("g command wins over g p", null, g, ShortcutStep.Run(G_ONLY)),
            )
    }
}
