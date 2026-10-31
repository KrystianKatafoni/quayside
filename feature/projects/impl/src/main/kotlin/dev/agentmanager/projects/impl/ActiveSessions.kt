package dev.agentmanager.projects.impl

import dev.agentmanager.agentruntime.api.RuntimeError
import dev.agentmanager.agentruntime.api.RuntimePhase
import dev.agentmanager.agentruntime.api.RuntimeSession
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.fold

/** Pure count of active sessions for the report. */
internal object ActiveSessions {
    /** Phases `claude agents` shows without `--all`; the others are ended sessions (AM-303, decision 3). */
    private val ACTIVE = setOf(RuntimePhase.WORKING, RuntimePhase.BLOCKED)

    /** Sessions in [ACTIVE] phases, or null when the runtime could not list them. */
    fun count(listed: Outcome<List<RuntimeSession>, RuntimeError>): Int? =
        listed.fold(onOk = { sessions -> sessions.count { it.phase in ACTIVE } }, onErr = { null })
}
