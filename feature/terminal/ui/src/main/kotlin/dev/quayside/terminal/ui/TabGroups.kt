package dev.quayside.terminal.ui

/** Tabs of one project, in the order they were opened; [name] comes from the first tab. */
internal data class TabGroup(
    val project: TerminalProject,
    val tabs: List<TerminalTab>,
)

internal object TabGroups {
    /** Groups in the order of the first tab of each project; tabs inside a group in opening order. */
    fun of(tabs: List<TerminalTab>): List<TabGroup> =
        tabs
            .groupBy { it.project.id }
            .values
            .map { TabGroup(it.first().project, it) }
}
