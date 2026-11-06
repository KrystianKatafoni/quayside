package dev.quayside.projects.ui

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory [ProjectHooks] with a state per folder (default [ProjectHooksState.ABSENT]). A successful uninstall sets
 * the folder to ABSENT. [calls] lists `status <dir>` and `uninstall <dir>` in order.
 *
 * [holdStatus] and [holdUninstall] make the call of one folder wait for the returned deferred. The wait is not
 * cancellable, so a result can arrive after the caller was cancelled, the way a blocking read finishes late; the
 * state is read before the wait, so the late result is the old one.
 */
internal class FakeProjectHooks : ProjectHooks {
    private val states = ConcurrentHashMap<Path, ProjectHooksState>()
    private val statusGates = ConcurrentHashMap<Path, CompletableDeferred<Unit>>()
    private val uninstallGates = ConcurrentHashMap<Path, CompletableDeferred<Unit>>()
    private val recorded = CopyOnWriteArrayList<String>()
    private var failure: Failure? = null

    /** What happens on the next [uninstall]: [error] and, if given, the file [then] changes to this state. */
    private class Failure(
        val error: ProjectHooksError,
        val then: ProjectHooksState?,
    )

    val calls: List<String> get() = recorded.toList()

    /** [status] of [dir] reports [state] from now on. */
    fun has(
        dir: Path,
        state: ProjectHooksState,
    ) {
        states[dir] = state
    }

    /** The next [uninstall] fails with [error]; the file then changes to [then] (null = stays as it was). */
    fun failNextUninstall(
        error: ProjectHooksError,
        then: ProjectHooksState? = null,
    ) {
        failure = Failure(error, then)
    }

    /** [status] of [dir] waits until the returned deferred is completed. */
    fun holdStatus(dir: Path): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { statusGates[dir] = it }

    /** [uninstall] of [dir] waits until the returned deferred is completed. */
    fun holdUninstall(dir: Path): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { uninstallGates[dir] = it }

    override suspend fun status(dir: Path): ProjectHooksState {
        recorded += "status $dir"
        val answer = states[dir] ?: ProjectHooksState.ABSENT
        statusGates[dir]?.let { gate -> withContext(NonCancellable) { gate.await() } }
        return answer
    }

    override suspend fun uninstall(dir: Path): Outcome<Unit, ProjectHooksError> {
        recorded += "uninstall $dir"
        uninstallGates[dir]?.let { gate -> withContext(NonCancellable) { gate.await() } }
        val scripted = failure
        failure = null
        if (scripted != null) {
            scripted.then?.let { states[dir] = it }
            return Outcome.Err(scripted.error)
        }
        states[dir] = ProjectHooksState.ABSENT
        return Outcome.Ok(Unit)
    }
}
