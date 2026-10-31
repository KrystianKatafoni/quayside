package dev.quayside.agentruntime.api

import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.aSessionId
import java.nio.file.Path
import java.time.Instant

// Test data builders for agent-runtime types: sensible defaults, override only what the test is about.

public val A_PROJECT_DIR: Path = Path.of("/Users/test/projects/agent-manager")

public fun aConversationId(value: String = "0b7e5c1e-3f4a-4d8b-9a51-2c6f0e9d1a77"): ConversationId =
    ConversationId(value)

public fun aRuntimeSession(
    id: SessionId = aSessionId(),
    conversationId: ConversationId? = aConversationId(),
    name: String = "fix-login",
    cwd: Path = A_PROJECT_DIR,
    phase: RuntimePhase = RuntimePhase.WORKING,
    waitingFor: WaitingFor? = null,
    startedAt: Instant = T0,
    pid: Long? = 4242L,
): RuntimeSession = RuntimeSession(id, conversationId, name, cwd, phase, waitingFor, startedAt, pid)

public fun aStartSpec(
    cwd: Path = A_PROJECT_DIR,
    prompt: String = "Fix the login bug",
    name: String? = "fix-login",
): StartSpec = StartSpec(cwd = cwd, prompt = prompt, name = name)

public fun anEventMeta(
    conversationId: ConversationId = aConversationId(),
    cwd: Path = A_PROJECT_DIR,
    at: Instant = T0,
): EventMeta = EventMeta(conversationId, cwd, at)
