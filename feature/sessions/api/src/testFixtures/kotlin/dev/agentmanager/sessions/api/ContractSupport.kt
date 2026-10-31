package dev.agentmanager.sessions.api

import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.SessionId
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import java.nio.file.Path

// Shared world and helpers of the sessions contracts.

internal val P1 = ProjectId("p1")
internal val P2 = ProjectId("p2")
internal val P1_DIR: Path = Path.of("/Users/test/projects/p1")
internal val P2_DIR: Path = Path.of("/Users/test/projects/p2")
internal val NESTED = ProjectId("nested")
internal val NESTED_DIR: Path = P1_DIR.resolve("modules/nested")
internal val OUTSIDE_DIR: Path = Path.of("/Users/test/elsewhere")

internal fun sessionIn(
    id: String,
    cwd: Path,
    state: SessionState = SessionState.Working,
): WorldSession = WorldSession(SessionId(id), cwd, state)

internal fun projects(vararg projects: Pair<ProjectId, Path>): List<WorldProject> =
    projects.map { (id, dir) -> WorldProject(id, dir) }

/** The session [id] in the current snapshot; fails when it is not listed. */
internal fun SessionsHarness.session(id: SessionId): Session =
    monitor.snapshot.value.sessions
        .firstOrNull { it.id == id }
        .shouldNotBeNull()

internal fun SessionsHarness.stateOf(id: SessionId): SessionState = session(id).state

internal fun SessionsHarness.listedIds(): List<SessionId> =
    monitor.snapshot.value.sessions
        .map { it.id }

/** Collects every event from now on, in the test's background scope. */
internal fun TestScope.recordEvents(monitor: SessionMonitor): List<SessionEvent> {
    val events = mutableListOf<SessionEvent>()
    backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { monitor.events.collect { events += it } }
    return events
}
