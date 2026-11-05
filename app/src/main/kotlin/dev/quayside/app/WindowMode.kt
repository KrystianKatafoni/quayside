package dev.quayside.app

import dev.quayside.terminal.ui.TerminalMenuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Layout of the main window: the session list with the sidebar, or the terminal mode of docs/06 (mockup B). */
internal enum class WindowMode { LIST, TERMINAL }

/** What changes the [WindowMode]: the full screen button or palette command, "Wróć", a project in the rail, no tabs. */
internal sealed interface WindowModeEvent {
    data object FullScreen : WindowModeEvent

    data object Back : WindowModeEvent

    data object ProjectPicked : WindowModeEvent

    data object TabsGone : WindowModeEvent
}

/** The mode after [event]; [hasTabs] says whether the terminal has a tab (plan AM-809, table 4). */
internal fun WindowMode.after(
    event: WindowModeEvent,
    hasTabs: Boolean,
): WindowMode =
    when (event) {
        WindowModeEvent.FullScreen -> if (hasTabs) WindowMode.TERMINAL else this
        WindowModeEvent.Back, WindowModeEvent.ProjectPicked, WindowModeEvent.TabsGone -> WindowMode.LIST
    }

/** The mode of one window; [hasTabs] reads whether the terminal has a tab now. */
internal class WindowModeState(
    private val hasTabs: () -> Boolean,
) {
    private val current = MutableStateFlow(WindowMode.LIST)
    val mode: StateFlow<WindowMode> = current.asStateFlow()

    fun onEvent(event: WindowModeEvent) {
        current.value = current.value.after(event, hasTabs())
    }

    /** Leaves the terminal mode when the last tab is gone; call it when the tabs change. */
    fun sync() {
        if (!hasTabs()) onEvent(WindowModeEvent.TabsGone)
    }

    /** Calls [sync] in [scope] whenever [menu] says the terminal gained or lost its tabs (any kind of tab). */
    fun follow(
        menu: StateFlow<TerminalMenuState>,
        scope: CoroutineScope,
    ) {
        scope.launch { menu.map { it.hasTabs }.distinctUntilChanged().collect { sync() } }
    }
}

/** Semantics tags of the terminal mode layout (the tags of the other modules are in `WindowTags` of the tests). */
internal object ModeTags {
    const val BACK = "window.back"
}
