package dev.quayside.agentruntime.api

import dev.quayside.core.domain.SessionId

/**
 * World state a contract test starts from. [FakeAgentRuntime] takes it directly; the adapter test
 * (AM-203) turns it into the answers of a fake `claude` on `PATH`.
 *
 * @property sessions sessions the supervisor already knows about.
 * @property outputs `claude logs` output per session.
 * @property missingConversations conversations without a session file: `resume` answers Ok and the job fails later.
 * @property openElsewhere conversations open in another Claude Code process: `resume` starts a copy (R4).
 * @property installed false = no `claude` binary at all.
 * @property version raw `claude --version` result when [installed].
 * @property agentViewEnabled whether Agent View (background sessions) is switched on.
 */
public data class RuntimeWorld(
    val sessions: List<RuntimeSession> = emptyList(),
    val outputs: Map<SessionId, String> = emptyMap(),
    val missingConversations: Set<ConversationId> = emptySet(),
    val openElsewhere: Set<ConversationId> = emptySet(),
    val installed: Boolean = true,
    val version: String = RuntimeRequirements.MIN_CLAUDE_VERSION,
    val agentViewEnabled: Boolean = true,
)
