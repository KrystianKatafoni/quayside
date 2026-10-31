package dev.quayside.terminal.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.terminal.api.TerminalSession
import java.nio.file.Path

/** The project a tab belongs to; tabs are grouped by [id] and labelled with [name]. [dir]: where shells start. */
public data class TerminalProject(
    val id: ProjectId,
    val name: String,
    val dir: Path,
)

/** How [TerminalController.split] places the new pane: ⌘D [SIDE_BY_SIDE], ⌘⇧D [STACKED]. */
public enum class SplitAxis { SIDE_BY_SIDE, STACKED }

/** A key press seen while a terminal has the focus; [keyCode] is a `java.awt.event.KeyEvent.VK_*` code. */
public data class TerminalKey(
    val keyCode: Int,
    val meta: Boolean,
    val shift: Boolean,
    val alt: Boolean,
    val ctrl: Boolean,
)

/** What the terminal can do now; `app` enables menu items and palette commands from it. */
public data class TerminalMenuState(
    val hasTabs: Boolean,
    val canSplit: Boolean,
    val canSwitchPane: Boolean,
    val maximized: Boolean,
)

/**
 * Identity of a tab chosen by the caller (e.g. `attach:<sessionId>`), so a later request finds the same tab (AM-510).
 * Not logged.
 */
@JvmInline
public value class TabKey(
    public val value: String,
)

/** The state shown as a dot next to a keyed tab; `app` maps its domain state to it (AM-510). */
public enum class TabStatus { WAITING, WORKING, DONE, FAILED, STOPPED }

@JvmInline
internal value class TabId(
    val value: Int,
)

@JvmInline
internal value class PaneId(
    val value: Int,
)

internal enum class TabKind { SHELL, COMMAND }

internal data class TerminalTab(
    val id: TabId,
    val project: TerminalProject,
    val label: String,
    val kind: TabKind,
    val session: TerminalSession,
    val exitCode: Int?,
    val key: TabKey? = null,
)

internal enum class OpenFailure { WORK_DIR_MISSING, START_FAILED }

internal data class TerminalUiState(
    val tabs: List<TerminalTab> = emptyList(),
    val layout: PaneTree? = null,
    val active: PaneId? = null,
    val maximized: Boolean = false,
    val error: OpenFailure? = null,
)

/** Events of [TerminalState.reduce]. */
internal sealed interface TerminalEvent {
    /** Events handled by [TerminalTabs]. */
    sealed interface OfTab : TerminalEvent

    /** Events handled by [PaneLayout]. */
    sealed interface OfPane : TerminalEvent

    data class Opened(
        val tab: TerminalTab,
    ) : OfTab

    data class Replaced(
        val old: TabId,
        val tab: TerminalTab,
    ) : OfTab

    data class Select(
        val tab: TabId,
    ) : OfTab

    data class Close(
        val tab: TabId,
    ) : OfTab

    data class Exited(
        val tab: TabId,
        val code: Int,
    ) : OfTab

    data class SplitOpened(
        val tab: TerminalTab,
        val axis: SplitAxis,
    ) : OfPane

    data class FocusPane(
        val pane: PaneId,
    ) : OfPane

    data object FocusNext : OfPane

    data object FocusPrevious : OfPane

    data object ToggleMaximized : OfPane

    data class Failed(
        val failure: OpenFailure,
    ) : TerminalEvent

    data object DismissError : TerminalEvent
}

/** A pane command bound to a key (⌘D, ⌘⇧D, ⌘⌥←/→, ⌘⇧↵). */
internal sealed interface PaneCommand {
    data class Split(
        val axis: SplitAxis,
    ) : PaneCommand

    data object FocusNext : PaneCommand

    data object FocusPrevious : PaneCommand

    data object ToggleMaximized : PaneCommand

    /** ⌘F: opens the search bar of the active pane. */
    data object Find : PaneCommand
}

/** What [TerminalController.focusOrOpen] does for a key (AM-510, table 1). */
internal sealed interface TabReuse {
    data class Focus(
        val tab: TabId,
    ) : TabReuse

    data class Replace(
        val tab: TabId,
    ) : TabReuse

    data object Open : TabReuse
}

/** What a new tab is: its [project], [label], [kind] and the [key] chosen by the caller, if any (AM-510). */
internal data class TabSpec(
    val project: TerminalProject,
    val label: String,
    val kind: TabKind,
    val key: TabKey? = null,
)

/** Where [TerminalController] puts the process it started (AM-510). */
internal sealed interface Placement {
    /** A new tab in the active pane. */
    data object Active : Placement

    /** A new pane next to the active one. */
    data class Split(
        val axis: SplitAxis,
    ) : Placement

    /** A new tab in the place of the exited tab [old]. */
    data class Replace(
        val old: TabId,
    ) : Placement
}
