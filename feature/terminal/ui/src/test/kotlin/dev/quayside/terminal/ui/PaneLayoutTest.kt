package dev.quayside.terminal.ui

import dev.quayside.terminal.ui.TerminalEvent.Close
import dev.quayside.terminal.ui.TerminalEvent.DismissError
import dev.quayside.terminal.ui.TerminalEvent.Failed
import dev.quayside.terminal.ui.TerminalEvent.FocusNext
import dev.quayside.terminal.ui.TerminalEvent.FocusPane
import dev.quayside.terminal.ui.TerminalEvent.FocusPrevious
import dev.quayside.terminal.ui.TerminalEvent.Opened
import dev.quayside.terminal.ui.TerminalEvent.Select
import dev.quayside.terminal.ui.TerminalEvent.SplitOpened
import dev.quayside.terminal.ui.TerminalEvent.ToggleMaximized
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Panes: split, focus, close and maximize (plan AM-503, table 2, decisions 2 to 5). */
class PaneLayoutTest {
    private val side = SplitAxis.SIDE_BY_SIDE
    private val stacked = SplitAxis.STACKED

    private fun one() = stateAfter(Opened(aTab(1)))

    private fun two() = stateAfter(SplitOpened(aTab(2), side), from = one())

    private fun three() = stateAfter(SplitOpened(aTab(3), stacked), from = two())

    private fun four() = stateAfter(SplitOpened(aTab(4), side), from = three())

    @Test
    fun `split side by side puts the new pane after the active one and activates it`() {
        val state = two()

        state.layout shouldBe PaneTree.Split(side, leaf(1, 1), leaf(2, 2))
        state.active shouldBe pane(2)
        state.tabs.map { it.id.value } shouldBe listOf(1, 2)
    }

    @Test
    fun `split stacked replaces only the active leaf`() {
        val state = three()

        state.layout shouldBe PaneTree.Split(side, leaf(1, 1), PaneTree.Split(stacked, leaf(2, 2), leaf(3, 3)))
        state.active shouldBe pane(3)
    }

    @Test
    fun `fourth pane is the last one a split can create`() {
        four().layout?.leaves()?.map { it.pane.value } shouldBe listOf(1, 2, 3, 4)
        four().active shouldBe pane(4)
    }

    @Test
    fun `split with four panes shows the tab in the active pane instead`() {
        val state = stateAfter(SplitOpened(aTab(5), side), from = four())

        state.leafTabs() shouldBe listOf(1, 2, 3, 5)
        state.layout?.leaves()?.size shouldBe 4
        state.active shouldBe pane(4)
        state.tabs.size shouldBe 5
    }

    @Test
    fun `closing the tab of a pane removes the pane and the sibling takes its place`() {
        val state = stateAfter(Close(tabId(2)), from = two())

        state.layout shouldBe leaf(1, 1)
        state.active shouldBe pane(1)
    }

    @Test
    fun `closing the active pane activates the next one in reading order`() {
        val state = stateAfter(FocusPane(pane(2)), Close(tabId(2)), from = three())

        state.leafTabs() shouldBe listOf(1, 3)
        state.active shouldBe pane(3)
    }

    @Test
    fun `closing the shown tab of another pane keeps the active pane`() {
        val state = stateAfter(Close(tabId(1)), from = three())

        state.leafTabs() shouldBe listOf(2, 3)
        state.active shouldBe pane(3)
    }

    @Test
    fun `closing a hidden tab keeps the layout of all panes`() {
        val hidden = stateAfter(Opened(aTab(9)), from = two())
        hidden.leafTabs() shouldBe listOf(1, 9)

        val state = stateAfter(Close(tabId(2)), from = hidden)

        state.leafTabs() shouldBe listOf(1, 9)
        state.tabs.map { it.id.value } shouldBe listOf(1, 9)
    }

    @Test
    fun `selecting the tab of another pane activates that pane`() {
        val state = stateAfter(FocusPane(pane(1)), Select(tabId(2)), from = two())

        state.active shouldBe pane(2)
        state.layout shouldBe PaneTree.Split(side, leaf(1, 1), leaf(2, 2))
    }

    @Test
    fun `selecting a hidden tab replaces the tab of the active pane only`() {
        val hidden = stateAfter(FocusPane(pane(1)), Opened(aTab(3)), Select(tabId(1)), from = two())
        // Given p1 shows t1 again and t3 is hidden after the select above
        hidden.leafTabs() shouldBe listOf(1, 2)

        val state = stateAfter(Select(tabId(3)), from = hidden)

        state.leafTabs() shouldBe listOf(3, 2)
        state.active shouldBe pane(1)
    }

    @Test
    fun `next pane goes on cyclically`() {
        val state = stateAfter(FocusNext, from = three())

        state.active shouldBe pane(1)
        stateAfter(FocusNext, from = state).active shouldBe pane(2)
    }

    @Test
    fun `previous pane goes back cyclically`() {
        val atFirst = stateAfter(FocusPane(pane(1)), from = three())

        stateAfter(FocusPrevious, from = atFirst).active shouldBe pane(3)
    }

    @Test
    fun `focus moves with a single pane stay put`() {
        stateAfter(FocusNext, from = one()).active shouldBe pane(1)
        stateAfter(FocusPrevious, from = one()).active shouldBe pane(1)
    }

    @Test
    fun `clicking a pane activates it`() {
        val state = stateAfter(FocusPane(pane(1)), from = two())

        state.active shouldBe pane(1)
        stateAfter(FocusPane(pane(1)), from = state) shouldBe state
    }

    @Test
    fun `focusing an unknown pane changes nothing`() {
        two().let { stateAfter(FocusPane(pane(9)), from = it) shouldBe it }
    }

    @Test
    fun `maximize toggles with two panes`() {
        val maximized = stateAfter(ToggleMaximized, from = two())
        maximized.maximized shouldBe true
        maximized.layout shouldBe two().layout

        stateAfter(ToggleMaximized, from = maximized).maximized shouldBe false
    }

    @Test
    fun `maximize is ignored with one pane`() {
        stateAfter(ToggleMaximized, from = one()).maximized shouldBe false
    }

    @Test
    fun `closing down to one pane ends the maximize`() {
        val maximized = stateAfter(ToggleMaximized, from = two())

        val state = stateAfter(Close(tabId(2)), from = maximized)

        state.leafTabs() shouldBe listOf(1)
        state.maximized shouldBe false
    }

    @Test
    fun `a split ends the maximize`() {
        val maximized = stateAfter(ToggleMaximized, from = two())

        val state = stateAfter(SplitOpened(aTab(3), stacked), from = maximized)

        state.layout?.leaves()?.size shouldBe 3
        state.maximized shouldBe false
    }

    @Test
    fun `focus change while maximized keeps the maximize and moves the shown pane`() {
        val maximized = stateAfter(FocusPane(pane(1)), ToggleMaximized, from = two())

        val state = stateAfter(FocusNext, from = maximized)

        state.active shouldBe pane(2)
        state.maximized shouldBe true
    }

    @Test
    fun `failed open shows the error until it is dismissed`() {
        val failed = stateAfter(Failed(OpenFailure.START_FAILED), from = two())

        failed.error shouldBe OpenFailure.START_FAILED
        failed.layout shouldBe two().layout
        stateAfter(DismissError, from = failed).error shouldBe null
    }
}
