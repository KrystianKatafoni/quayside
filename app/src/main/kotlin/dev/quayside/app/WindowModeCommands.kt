package dev.quayside.app

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.commandpalette.api.CommandProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Id of the palette command that enters the terminal mode ("Tryb terminala"). */
internal const val TERMINAL_MODE_COMMAND = "window.terminalMode"

/** Id of the palette command that leaves the terminal mode ("Wróć do listy sesji"). */
internal const val BACK_TO_LIST_COMMAND = "window.backToList"

/** Offers "Tryb terminala" in the list mode and "Wróć do listy sesji" in the terminal mode. */
internal class WindowModeCommands(
    state: WindowModeState,
    scope: CoroutineScope,
) : CommandProvider {
    private fun offered(mode: WindowMode): List<Command> =
        when (mode) {
            WindowMode.LIST -> {
                listOf(
                    command(TERMINAL_MODE_COMMAND, "command.terminalMode", WindowModeEvent.FullScreen),
                )
            }

            WindowMode.TERMINAL -> {
                listOf(command(BACK_TO_LIST_COMMAND, "command.backToList", WindowModeEvent.Back))
            }
        }

    private val onEvent = state::onEvent

    private fun command(
        id: String,
        titleKey: String,
        event: WindowModeEvent,
    ): Command = Command(CommandId(id), Strings[titleKey], CommandGroup.APP, listOf("terminal")) { onEvent(event) }

    override val commands: StateFlow<List<Command>> =
        state.mode.map(::offered).stateIn(scope, SharingStarted.Eagerly, offered(state.mode.value))
}
