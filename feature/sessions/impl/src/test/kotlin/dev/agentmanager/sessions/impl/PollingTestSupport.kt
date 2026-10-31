@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.agentmanager.sessions.impl

import dev.agentmanager.agentruntime.api.AgentRuntime
import dev.agentmanager.agentruntime.api.FakeAgentRuntime
import dev.agentmanager.agentruntime.api.RuntimeError
import dev.agentmanager.agentruntime.api.RuntimeSession
import dev.agentmanager.agentruntime.api.StartSpec
import dev.agentmanager.core.domain.Clock
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.testing.T0
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

// Shared support of the AM-403 monitor tests: virtual time, a runtime that can be switched off and
// records when it was listed, and event recording.

/** Clock bound to the virtual time of the test: `T0` plus the scheduler's current time. */
internal fun TestScope.virtualClock(): Clock = Clock { T0.plusMillis(testScheduler.currentTime) }

/** `T0` plus [millis] of virtual time. */
internal fun atMillis(millis: Long) = T0.plusMillis(millis)

/** Advances virtual time by [duration] and runs what is due at the new time; never `advanceUntilIdle`. */
internal fun TestScope.advance(duration: Duration) {
    advanceTimeBy(duration)
    runCurrent()
}

/** A scope that is a child of `backgroundScope`, so a test can cancel one monitor alone. */
internal fun TestScope.childScope(): CoroutineScope =
    CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))

/** Collects every element of [flow] from now on, in the test's background scope. */
internal fun <T> TestScope.record(flow: Flow<T>): List<T> {
    val recorded = mutableListOf<T>()
    backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { flow.collect { recorded += it } }
    return recorded
}

/**
 * [AgentRuntime] over a [FakeAgentRuntime] that can be switched off: while off every `suspend`
 * operation returns [RuntimeError.NotInstalled] (`FakeAgentRuntime.failNext` fails only once).
 * Records the virtual time at which each listing started; a listing takes [listingTime]. A stop of a
 * session passed to [failStop] returns [RuntimeError.CommandFailed] and leaves the session running.
 */
internal class SwitchableRuntime(
    val fake: FakeAgentRuntime,
    private val now: () -> Long,
    private val listingTime: Duration = Duration.ZERO,
) : AgentRuntime by fake {
    private val down = AtomicBoolean(false)
    private val listed = MutableStateFlow(emptyList<Long>())
    private val failingStops = MutableStateFlow(emptySet<SessionId>())

    /** Virtual time in milliseconds at which each `listSessions` call started, in order. */
    val listedAtMillis: List<Long> get() = listed.value

    fun available(available: Boolean) {
        down.set(!available)
    }

    /** Every later stop of [id] fails with [RuntimeError.CommandFailed]; the session keeps running. */
    fun failStop(id: SessionId) {
        failingStops.update { it + id }
    }

    override suspend fun listSessions(under: Path?): Outcome<List<RuntimeSession>, RuntimeError> {
        listed.update { it + now() }
        if (listingTime.isPositive()) delay(listingTime)
        return if (down.get()) Outcome.Err(RuntimeError.NotInstalled) else fake.listSessions(under)
    }

    override suspend fun start(spec: StartSpec): Outcome<SessionId, RuntimeError> =
        if (down.get()) Outcome.Err(RuntimeError.NotInstalled) else fake.start(spec)

    override suspend fun stop(id: SessionId): Outcome<Unit, RuntimeError> =
        when {
            down.get() -> Outcome.Err(RuntimeError.NotInstalled)
            id in failingStops.value -> Outcome.Err(RuntimeError.CommandFailed(exitCode = 1, stderrTail = STOP_FAILED))
            else -> fake.stop(id)
        }

    override suspend fun respawn(id: SessionId): Outcome<Unit, RuntimeError> =
        if (down.get()) Outcome.Err(RuntimeError.NotInstalled) else fake.respawn(id)

    override suspend fun remove(id: SessionId): Outcome<Unit, RuntimeError> =
        if (down.get()) Outcome.Err(RuntimeError.NotInstalled) else fake.remove(id)

    override suspend fun recentOutput(id: SessionId): Outcome<String, RuntimeError> =
        if (down.get()) Outcome.Err(RuntimeError.NotInstalled) else fake.recentOutput(id)

    private companion object {
        const val STOP_FAILED = "stop failed"
    }
}
