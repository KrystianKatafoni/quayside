package dev.agentmanager.terminal.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.errorOrNull
import dev.agentmanager.core.domain.getOrNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Behaviour every [TerminalSearch] must have (ENG-T2, FR-T3). Runs on the fake and on the JediTerm implementation.
 * Subclasses that start real processes add `@Timeout(value = 10, threadMode = SEPARATE_THREAD)`.
 */
public abstract class TerminalSearchContract {
    private val opened = mutableListOf<TerminalSession>()

    /**
     * A session whose screen shows [lines], oldest first, and nothing else. Fake: [FakeTerminalSession.print];
     * `impl`: `openCommand(printf '%s\n' ...)` and waiting until the last line is in the buffer.
     */
    protected abstract suspend fun sessionShowing(lines: List<String>): TerminalSession

    /** Ends the process of [session] and returns when [TerminalSession.exitCode] is set. */
    protected abstract suspend fun endProcess(session: TerminalSession)

    /** Hook for subclasses to release what they allocated, after every session was closed. */
    protected open fun tearDown() {}

    @AfterEach
    public fun closeSessions() {
        opened.forEach { it.close() }
        tearDown()
    }

    private suspend fun showing(vararg lines: String): TerminalSession =
        sessionShowing(lines.toList()).also { opened += it }

    private fun <T> Outcome<T, SearchError>.hit(): T = getOrNull() ?: error("search failed: ${errorOrNull()}")

    @Test
    public fun `find counts hits in all lines`(): Unit =
        runTest {
            val session = showing("alpha one", "beta", "alphabet alpha")

            val result = session.search.find(SearchQuery("alpha")).hit()

            result.count shouldBe 3
            result.limited shouldBe false
        }

    @Test
    public fun `find ignores case unless matchCase`(): Unit =
        runTest {
            val session = showing("Error", "no err", "ERR")

            session.search
                .find(SearchQuery("err"))
                .hit()
                .count shouldBe 3
            session.search
                .find(SearchQuery("err", matchCase = true))
                .hit()
                .count shouldBe 1
        }

    @Test
    public fun `regex finds pattern hits`(): Unit =
        runTest {
            val session = showing("a12b3", "none", "7")

            session.search
                .find(SearchQuery("""\d+""", regex = true))
                .hit()
                .count shouldBe 3
            session.search
                .find(SearchQuery("""\d+""", regex = false))
                .hit()
                .count shouldBe 0
        }

    @Test
    public fun `invalid regex returns InvalidPattern`(): Unit =
        runTest {
            val session = showing("a(b")

            session.search.find(SearchQuery("(", regex = true)).errorOrNull() shouldBe SearchError.InvalidPattern
        }

    @Test
    public fun `empty text returns NONE`(): Unit =
        runTest {
            val session = showing("abc")

            session.search.find(SearchQuery("")) shouldBe Outcome.Ok(SearchResult.NONE)
        }

    @Test
    public fun `find selects the newest hit`(): Unit =
        runTest {
            val session = showing("hit 1", "other", "hit 2", "hit 3")

            val result = session.search.find(SearchQuery("hit")).hit()

            result shouldBe SearchResult(count = 3, current = 2, limited = false)
        }

    @Test
    public fun `step wraps around in both directions`(): Unit =
        runTest {
            val session = showing("hit 1", "hit 2", "hit 3")
            val search = session.search
            search.find(SearchQuery("hit")).hit().current shouldBe 2

            search.step(SearchDirection.UP).hit().current shouldBe 1
            search.step(SearchDirection.UP).hit().current shouldBe 0
            search.step(SearchDirection.UP).hit().current shouldBe 2
            search.step(SearchDirection.DOWN).hit().current shouldBe 0
            search.step(SearchDirection.DOWN).hit().current shouldBe 1
        }

    @Test
    public fun `step without a search returns NONE`(): Unit =
        runTest {
            val session = showing("abc")

            session.search.step(SearchDirection.UP) shouldBe Outcome.Ok(SearchResult.NONE)
            session.search.step(SearchDirection.DOWN) shouldBe Outcome.Ok(SearchResult.NONE)
        }

    @Test
    public fun `search works after the process exited`(): Unit =
        runTest {
            val session = showing("one", "two", "one more")
            endProcess(session)

            session.search
                .find(SearchQuery("one"))
                .hit()
                .count shouldBe 2
        }

    @Test
    public fun `find after close returns Closed`(): Unit =
        runTest {
            val session = showing("abc")
            session.close()

            session.search.find(SearchQuery("abc")).errorOrNull() shouldBe SearchError.Closed
        }

    @Test
    public fun `clear twice is harmless`(): Unit =
        runTest {
            val session = showing("abc")
            session.search
                .find(SearchQuery("abc"))
                .hit()
                .count shouldBe 1

            session.search.clear()
            session.search.clear()

            session.search.step(SearchDirection.UP) shouldBe Outcome.Ok(SearchResult.NONE)
        }
}
