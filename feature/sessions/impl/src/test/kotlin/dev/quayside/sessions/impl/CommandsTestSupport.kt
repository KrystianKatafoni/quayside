@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.agentruntime.api.StartSpec
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.impl.MonitorFixtures.P1
import dev.quayside.sessions.impl.MonitorFixtures.P1_DIR
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import java.nio.file.Path
import kotlin.time.Duration

// Shared support of the AM-404 command tests: a supervisor that lags behind its commands, a runtime
// whose listings take scripted times, and the commands over one SessionsState without a polling loop.

/**
 * [AgentRuntime] over a [FakeAgentRuntime] whose listings lag behind its commands, like a supervisor that
 * reports the effect of `--bg`, `stop` or `rm` only later.
 *
 * After every successful command: the first listing fails with [failAfterCommand] when set, then [lag]
 * listings still show the supervisor as it was before the command, then listings are live again.
 * [failListings] fails every listing while set. [fail] scripts the result of one command call.
 * [commandCalls] records every command call (not listings), e.g. `start`, `stop aaaa0001`.
 */
internal class LaggingRuntime(
    val fake: FakeAgentRuntime,
) : AgentRuntime by fake {
    /** Listings after each successful command that still show the supervisor before it. */
    var lag: Int = 0

    /** When set, the first listing after each successful command fails with it. */
    var failAfterCommand: RuntimeError? = null

    /** When set, every listing fails with it. */
    var failListings: RuntimeError? = null

    private val calls = MutableStateFlow(emptyList<String>())
    private val scripted = MutableStateFlow(emptyMap<String, Scripted>())
    private var frozen: List<RuntimeSession> = emptyList()
    private var lagLeft = 0
    private var failureLeft: RuntimeError? = null

    /** Command calls so far, in order: `start`, `stop <id>`, `respawn <id>`, `remove <id>`. */
    val commandCalls: List<String> get() = calls.value

    /**
     * Every later [call] (e.g. `stop aaaa0001`) returns [error] without reaching the supervisor; with
     * [vanish] the supervisor drops the session first, as if it had been removed meanwhile.
     */
    fun fail(
        call: String,
        error: RuntimeError,
        vanish: Boolean = false,
    ) {
        scripted.update { it + (call to Scripted(error, vanish)) }
    }

    override suspend fun listSessions(under: Path?): Outcome<List<RuntimeSession>, RuntimeError> {
        val afterCommand = failureLeft
        return when {
            failListings != null -> {
                Outcome.Err(failListings ?: RuntimeError.Timeout)
            }

            afterCommand != null -> {
                failureLeft = null
                Outcome.Err(afterCommand)
            }

            lagLeft > 0 -> {
                lagLeft--
                Outcome.Ok(frozen.filter { under == null || it.cwd.startsWith(under) })
            }

            else -> {
                fake.listSessions(under)
            }
        }
    }

    override suspend fun start(spec: StartSpec): Outcome<SessionId, RuntimeError> =
        command("start", null) {
            fake.start(spec)
        }

    override suspend fun stop(id: SessionId): Outcome<Unit, RuntimeError> = command("stop", id) { fake.stop(id) }

    override suspend fun respawn(id: SessionId): Outcome<Unit, RuntimeError> =
        command("respawn", id) { fake.respawn(id) }

    override suspend fun remove(id: SessionId): Outcome<Unit, RuntimeError> = command("remove", id) { fake.remove(id) }

    private suspend fun <T> command(
        name: String,
        id: SessionId?,
        run: suspend () -> Outcome<T, RuntimeError>,
    ): Outcome<T, RuntimeError> {
        val call = if (id == null) name else "$name ${id.value}"
        calls.update { it + call }
        scripted.value[call]?.let { script ->
            if (script.vanish && id != null) fake.remove(id)
            return Outcome.Err(script.error)
        }
        val before = (fake.listSessions(null) as? Outcome.Ok)?.value.orEmpty()
        val result = run()
        if (result is Outcome.Ok) {
            if (lag > 0) {
                frozen = before
                lagLeft = lag
            }
            failureLeft = failAfterCommand
        }
        return result
    }

    private data class Scripted(
        val error: RuntimeError,
        val vanish: Boolean,
    )
}

/**
 * [AgentRuntime] over a [FakeAgentRuntime] whose listing reads the supervisor when it starts and returns
 * after the next of [listingTimes] (zero once they run out), so a slow listing returns an older view.
 */
internal class DelayedListingRuntime(
    val fake: FakeAgentRuntime,
    listingTimes: List<Duration>,
) : AgentRuntime by fake {
    private val times = ArrayDeque(listingTimes)

    override suspend fun listSessions(under: Path?): Outcome<List<RuntimeSession>, RuntimeError> {
        val listing = fake.listSessions(under)
        val time = times.removeFirstOrNull() ?: Duration.ZERO
        if (time.isPositive()) delay(time)
        return listing
    }
}

/** The commands under test over one [SessionsState], with project P1 at [P1_DIR]; no polling loop. */
internal class CommandsWorld(
    val fake: FakeAgentRuntime,
    val runtime: LaggingRuntime,
    val projects: FakeProjectsApi,
    val logger: RecordingLogger,
    val state: SessionsState,
    val commands: RuntimeSessionCommands,
) {
    val snapshot: SessionsSnapshot get() = state.snapshot.value

    /** One listing published through the state, as the polling loop does. */
    suspend fun listOnce(): Outcome<SessionsSnapshot, RuntimeError> = state.refresh()

    fun session(id: SessionId): Session = snapshot.sessions.single { it.id == id }

    fun stateOf(id: String): SessionState = session(SessionId(id)).state

    fun listedIds(): List<SessionId> = snapshot.sessions.map { it.id }
}

/** Builds [CommandsWorld] over a supervisor with [sessions], on the virtual clock of the test. */
internal fun TestScope.commandsWorld(vararg sessions: RuntimeSession): CommandsWorld {
    val clock = virtualClock()
    val fake = FakeAgentRuntime(RuntimeWorld(sessions = sessions.toList()), clock)
    val runtime = LaggingRuntime(fake)
    val projects = FakeProjectsApi(listOf(Project(P1, P1_DIR, "p1")))
    val logger = RecordingLogger()
    val state = SessionsState(runtime, projects, clock, COMMAND_OVERLAY_TTL)
    val commands = RuntimeSessionCommands(runtime, projects, state, clock, logger)
    return CommandsWorld(fake, runtime, projects, logger, state, commands)
}
