package dev.agentmanager.terminal.ui

import dev.agentmanager.terminal.ui.TerminalEvent.Close
import dev.agentmanager.terminal.ui.TerminalEvent.Exited
import dev.agentmanager.terminal.ui.TerminalEvent.FocusPane
import dev.agentmanager.terminal.ui.TerminalEvent.Opened
import dev.agentmanager.terminal.ui.TerminalEvent.Replaced
import dev.agentmanager.terminal.ui.TerminalEvent.Select
import dev.agentmanager.terminal.ui.TerminalEvent.SplitOpened
import dev.agentmanager.terminal.ui.TerminalEvent.ToggleMaximized
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Tabs in a single pane (plan AM-503, table 1, decisions 1 to 3). */
class TerminalTabsTest {
    private val t1 = aTab(1)
    private val t2 = aTab(2)

    @Test
    fun `first tab opens the first pane and activates it`() {
        val state = stateAfter(Opened(t1))

        state.tabs.map { it.id } shouldBe listOf(tabId(1))
        state.layout shouldBe leaf(1, 1)
        state.active shouldBe pane(1)
        state.maximized shouldBe false
        state.error shouldBe null
    }

    @Test
    fun `second tab takes the pane and hides the first`() {
        val state = stateAfter(Opened(t1), Opened(t2))

        state.tabs.map { it.id } shouldBe listOf(tabId(1), tabId(2))
        state.layout shouldBe leaf(1, 2)
        state.active shouldBe pane(1)
    }

    @Test
    fun `selecting a hidden tab shows it in the active pane`() {
        val state = stateAfter(Opened(t1), Opened(t2), Select(tabId(1)))

        state.layout shouldBe leaf(1, 1)
        state.tabs.size shouldBe 2
    }

    @Test
    fun `closing the shown tab shows its next neighbour in the group`() {
        val state =
            stateAfter(Opened(aTab(1)), Opened(aTab(2)), Opened(aTab(3)), Select(tabId(2)), Close(tabId(2)))

        state.layout shouldBe leaf(1, 3)
        state.tabs.map { it.id.value } shouldBe listOf(1, 3)
    }

    @Test
    fun `closing the last tab of a group shows the last tab of another group`() {
        val state =
            stateAfter(
                Opened(aTab(1, ALPHA)),
                Opened(aTab(2, BETA)),
                Opened(aTab(3, BETA)),
                Select(tabId(1)),
                Close(tabId(1)),
            )

        state.layout shouldBe leaf(1, 3)
        state.tabs.map { it.id.value } shouldBe listOf(2, 3)
    }

    @Test
    fun `closing a hidden tab keeps the layout`() {
        val before = stateAfter(Opened(t1), Opened(t2))

        val state = TerminalState.reduce(before, Close(tabId(1)))

        state.layout shouldBe leaf(1, 2)
        state.active shouldBe pane(1)
        state.tabs.map { it.id.value } shouldBe listOf(2)
    }

    @Test
    fun `closing the only tab empties the layout`() {
        val state = stateAfter(Opened(t1), Close(tabId(1)))

        state.tabs shouldBe emptyList()
        state.layout shouldBe null
        state.active shouldBe null
    }

    @Test
    fun `exit records the code and keeps the tab shown`() {
        val state = stateAfter(Opened(t1), Exited(tabId(1), 3))

        state.tab(1).exitCode shouldBe 3
        state.layout shouldBe leaf(1, 1)
    }

    @Test
    fun `unknown tab ids change nothing`() {
        val before = stateAfter(Opened(t1), Opened(t2))

        TerminalState.reduce(before, Select(tabId(9))) shouldBe before
        TerminalState.reduce(before, Close(tabId(9))) shouldBe before
        TerminalState.reduce(before, Exited(tabId(9), 1)) shouldBe before
    }

    // AM-510, table 2: replace(state, old, tab)

    private fun twoPanes() = stateAfter(Opened(aTab(1)), SplitOpened(aTab(2), SplitAxis.SIDE_BY_SIDE))

    @Test
    fun `replace keeps the position of the dead tab in its group`() {
        val state = stateAfter(Opened(aTab(1)), Opened(aTab(2)), Opened(aTab(3)), Replaced(tabId(2), aTab(4)))

        state.tabs.map { it.id.value } shouldBe listOf(1, 4, 3)
    }

    @Test
    fun `replace shows the new tab in the pane of the dead tab and makes it active`() {
        // Given the dead tab 2 in pane 2 while pane 1 is active
        val before = stateAfter(FocusPane(pane(1)), from = twoPanes())

        val state = stateAfter(Replaced(tabId(2), aTab(3)), from = before)

        state.leafTabs() shouldBe listOf(1, 3)
        state.layout?.leafOf(tabId(3))?.pane shouldBe pane(2)
        state.active shouldBe pane(2)
        state.tabs.map { it.id.value } shouldBe listOf(1, 3)
    }

    @Test
    fun `replace of the tab in the active pane keeps the active pane`() {
        val state = stateAfter(Replaced(tabId(2), aTab(3)), from = twoPanes())

        state.leafTabs() shouldBe listOf(1, 3)
        state.active shouldBe pane(2)
    }

    @Test
    fun `replace of a hidden dead tab shows the new tab in the active pane`() {
        // Given tab 1 hidden behind tab 2
        val state = stateAfter(Opened(aTab(1)), Opened(aTab(2)), Replaced(tabId(1), aTab(3)))

        state.tabs.map { it.id.value } shouldBe listOf(3, 2)
        state.layout shouldBe leaf(1, 3)
        state.active shouldBe pane(1)
    }

    @Test
    fun `replace of a closed tab opens like a new tab`() {
        val state = stateAfter(Opened(aTab(1)), Replaced(tabId(9), aTab(2)))

        state.tabs.map { it.id.value } shouldBe listOf(1, 2)
        state.layout shouldBe leaf(1, 2)
        state.active shouldBe pane(1)
    }

    @Test
    fun `replace keeps maximized`() {
        val state = stateAfter(ToggleMaximized, Replaced(tabId(2), aTab(3)), from = twoPanes())

        state.maximized shouldBe true
        state.leafTabs() shouldBe listOf(1, 3)
    }
}
