package dev.quayside.terminal.ui

/** Layout of panes: a leaf shows one tab, a split puts two subtrees next to each other or one above the other. */
internal sealed interface PaneTree {
    data class Leaf(
        val pane: PaneId,
        val tab: TabId,
    ) : PaneTree

    data class Split(
        val axis: SplitAxis,
        val first: PaneTree,
        val second: PaneTree,
    ) : PaneTree

    /** Leaves in reading order: first subtree before second, depth first. */
    fun leaves(): List<Leaf> =
        when (this) {
            is Leaf -> listOf(this)
            is Split -> first.leaves() + second.leaves()
        }

    /** The leaf that shows [tab], null when the tab is hidden. */
    fun leafOf(tab: TabId): Leaf? = leaves().firstOrNull { it.tab == tab }

    /** This tree with the leaf [pane] replaced by [node]. */
    fun replace(
        pane: PaneId,
        node: PaneTree,
    ): PaneTree =
        when (this) {
            is Leaf -> if (this.pane == pane) node else this
            is Split -> Split(axis, first.replace(pane, node), second.replace(pane, node))
        }

    /** This tree without the leaf [pane]; the sibling takes its place. Null when nothing is left. */
    fun without(pane: PaneId): PaneTree? =
        when (this) {
            is Leaf -> {
                if (this.pane == pane) null else this
            }

            is Split -> {
                val kept = listOfNotNull(first.without(pane), second.without(pane))
                if (kept.size == 2) Split(axis, kept[0], kept[1]) else kept.singleOrNull()
            }
        }
}
