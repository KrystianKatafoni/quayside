package dev.quayside.notifications.impl

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.notifications.api.NotificationKind

/** One message the voice is asked to speak; [session] and [project] are null for a terminal bell. */
internal data class VoiceMessage(
    val session: SessionId?,
    val kind: NotificationKind,
    val text: String,
    val project: ProjectId? = null,
)

/** What the caller of [VoiceGrouping] does next. */
internal sealed class VoiceAction {
    /**
     * Say [text] now and start the next 20 seconds of cooldown. [project] is the one project all the messages
     * came from, null for several projects or a session without one; it picks the voice (FR-N7).
     */
    internal data class Speak(
        val text: String,
        val project: ProjectId? = null,
    ) : VoiceAction()

    /** Say nothing. */
    internal data object Stay : VoiceAction()
}

/** The new [state] and what to do. */
internal data class VoiceStep(
    val state: VoiceGrouping,
    val action: VoiceAction,
)

/**
 * Pure reducer of the "at most one message per 20 s" rule (FR-N4). Time lives in the caller: it starts the 20 s
 * after every [VoiceAction.Speak] and calls [cooldownEnded] when they pass.
 */
internal data class VoiceGrouping(
    val cooling: Boolean = false,
    val pending: List<VoiceMessage> = emptyList(),
) {
    /**
     * A message arrives: spoken at once without a cooldown, otherwise kept (a newer one of the same session and
     * kind replaces it).
     */
    fun offer(message: VoiceMessage): VoiceStep =
        if (cooling) {
            val kept = pending.filterNot { it.session == message.session && it.kind == message.kind }
            VoiceStep(copy(pending = kept + message), VoiceAction.Stay)
        } else {
            VoiceStep(VoiceGrouping(cooling = true), VoiceAction.Speak(message.text, message.project))
        }

    /** The cooldown passed: speaks the pending messages as one, or ends the cooling when none wait. */
    fun cooldownEnded(): VoiceStep {
        if (pending.isEmpty()) return VoiceStep(VoiceGrouping(), VoiceAction.Stay)
        val sources = pending.map { it.session }.distinct().size
        val text = if (sources == 1) pending.last().text else groupedText(sources)
        val project = pending.map { it.project }.distinct().singleOrNull()
        return VoiceStep(VoiceGrouping(cooling = true), VoiceAction.Speak(text, project))
    }
}

private const val FEW_MIN = 2
private const val FEW_MAX = 4
private const val TEEN_MIN = 12
private const val TEEN_MAX = 14

/** "2 agenty czekają", "5 agentów czeka" for [count] sessions. */
internal fun groupedText(count: Int): String {
    val few = count % 10 in FEW_MIN..FEW_MAX && count % 100 !in TEEN_MIN..TEEN_MAX
    return if (few) "$count agenty czekają" else "$count agentów czeka"
}
