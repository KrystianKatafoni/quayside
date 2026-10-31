package dev.agentmanager.agentruntime.api

import dev.agentmanager.agentruntime.api.RuntimeRequirements.MIN_CLAUDE_VERSION
import dev.agentmanager.core.domain.Clock
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.testing.TestClock
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/**
 * Scriptable in-memory [AgentRuntime] for tests of features that run agents. Passes
 * [AgentRuntimeContract] and [AgentRuntimeHealthContract].
 *
 * Started sessions get ids `fake0001`, `fake0002`, … and [Clock.now] as their start time. When
 * [RuntimeWorld.installed] is false every `suspend` operation returns [RuntimeError.NotInstalled].
 */
public class FakeAgentRuntime(
    private val world: RuntimeWorld = RuntimeWorld(),
    private val clock: Clock = TestClock(),
) : AgentRuntime {
    private val state = AtomicReference(FakeRuntimeState(sessions = world.sessions))

    /** Operations called so far, in order, e.g. `listSessions`, `start`, `stop 7c5dcf5d`. */
    public val calls: List<String> get() = state.get().calls

    /** Adds [session] to the supervisor list, replacing a session with the same id. */
    public fun hasSession(session: RuntimeSession) {
        state.updateAndGet { it.copy(sessions = it.sessions.filterNot { s -> s.id == session.id } + session) }
    }

    /** Changes the phase of the existing session [id], as if the agent moved on by itself. */
    public fun setPhase(
        id: SessionId,
        phase: RuntimePhase,
        waitingFor: WaitingFor? = null,
    ) {
        state.updateAndGet { current ->
            require(current.sessions.any { it.id == id }) { "FakeAgentRuntime has no session $id" }
            current.withSession(id) { it.copy(phase = phase, waitingFor = waitingFor) }
        }
    }

    /** Makes the next `suspend` operation except [health] return [error]; later calls work again. */
    public fun failNext(error: RuntimeError) {
        state.updateAndGet { it.copy(nextFailure = error) }
    }

    override suspend fun listSessions(under: Path?): Outcome<List<RuntimeSession>, RuntimeError> =
        transact("listSessions") { current ->
            current to Outcome.Ok(current.sessions.filter { under == null || it.cwd.startsWith(under) })
        }

    override suspend fun start(spec: StartSpec): Outcome<SessionId, RuntimeError> {
        val startedAt = clock.now()
        return transact("start") { current ->
            val id = SessionId("fake%04d".format(current.startedCount + 1))
            val session =
                RuntimeSession(id, null, spec.name ?: id.value, spec.cwd, RuntimePhase.WORKING, null, startedAt, null)
            current.copy(sessions = current.sessions + session, startedCount = current.startedCount + 1) to
                Outcome.Ok(id)
        }
    }

    override suspend fun stop(id: SessionId): Outcome<Unit, RuntimeError> =
        changeSession("stop", id) { it.copy(phase = RuntimePhase.STOPPED, waitingFor = null, pid = null) }

    override suspend fun respawn(id: SessionId): Outcome<Unit, RuntimeError> =
        changeSession("respawn", id) { it.copy(phase = RuntimePhase.WORKING, waitingFor = null) }

    override suspend fun remove(id: SessionId): Outcome<Unit, RuntimeError> =
        transact("remove ${id.value}") { current ->
            current.ifKnown(id) {
                current.copy(sessions = current.sessions.filterNot { it.id == id }) to
                    Outcome.Ok(Unit)
            }
        }

    override suspend fun recentOutput(id: SessionId): Outcome<String, RuntimeError> =
        transact("recentOutput ${id.value}") { current ->
            current.ifKnown(id) { current to Outcome.Ok(world.outputs[id].orEmpty()) }
        }

    override fun attachCommand(id: SessionId): ShellCommand =
        ShellCommand(argv = listOf("claude", "attach", id.value), workDir = Path.of("/"))

    override suspend fun health(): RuntimeHealth {
        state.updateAndGet { it.copy(calls = it.calls + "health") }
        return fakeHealth(world)
    }

    private fun changeSession(
        name: String,
        id: SessionId,
        change: (RuntimeSession) -> RuntimeSession,
    ): Outcome<Unit, RuntimeError> =
        transact("$name ${id.value}") { current ->
            current.ifKnown(id) { current.withSession(id, change) to Outcome.Ok(Unit) }
        }

    /** Records [call] and applies [operation] atomically, unless a scripted failure or a missing CLI comes first. */
    private fun <T> transact(
        call: String,
        operation: (FakeRuntimeState) -> Pair<FakeRuntimeState, Outcome<T, RuntimeError>>,
    ): Outcome<T, RuntimeError> {
        while (true) {
            val before = state.get()
            val recorded = before.copy(calls = before.calls + call, nextFailure = null)
            val failure = before.nextFailure ?: RuntimeError.NotInstalled.takeUnless { world.installed }
            val (after, result) = if (failure != null) recorded to Outcome.Err(failure) else operation(recorded)
            if (state.compareAndSet(before, after)) return result
        }
    }
}

private data class FakeRuntimeState(
    val sessions: List<RuntimeSession>,
    val calls: List<String> = emptyList(),
    val nextFailure: RuntimeError? = null,
    val startedCount: Int = 0,
) {
    fun withSession(
        id: SessionId,
        change: (RuntimeSession) -> RuntimeSession,
    ): FakeRuntimeState = copy(sessions = sessions.map { if (it.id == id) change(it) else it })

    fun <T> ifKnown(
        id: SessionId,
        operation: () -> Pair<FakeRuntimeState, Outcome<T, RuntimeError>>,
    ): Pair<FakeRuntimeState, Outcome<T, RuntimeError>> =
        if (sessions.any { it.id == id }) operation() else this to Outcome.Err(RuntimeError.UnknownSession(id))
}

private fun fakeHealth(world: RuntimeWorld): RuntimeHealth {
    val version = world.version.takeIf { world.installed }
    val problems =
        listOfNotNull(
            RuntimeError.NotInstalled.takeIf { version == null },
            version
                ?.takeUnless { isAtLeast(it, MIN_CLAUDE_VERSION) }
                ?.let { RuntimeError.UnsupportedVersion(found = it, required = MIN_CLAUDE_VERSION) },
            RuntimeError.AgentViewDisabled.takeUnless { world.agentViewEnabled },
        )
    return RuntimeHealth(version, world.agentViewEnabled, supervisorRunning = world.installed, problems = problems)
}

/** Compares dotted numeric versions (`2.1.139`); a part that is not a number counts as 0. */
private fun isAtLeast(
    found: String,
    required: String,
): Boolean {
    val have = found.split('.').map { it.toIntOrNull() ?: 0 }
    val need = required.split('.').map { it.toIntOrNull() ?: 0 }
    val firstDifference = have.zip(need).firstOrNull { (h, n) -> h != n }
    return firstDifference?.let { (h, n) -> h > n } ?: (have.size >= need.size)
}
