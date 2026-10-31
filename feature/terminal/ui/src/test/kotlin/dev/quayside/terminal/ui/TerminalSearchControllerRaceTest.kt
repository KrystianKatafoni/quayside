@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.terminal.ui

import dev.quayside.core.testing.RecordingLogger
import dev.quayside.terminal.api.SearchDirection
import dev.quayside.terminal.api.SearchQuery
import dev.quayside.terminal.api.SearchResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Races of the search bar logic: queued steps, cancelled chains and close during a find (plan AM-504). */
class TerminalSearchControllerRaceTest {
    private val logger = RecordingLogger()

    /** A controller whose active tab is [active], working in the test scope. */
    private class Bar(
        scope: TestScope,
        logger: RecordingLogger,
        var active: TerminalTab?,
    ) {
        val controller = TerminalSearchController(scope.backgroundScope, logger) { active }
        val state get() = controller.state.value
    }

    private fun TestScope.bar(tab: TerminalTab?): Bar = Bar(this, logger, tab)

    private suspend fun hits(): TerminalTab = searchableTab(id = 1, "err one", "other", "Err two", "ERR three")

    @Test
    fun `newer typing cancels the running find`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            act { bar.controller.open() }
            val gate = CompletableDeferred<Unit>()
            tab.fake.search.gate = gate

            act { bar.controller.type("e") }
            act { bar.controller.type("three") }
            gate.complete(Unit)
            runCurrent()

            tab.fake.search.queries shouldBe listOf(SearchQuery("e"), SearchQuery("three"))
            tab.fake.search.completedFinds shouldBe 1
            bar.state?.query?.text shouldBe "three"
            bar.state?.result shouldBe SearchResult(count = 1, current = 0, limited = false)
        }

    @Test
    fun `step queued behind a find does not let typing leak that find`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            act { bar.controller.open() }
            act { bar.controller.type("err") }
            val gate = CompletableDeferred<Unit>()
            tab.fake.search.gate = gate

            act { bar.controller.type("e") }
            act { bar.controller.step(SearchDirection.UP) }
            act { bar.controller.type("three") }
            gate.complete(Unit)
            runCurrent()

            // "err" (before the gate) and "three"; the find of "e" was cancelled, not just its queued step
            tab.fake.search.completedFinds shouldBe 2
            bar.state?.query?.text shouldBe "three"
            bar.state?.result shouldBe SearchResult(count = 1, current = 0, limited = false)
        }

    @Test
    fun `typing cancels the whole chain of queued steps`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            act { bar.controller.open() }
            act { bar.controller.type("err") }
            val gate = CompletableDeferred<Unit>()
            tab.fake.search.gate = gate

            act { bar.controller.type("e") }
            act { bar.controller.step(SearchDirection.UP) }
            act { bar.controller.step(SearchDirection.UP) }
            act { bar.controller.type("three") }
            gate.complete(Unit)
            runCurrent()

            tab.fake.search.steps shouldBe emptyList()
            bar.state?.query?.text shouldBe "three"
            bar.state?.result shouldBe SearchResult(count = 1, current = 0, limited = false)
        }

    @Test
    fun `close during a find with a queued step leaves no highlights`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            act { bar.controller.open() }
            act { bar.controller.type("err") }
            val gate = CompletableDeferred<Unit>()
            tab.fake.search.gate = gate
            act { bar.controller.type("e") }
            act { bar.controller.step(SearchDirection.UP) }

            act { bar.controller.close() }
            gate.complete(Unit)
            runCurrent()

            bar.state shouldBe null
            tab.fake.search.highlighted shouldBe emptyList()
        }

    @Test
    fun `step during the first find is counted after the find ends`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            act { bar.controller.open() }
            val gate = CompletableDeferred<Unit>()
            tab.fake.search.gate = gate
            act { bar.controller.type("err") }
            bar.state?.result shouldBe SearchResult.NONE

            act { bar.controller.step(SearchDirection.UP) }
            gate.complete(Unit)
            runCurrent()

            tab.fake.search.steps shouldBe listOf(SearchDirection.UP)
            bar.state?.result shouldBe SearchResult(count = 3, current = 1, limited = false)
        }
}
