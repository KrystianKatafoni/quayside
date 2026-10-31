package dev.agentmanager.sessions.api

import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.testing.T0
import java.nio.file.Path
import java.time.Instant

/** A registered project of a [SessionsWorld]; its directory does not have to exist on disk. */
public data class WorldProject(
    val id: ProjectId,
    val dir: Path,
)

/**
 * A session the supervisor of a [SessionsWorld] knows about. Which project it belongs to follows
 * from [cwd] (see [SessionRules.projectFor]).
 */
public data class WorldSession(
    val id: SessionId,
    val cwd: Path,
    val state: SessionState = SessionState.Working,
    val name: String = "fix-login",
    val startedAt: Instant = T0,
)

/**
 * World state a sessions contract starts from. [FakeSessions] takes it directly; the `sessions:impl`
 * tests (AM-403/404) turn it into `FakeProjectsApi` and `FakeAgentRuntime` (+ `FakeAgentEventSource`).
 *
 * @property projects registered projects.
 * @property sessions sessions the supervisor already runs.
 * @property outputs output tail of sessions by id; a session without an entry has printed nothing yet.
 */
public data class SessionsWorld(
    val projects: List<WorldProject> = emptyList(),
    val sessions: List<WorldSession> = emptyList(),
    val outputs: Map<SessionId, String> = emptyMap(),
)
