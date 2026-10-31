package dev.agentmanager.commandpalette.ui

import dev.agentmanager.commandpalette.api.Command
import dev.agentmanager.commandpalette.api.KeyChord
import dev.agentmanager.commandpalette.api.KeyModifier

/** Result of one [ShortcutMatcher] step (AM-409). */
internal sealed interface ShortcutStep {
    data class Run(
        val command: Command,
    ) : ShortcutStep

    data class Wait(
        val first: KeyChord,
    ) : ShortcutStep

    data object Cancelled : ShortcutStep

    data object NoMatch : ShortcutStep
}

/** The sequence rule of window keys without ⌘ (ADR 0011); a one chord command wins over a two chord prefix. */
internal object ShortcutMatcher {
    fun step(
        pending: KeyChord?,
        chord: KeyChord,
        commands: List<Command>,
    ): ShortcutStep {
        val bound =
            commands.mapNotNull { command ->
                command.shortcut
                    ?.chords
                    ?.takeIf { chords ->
                        chords.none { KeyModifier.META in it.modifiers }
                    }?.to(command)
            }
        return if (pending == null) first(chord, bound) else second(pending, chord, bound)
    }

    private fun first(
        chord: KeyChord,
        bound: List<Pair<List<KeyChord>, Command>>,
    ): ShortcutStep {
        val single = bound.firstOrNull { it.first == listOf(chord) }
        return when {
            single != null -> ShortcutStep.Run(single.second)
            bound.any { it.first.size == 2 && it.first[0] == chord } -> ShortcutStep.Wait(chord)
            else -> ShortcutStep.NoMatch
        }
    }

    private fun second(
        pending: KeyChord,
        chord: KeyChord,
        bound: List<Pair<List<KeyChord>, Command>>,
    ): ShortcutStep {
        val hit = bound.firstOrNull { it.first == listOf(pending, chord) }
        return if (hit != null) ShortcutStep.Run(hit.second) else ShortcutStep.Cancelled
    }
}
