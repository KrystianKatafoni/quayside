package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.WaitingFor
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.Session
import java.time.Instant

/**
 * What a resume puts in the snapshot: the live [session] and whether it is a [copy] of the conversation.
 *
 * @property session the session as the supervisor will list it: waiting for input, in the start directory.
 * @property copy true when the supervisor ran the conversation under another id (it was open elsewhere).
 */
internal data class ResumeEffect(
    val session: RuntimeSession,
    val copy: Boolean,
)

/** Pure rules of the effect of `claude --bg --resume` (FR-P9, docs/compat.md R4, R5). No IO. */
internal object ResumedSessions {
    /**
     * The effect of resuming [disk] (conversation [uuid]) that `claude` answered with [id]. [id] equal to the first 8
     * characters of [uuid] = the same conversation woke ([RuntimeSession.conversationId] = [uuid], `copy` false); any
     * other id is a copy with an unknown UUID (`conversationId` null, `copy` true). Always: phase `BLOCKED`, waiting
     * for `Input`, `cwd` and `name` of [disk], `startedAt` = [now], no pid.
     */
    fun effect(
        disk: Session,
        uuid: ConversationId,
        id: SessionId,
        now: Instant,
    ): ResumeEffect {
        val sameConversation = id.value == uuid.value.take(DiskSessions.SHORT_ID)
        val session =
            RuntimeSession(
                id = id,
                conversationId = uuid.takeIf { sameConversation },
                name = disk.name,
                cwd = disk.cwd,
                phase = RuntimePhase.BLOCKED,
                waitingFor = WaitingFor.Input,
                startedAt = now,
                pid = null,
            )
        return ResumeEffect(session, copy = !sameConversation)
    }
}
