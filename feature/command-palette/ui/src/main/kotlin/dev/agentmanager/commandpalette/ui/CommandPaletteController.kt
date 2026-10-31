package dev.agentmanager.commandpalette.ui

import androidx.compose.ui.input.key.KeyEvent
import dev.agentmanager.commandpalette.api.Command
import dev.agentmanager.commandpalette.api.CommandGroup
import dev.agentmanager.commandpalette.api.CommandRegistry
import dev.agentmanager.commandpalette.api.KeyChord
import dev.agentmanager.core.domain.Logger
import dev.agentmanager.core.domain.info
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder of the ⌘K palette (FR-K1). Reads [registry] and runs the chosen command in [scope]
 * (the window scope, Main dispatcher; not thread-safe). Logs only command ids (ENG-C8), never titles or queries.
 */
public class CommandPaletteController(
    private val registry: CommandRegistry,
    private val scope: CoroutineScope,
    private val logger: Logger,
) {
    private val state = MutableStateFlow(PaletteState())
    private var pending: KeyChord? = null

    /** True while the palette is shown; `app` disables the window-menu project items then. */
    public val isOpen: StateFlow<Boolean> =
        state.map { it.open }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Read by the composable and the tests only. */
    internal val view: StateFlow<PaletteView> =
        combine(state, registry.commands) { palette, all -> palette.view(all) }
            .stateIn(scope, SharingStarted.Eagerly, state.value.view(registry.commands.value))

    /** Opens with an empty query, limited to [group] when given (`g p` in AM-409); ignored when already open. */
    public fun open(group: CommandGroup? = null) {
        if (state.value.open) return
        pending = null
        state.value = PaletteState(open = true, group = group)
    }

    /** Closes without running anything. */
    public fun close() {
        pending = null
        state.value = PaletteState()
    }

    /**
     * A key of the main window (`Window.onKeyEvent`, ADR 0011). Runs the registered command whose shortcut has no ⌘
     * and matches this key, or this key after a pending first chord (`g p`). The first chord waits with no time
     * limit; Escape or any other key cancels it. Ignored while the palette is open. ⌘ shortcuts stay with `MenuBar`.
     * @return true when the key was used: a run, a pending first chord or a cancel.
     */
    public fun onKeyEvent(event: KeyEvent): Boolean = event.toChord()?.let(::onChord) ?: false

    /** [onKeyEvent] without Compose types. */
    internal fun onChord(chord: KeyChord): Boolean {
        if (state.value.open) return false
        val step = ShortcutMatcher.step(pending, chord, registry.commands.value)
        pending = (step as? ShortcutStep.Wait)?.first
        if (step is ShortcutStep.Run) run(step.command)
        return step != ShortcutStep.NoMatch
    }

    internal fun onEvent(event: PaletteEvent) {
        val current = state.value
        if (!current.open) return
        val now = current.view(registry.commands.value)
        when (event) {
            PaletteEvent.Confirm -> run(now.results.getOrNull(now.highlighted))
            is PaletteEvent.Pick -> run(now.results.getOrNull(event.index))
            else -> state.value = current.copy(highlighted = now.highlighted).reduce(event, now.results.size)
        }
    }

    /** Closes first, so a second Enter finds a closed palette; then runs [command] in the window scope. */
    private fun run(command: Command?) {
        if (command == null) return
        close()
        logger.info("command run", "commandId" to command.id.value)
        scope.launch { command.run() }
    }
}
