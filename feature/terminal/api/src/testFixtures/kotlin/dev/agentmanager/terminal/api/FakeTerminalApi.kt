package dev.agentmanager.terminal.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ShellCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * In-memory [TerminalApi] for tests of other features: no process, no PTY, a plain Swing panel as the
 * component. A test ends a "process" with [FakeTerminalSession.finish] and reads typed text from
 * [FakeTerminalSession.sent].
 */
public class FakeTerminalApi : TerminalApi {
    private val counter = AtomicInteger()
    private val opened = CopyOnWriteArrayList<FakeTerminalSession>()

    @Volatile
    private var closed = false

    /** Every session opened so far, in order (closed ones included). */
    public val sessions: List<FakeTerminalSession> get() = opened.toList()

    /** Commands passed to [openCommand] that succeeded, in order. */
    public val commands: List<ShellCommand> get() = ranCommands.toList()

    private val ranCommands = CopyOnWriteArrayList<ShellCommand>()

    override suspend fun openShell(dir: Path): Outcome<TerminalSession, TerminalError> = open(dir)

    override suspend fun openCommand(command: ShellCommand): Outcome<TerminalSession, TerminalError> =
        open(command.workDir).also { if (it is Outcome.Ok) ranCommands += command }

    private fun open(dir: Path): Outcome<TerminalSession, TerminalError> =
        when {
            closed -> {
                Outcome.Err(TerminalError.Closed)
            }

            !Files.isDirectory(dir) -> {
                Outcome.Err(TerminalError.WorkDirMissing)
            }

            else -> {
                Outcome.Ok(
                    FakeTerminalSession(TerminalId("fake-${counter.incrementAndGet()}")).also { opened += it },
                )
            }
        }

    override fun close() {
        closed = true
        opened.forEach { it.close() }
    }
}

/** Session of [FakeTerminalApi]; running until [finish] or [close]. */
public class FakeTerminalSession internal constructor(
    override val id: TerminalId,
) : TerminalSession {
    private val exit = MutableStateFlow<Int?>(null)
    private val typed = CopyOnWriteArrayList<String>()
    private val panel: JPanel by lazy { JPanel() }
    private val screen = CopyOnWriteArrayList<String>()
    private val searching = FakeTerminalSearch(rows = { screen.toList() }, closed = { closed })

    @Volatile
    private var closed = false

    override val exitCode: StateFlow<Int?> get() = exit

    /** Everything accepted by [send], in order. */
    public val sent: List<String> get() = typed.toList()

    /** Search over the lines added with [print]; see [FakeTerminalSearch] for what a test can observe. */
    override val search: FakeTerminalSearch get() = searching

    /** True after [close]. */
    public val isClosed: Boolean get() = closed

    /** Appends [line] to the simulated screen, the text [search] looks in (oldest line first). */
    public fun print(line: String) {
        screen += line
    }

    /** Simulates the process ending with [code]; the session stays open. */
    public fun finish(code: Int) {
        exit.value = code
    }

    override fun component(): JComponent = panel

    override suspend fun send(text: String): Outcome<Unit, TerminalError> =
        if (closed || exit.value != null) {
            Outcome.Err(TerminalError.Closed)
        } else {
            typed += text
            Outcome.Ok(Unit)
        }

    override fun close() {
        closed = true
    }
}

/**
 * [TerminalSearch] over the lines of a [FakeTerminalSession]: same matching as `impl` ([ScrollbackMatcher]),
 * no widget. A test reads what was asked from [queries], [steps], [clears] and what is highlighted from
 * [highlighted] and [current]; [gate] holds [find] until completed (a search in flight).
 */
public class FakeTerminalSearch internal constructor(
    private val rows: () -> List<String>,
    private val closed: () -> Boolean,
) : TerminalSearch {
    private val asked = CopyOnWriteArrayList<SearchQuery>()
    private val stepped = CopyOnWriteArrayList<SearchDirection>()
    private val cleared = AtomicInteger()
    private val finished = AtomicInteger()

    @Volatile
    private var hits: List<SearchMatch> = emptyList()

    @Volatile
    private var index: Int? = null

    @Volatile
    private var limited = false

    /** When set, [find] suspends on it after being recorded; cancelling the caller drops the search. */
    @Volatile
    public var gate: CompletableDeferred<Unit>? = null

    /** Every query passed to [find], in order, recorded when the call starts. */
    public val queries: List<SearchQuery> get() = asked.toList()

    /** Number of [find] calls that ran to the end (not cancelled while on the [gate]). */
    public val completedFinds: Int get() = finished.get()

    /** Every direction passed to [step], in order. */
    public val steps: List<SearchDirection> get() = stepped.toList()

    /** How many times [clear] was called. */
    public val clears: Int get() = cleared.get()

    /** The hits shown now; empty after [clear] or an unsuccessful [find]. */
    public val highlighted: List<SearchMatch> get() = hits

    /** The selected hit, null without hits. */
    public val current: SearchMatch? get() = index?.let { hits.getOrNull(it) }

    override suspend fun find(query: SearchQuery): Outcome<SearchResult, SearchError> {
        asked += query
        gate?.await()
        finished.incrementAndGet()
        if (closed()) return Outcome.Err(SearchError.Closed)
        if (query.text.isEmpty()) {
            forget()
            return Outcome.Ok(SearchResult.NONE)
        }
        return when (val found = ScrollbackMatcher.find(rows(), query)) {
            is Outcome.Err -> {
                forget()
                found
            }

            is Outcome.Ok -> {
                hits = found.value
                limited = hits.size >= ScrollbackMatcher.MAX_MATCHES
                index = hits.indices.lastOrNull()
                Outcome.Ok(result())
            }
        }
    }

    override suspend fun step(direction: SearchDirection): Outcome<SearchResult, SearchError> {
        stepped += direction
        if (closed()) return Outcome.Err(SearchError.Closed)
        val from = index
        if (hits.isEmpty() || from == null) return Outcome.Ok(SearchResult.NONE)
        val delta = if (direction == SearchDirection.UP) -1 else 1
        index = Math.floorMod(from + delta, hits.size)
        return Outcome.Ok(result())
    }

    override suspend fun clear() {
        cleared.incrementAndGet()
        forget()
    }

    private fun forget() {
        hits = emptyList()
        index = null
        limited = false
    }

    private fun result(): SearchResult =
        if (hits.isEmpty()) SearchResult.NONE else SearchResult(hits.size, index, limited)
}
