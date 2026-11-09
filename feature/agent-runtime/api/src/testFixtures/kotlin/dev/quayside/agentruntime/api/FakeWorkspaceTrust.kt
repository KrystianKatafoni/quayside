package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/**
 * Scriptable in-memory [WorkspaceTrust] for tests of features that ask the user to trust a directory. Passes
 * [WorkspaceTrustContract].
 *
 * Trusting a directory records it in [trusted] (once; a second call changes nothing). A path for which
 * [directoryExists] is false gives [TrustError.DIRECTORY_MISSING]; the default accepts every path, so tests with
 * made-up project paths need no disk. A scripted [failNext] comes first and records nothing.
 */
public class FakeWorkspaceTrust(
    private val directoryExists: (Path) -> Boolean = { true },
) : WorkspaceTrust {
    private val state = AtomicReference(State())

    /** Directories trusted so far, in order, each once, exactly as passed to [trust]. */
    public val trusted: List<Path> get() = state.get().trusted

    /** Every directory [trust] was called with, in call order, also the failed calls. */
    public val calls: List<Path> get() = state.get().calls

    /** Makes the next [trust] return [error] without recording the directory. */
    public fun failNext(error: TrustError) {
        state.updateAndGet { it.copy(nextFailure = error) }
    }

    override suspend fun trust(dir: Path): Outcome<Unit, TrustError> {
        while (true) {
            val before = state.get()
            val recorded = before.copy(calls = before.calls + listOf(dir), nextFailure = null)
            val failure = before.nextFailure ?: TrustError.DIRECTORY_MISSING.takeUnless { directoryExists(dir) }
            val after =
                when {
                    failure != null -> recorded
                    dir in recorded.trusted -> recorded
                    else -> recorded.copy(trusted = recorded.trusted + listOf(dir))
                }
            if (state.compareAndSet(before, after)) {
                return if (failure != null) Outcome.Err(failure) else Outcome.Ok(Unit)
            }
        }
    }

    // `list + path` would add the path's name elements: a Path is an Iterable<Path>.
    private data class State(
        val trusted: List<Path> = emptyList(),
        val calls: List<Path> = emptyList(),
        val nextFailure: TrustError? = null,
    )
}
