package dev.quayside.sessions.api

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import java.nio.file.Path
import java.time.Instant

// Test data builders for sessions types: sensible defaults, override only what the test is about.

/** Working directory of the default [aSession]; does not exist on disk. */
public val A_SESSION_CWD: Path = Path.of("/Users/test/projects/agent-manager")

public fun aSession(
    id: String = "7c5dcf5d",
    project: ProjectId? = ProjectId("agent-manager"),
    state: SessionState = SessionState.Working,
    cwd: Path = A_SESSION_CWD,
    name: String = "fix-login",
    startedAt: Instant = T0,
    lastActivityAt: Instant = startedAt,
    summary: String? = null,
    meta: SessionMeta = SessionMeta(),
): Session = Session(SessionId(id), project, name, cwd, state, startedAt, lastActivityAt, summary, meta)

public fun aNewSessionSpec(
    prompt: String = "Fix the login bug",
    name: String? = "fix-login",
    subfolder: String? = null,
): NewSessionSpec = NewSessionSpec(prompt = prompt, name = name, subfolder = subfolder)

/** UUID of the default [pastSession]. */
public const val A_PAST_UUID: String = "0b7e5c1e-3f4a-4d8b-9a51-2c6f0e9d1a77"

/**
 * A session read from disk (FR-P9): stopped, named by [name], the UUID as id, [SessionOrigin.DISK]. Not the output of
 * a fake: what a listing of a [WorldPastSession] must give.
 */
public fun pastSession(
    id: String = A_PAST_UUID,
    project: ProjectId? = ProjectId("agent-manager"),
    cwd: Path = A_SESSION_CWD,
    name: String = "Fix the login bug",
    startedAt: Instant = T0,
    lastActivityAt: Instant = startedAt,
): Session =
    Session(
        SessionId(id),
        project,
        name,
        cwd,
        SessionState.Stopped,
        startedAt,
        lastActivityAt,
        summary = null,
        meta = SessionMeta(),
        origin = SessionOrigin.DISK,
    )
