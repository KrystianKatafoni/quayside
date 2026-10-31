package dev.agentmanager.commandpalette.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A command with sensible defaults; [run] does nothing. */
public fun aCommand(
    id: String = "test.command",
    title: String = "Test command",
    group: CommandGroup = CommandGroup.APP,
    keywords: List<String> = emptyList(),
    shortcut: Shortcut? = null,
    run: suspend () -> Unit = {},
): Command = Command(CommandId(id), title, group, keywords, shortcut, run)

/** A single-letter chord such as `g`, optionally with [modifiers]. */
public fun letterChord(
    char: Char,
    vararg modifiers: KeyModifier,
): KeyChord = KeyChord(ChordKey.Letter(char), modifiers.toSet())

/** A [CommandProvider] whose list the test changes with [set]. */
public class FakeCommandProvider(
    initial: List<Command> = emptyList(),
) : CommandProvider {
    private val state = MutableStateFlow(initial)

    override val commands: StateFlow<List<Command>> get() = state

    public fun set(commands: List<Command>) {
        state.value = commands
    }
}
