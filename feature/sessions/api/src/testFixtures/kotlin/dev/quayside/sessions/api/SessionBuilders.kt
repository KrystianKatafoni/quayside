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
): Session = Session(SessionId(id), project, name, cwd, state, startedAt, lastActivityAt, summary)

public fun aNewSessionSpec(
    prompt: String = "Fix the login bug",
    name: String? = "fix-login",
): NewSessionSpec = NewSessionSpec(prompt = prompt, name = name)
