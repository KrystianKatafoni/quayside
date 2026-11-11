package dev.quayside.sessions.api

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import java.nio.file.Path
import java.time.Instant

/**
 * A registered project of a [SessionsWorld]; its directory does not have to exist on disk.
 *
 * @property subfolders names of the project's subfolders (FR-P7), as `Project.subfolders` holds them; empty = no group.
 * @property missing those of [subfolders] that are not a directory on disk (deleted, a file or a symlink).
 */
public data class WorldProject(
    val id: ProjectId,
    val dir: Path,
    val subfolders: List<String> = emptyList(),
    val missing: Set<String> = emptySet(),
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
    val meta: SessionMeta = SessionMeta(),
    val conversationId: String? = null,
)

/**
 * A session file on the disk of a [SessionsWorld] that the supervisor may not know (FR-P9). [id] is the conversation
 * UUID. A file shows up as a disk session of the project that contains [cwd] when it is not too old, not hidden, not
 * cut by the count limit and not a conversation of a supervisor session.
 *
 * @property title first user message; null = none (the session is then named by 8 characters of [id]).
 * @property name the name from the file (`custom-title`, AM-315); it wins over [title] as the session name.
 * @property lastPrompt the last user message of the file; with [title] it makes the summary of the session.
 * @property startedAt null = unknown (the session then starts at [modifiedAt]).
 * @property lastActivityAt null = unknown (the session then was last active at [modifiedAt]).
 * @property modifiedAt modification time of the file; age and the count limit are judged by it.
 */
public data class WorldPastSession(
    val id: SessionId,
    val cwd: Path,
    val modifiedAt: Instant = T0,
    val title: String? = "Fix the login bug",
    val startedAt: Instant? = null,
    val lastActivityAt: Instant? = null,
    val name: String? = null,
    val lastPrompt: String? = null,
)

/**
 * World state a sessions contract starts from. [FakeSessions] takes it directly; the `sessions:impl`
 * tests (AM-403/404) turn it into `FakeProjectsApi` and `FakeAgentRuntime` (+ `FakeAgentEventSource`).
 *
 * @property projects registered projects.
 * @property sessions sessions the supervisor already runs.
 * @property outputs output tail of sessions by id; a session without an entry has printed nothing yet.
 * @property pastSessions session files on disk (FR-P9), found by the first scan.
 */
public data class SessionsWorld(
    val projects: List<WorldProject> = emptyList(),
    val sessions: List<WorldSession> = emptyList(),
    val outputs: Map<SessionId, String> = emptyMap(),
    val pastSessions: List<WorldPastSession> = emptyList(),
)
