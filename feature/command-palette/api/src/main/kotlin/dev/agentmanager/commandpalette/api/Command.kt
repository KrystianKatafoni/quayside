package dev.agentmanager.commandpalette.api

import kotlinx.coroutines.flow.StateFlow

/** Stable id of a command, e.g. `projects.add` or `projects.goto.<projectId>`; logged and tested, never shown. */
@JvmInline
public value class CommandId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "Command id must not be blank" }
    }
}

/** Where a command is listed; the palette shows groups in this order. */
public enum class CommandGroup { PROJECT, GO_TO_PROJECT, SESSION, APP }

/**
 * One action of the palette (FR-K1). [run] is called on the palette's scope (Main dispatcher), must not block and
 * must not throw except CancellationException; it shows its own errors (e.g. as a dialog of its feature).
 *
 * @property title localised text; may hold a project name, so it is never logged (ENG-C8).
 * @property keywords extra words the search matches, e.g. `rename`.
 * @property shortcut optional key binding shown next to the title.
 */
public data class Command(
    val id: CommandId,
    val title: String,
    val group: CommandGroup,
    val keywords: List<String> = emptyList(),
    val shortcut: Shortcut? = null,
    val run: suspend () -> Unit,
)

/**
 * A source of commands that change with the app state (e.g. one per project).
 * [commands] may emit from any thread (ENG-I1).
 */
public interface CommandProvider {
    /** Current commands of this source, in its own order. */
    public val commands: StateFlow<List<Command>>
}

/**
 * Every command the app offers now. [register] and [AutoCloseable.close] may be called from any thread.
 * [commands] lists providers in registration order and keeps each provider's own order. A duplicate [CommandId]
 * keeps the first command. A duplicate [Shortcut] keeps the first binding, the later command stays without a shortcut.
 */
public interface CommandRegistry {
    /** Adds [provider] until the returned handle is closed; closing twice does nothing. */
    public fun register(provider: CommandProvider): AutoCloseable

    /** The merged list of all registered providers. */
    public val commands: StateFlow<List<Command>>
}
