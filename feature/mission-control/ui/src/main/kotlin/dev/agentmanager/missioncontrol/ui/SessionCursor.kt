package dev.agentmanager.missioncontrol.ui

import dev.agentmanager.core.domain.SessionId

/** Moves the selection over the visible rows (AM-409); no wrap, a hidden selection counts as none. */
internal object SessionCursor {
    /** The session after [selected]; null = no change. */
    fun next(
        rows: List<SessionId>,
        selected: SessionId?,
    ): SessionId? = move(rows, selected, step = 1, whenNone = rows.firstOrNull())

    /** The session before [selected]; null = no change. */
    fun previous(
        rows: List<SessionId>,
        selected: SessionId?,
    ): SessionId? = move(rows, selected, step = -1, whenNone = rows.lastOrNull())

    private fun move(
        rows: List<SessionId>,
        selected: SessionId?,
        step: Int,
        whenNone: SessionId?,
    ): SessionId? {
        val index = rows.indexOf(selected)
        return if (index < 0) whenNone else rows[(index + step).coerceIn(rows.indices)]
    }
}
