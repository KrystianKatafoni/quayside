package dev.agentmanager.terminal.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Reading order and lookup in the layout tree (plan AM-503, decisions 2 and 3). */
class PaneTreeTest {
    private val tree: PaneTree =
        PaneTree.Split(
            SplitAxis.SIDE_BY_SIDE,
            leaf(1, 10),
            PaneTree.Split(SplitAxis.STACKED, leaf(2, 20), leaf(3, 30)),
        )

    @Test
    fun `leaves are listed in reading order`() {
        tree.leaves() shouldBe listOf(leaf(1, 10), leaf(2, 20), leaf(3, 30))
    }

    @Test
    fun `a single leaf lists itself`() {
        leaf(1, 10).leaves() shouldBe listOf(leaf(1, 10))
    }

    @Test
    fun `leaf of a tab is found`() {
        tree.leafOf(tabId(30)) shouldBe leaf(3, 30)
        tree.leafOf(tabId(10)) shouldBe leaf(1, 10)
    }

    @Test
    fun `a hidden tab has no leaf`() {
        tree.leafOf(tabId(99)) shouldBe null
    }
}
