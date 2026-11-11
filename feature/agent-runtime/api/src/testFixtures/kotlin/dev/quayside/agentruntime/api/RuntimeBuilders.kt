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

/** UUIDs of the default past conversations of the history tests. */
public const val A_CONVERSATION_UUID: String = "0b7e5c1e-3f4a-4d8b-9a51-2c6f0e9d1a77"

public fun aPastConversation(
    conversationId: ConversationId = aConversationId(A_CONVERSATION_UUID),
    startCwd: Path = A_PROJECT_DIR,
    startedAt: Instant? = T0,
    lastActivityAt: Instant? = T0.plusSeconds(60),
    modifiedAt: Instant = T0.plusSeconds(60),
    cwdChanged: Boolean = false,
    title: String? = "Fix the login bug",
    name: String? = null,
    lastPrompt: String? = null,
): PastConversation =
    PastConversation(
        conversationId,
        startCwd,
        startedAt,
        lastActivityAt,
        modifiedAt,
        cwdChanged,
        title,
        name,
        lastPrompt,
    )

/** A message of a conversation; [text] "message N" when only [n] is given. */
public fun aConversationMessage(
    n: Int = 1,
    role: ConversationRole = if (n % 2 == 1) ConversationRole.USER else ConversationRole.ASSISTANT,
    text: String = "message $n",
    at: Instant? = T0.plusSeconds(n.toLong()),
    truncated: Boolean = false,
): ConversationMessage = ConversationMessage(role, text, at, truncated)
