package dev.agentmanager.terminal.ui

import dev.agentmanager.core.domain.LogLevel
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.terminal.api.SearchDirection
import dev.agentmanager.terminal.api.SearchQuery
import dev.agentmanager.terminal.api.SearchResult
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** The search bar logic over fake sessions (plan AM-504, decisions 3 to 6); time is the test scheduler's. */
class TerminalSearchControllerTest {
    @TempDir
    lateinit var root: Path

    private val logger = RecordingLogger()

    /** A controller whose active tab is [active] (reassignable), working in the test scope. */
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
    fun `open binds the bar to the active tab`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            bar.state shouldBe null

            act { bar.controller.open() }

            bar.state?.tab shouldBe tab.id
            bar.state?.query shouldBe SearchQuery("")
            bar.state?.result shouldBe SearchResult.NONE
            bar.state?.invalid shouldBe false
        }

    @Test
    fun `open without an active tab does nothing`() =
        runTest {
            val bar = bar(null)

            act { bar.controller.open() }

            bar.state shouldBe null
        }

    @Test
    fun `typing runs find with the current options`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            act { bar.controller.open() }

            act { bar.controller.type("err") }

            tab.fake.search.queries shouldBe listOf(SearchQuery("err", matchCase = false, regex = false))
            bar.state?.query shouldBe SearchQuery("err")
            bar.state?.result shouldBe SearchResult(count = 3, current = 2, limited = false)
        }

    @Test
    fun `toggles rerun find`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            act { bar.controller.open() }
            act { bar.controller.type("Err") }

            act { bar.controller.toggleCase() }
            bar.state?.result?.count shouldBe 1
            tab.fake.search.queries
                .last() shouldBe SearchQuery("Err", matchCase = true, regex = false)

            act { bar.controller.toggleRegex() }
            tab.fake.search.queries
                .last() shouldBe SearchQuery("Err", matchCase = true, regex = true)

            act { bar.controller.toggleCase() }
            tab.fake.search.queries
                .last() shouldBe SearchQuery("Err", matchCase = false, regex = true)
            bar.state?.result?.count shouldBe 3
            tab.fake.search.queries shouldHaveSize 4
        }

    @Test
    fun `empty text clears and hides the counter`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            act { bar.controller.open() }
            act { bar.controller.type("err") }
            bar.state?.result?.count shouldBe 3

            act { bar.controller.type("") }

            tab.fake.search.clears shouldBe 1
            tab.fake.search.queries shouldBe listOf(SearchQuery("err"))
            tab.fake.search.highlighted shouldBe emptyList()
            bar.state?.result shouldBe SearchResult.NONE
            bar.state?.invalid shouldBe false
        }

    @Test
    fun `invalid regex marks the bar and clears`() =
        runTest {
            val tab = searchableTab(id = 1, "a(b", "c")
            val bar = bar(tab)
            act { bar.controller.open() }
            act { bar.controller.type("(") }
            tab.fake.search.highlighted shouldHaveSize 1

            act { bar.controller.toggleRegex() }

            bar.state?.invalid shouldBe true
            bar.state?.result shouldBe SearchResult.NONE
            tab.fake.search.highlighted shouldBe emptyList()

            act { bar.controller.type("a") }
            bar.state?.invalid shouldBe false
        }

    @Test
    fun `step up and down update the counter`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            act { bar.controller.open() }
            act { bar.controller.type("err") }
            bar.state?.result?.current shouldBe 2

            act { bar.controller.step(SearchDirection.UP) }
            bar.state?.result?.current shouldBe 1

            act { bar.controller.step(SearchDirection.UP) }
            act { bar.controller.step(SearchDirection.DOWN) }
            bar.state?.result?.current shouldBe 1

            tab.fake.search.steps shouldBe listOf(SearchDirection.UP, SearchDirection.UP, SearchDirection.DOWN)
        }

    @Test
    fun `step without hits does not call the session`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            act { bar.controller.open() }
            act { bar.controller.type("zzz") }

            act { bar.controller.step(SearchDirection.UP) }

            bar.state?.result shouldBe SearchResult.NONE
            tab.fake.search.steps shouldBe emptyList()
        }

    @Test
    fun `close clears the session search`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            act { bar.controller.open() }
            act { bar.controller.type("err") }

            act { bar.controller.close() }

            bar.state shouldBe null
            tab.fake.search.clears shouldBe 1
            tab.fake.search.highlighted shouldBe emptyList()
        }

    @Test
    fun `switching the active tab closes the bar`() =
        runTest {
            val first = hits()
            val second = searchableTab(id = 2, "x")
            val bar = bar(first)
            act { bar.controller.open() }
            act { bar.controller.type("err") }

            bar.active = second
            act { bar.controller.onActiveTabChanged(second) }

            bar.state shouldBe null
            first.fake.search.clears shouldBe 1
            second.fake.search.clears shouldBe 0
        }

    @Test
    fun `the same active tab keeps the bar`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            act { bar.controller.open() }
            act { bar.controller.type("err") }

            act { bar.controller.onActiveTabChanged(tab) }

            bar.state?.result?.count shouldBe 3
            tab.fake.search.clears shouldBe 0
        }

    @Test
    fun `closing the tab closes the bar`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            act { bar.controller.open() }
            act { bar.controller.type("err") }

            bar.active = null
            act { bar.controller.onActiveTabChanged(null) }

            bar.state shouldBe null
        }

    @Test
    fun `open again requests focus`() =
        runTest {
            val tab = hits()
            val bar = bar(tab)
            act { bar.controller.open() }
            act { bar.controller.type("err") }
            val first = bar.state?.focusRequest ?: error("bar is closed")

            act { bar.controller.open() }

            (bar.state?.focusRequest ?: 0) shouldBeGreaterThan first
            bar.state?.query shouldBe SearchQuery("err")
            bar.state?.result?.count shouldBe 3
        }

    @Test
    fun `logs hold no query text`() =
        runTest {
            val tab = searchableTab(id = 1, "needle SECRET_QUERY", "x")
            val bar = bar(tab)
            act { bar.controller.open() }

            act { bar.controller.type("SECRET_QUERY") }
            val found = logger.entries.filter { it.message == "terminal search" }
            act { bar.controller.toggleRegex() }
            act { bar.controller.type("(") }

            found shouldHaveSize 1
            found.single().level shouldBe LogLevel.DEBUG
            found.single().fields["tabId"] shouldBe 1
            found.single().fields["regex"] shouldBe false
            found.single().fields["count"] shouldBe 1
            logger.entries.map { it.message } shouldContain "terminal search"
            logger.entries.forEach { entry ->
                "${entry.message} ${entry.fields} ${entry.cause}" shouldNotContain "SECRET_QUERY"
            }
            // the invalid pattern is not logged at all
            logger.entries.count { it.message == "terminal search" } shouldBe 2
        }

    @Test
    fun `closing a tab in the terminal controller closes the bar`() =
        runTest {
            val world = world()
            act { world.controller.openShell(projectIn(root, "Alpha")) }
            act { world.controller.search.open() }
            world.controller.search.state.value
                ?.tab shouldBe tabId(1)

            act { world.controller.close(tabId(1)) }

            world.controller.search.state.value shouldBe null
        }

    @Test
    fun `selecting another tab in the terminal controller closes the bar`() =
        runTest {
            val world = world()
            val project = projectIn(root, "Alpha")
            act { world.controller.openShell(project) }
            act { world.controller.openShell(project) }
            act { world.controller.search.open() }
            world.controller.search.state.value
                ?.tab shouldBe tabId(2)

            act { world.controller.select(tabId(1)) }

            world.controller.search.state.value shouldBe null
        }
}
