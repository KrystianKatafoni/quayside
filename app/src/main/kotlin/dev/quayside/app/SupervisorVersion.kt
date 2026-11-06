package dev.quayside.app

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.RuntimeHealth

private val WHITESPACE = Regex("\\s+")

/** The version token of Claude Code ("2.1.139" of "2.1.139 (Claude Code)"), or null without Claude Code. */
internal fun supervisorOf(health: RuntimeHealth): String? =
    health.version
        ?.trim()
        ?.split(WHITESPACE, limit = 2)
        ?.first()
        ?.takeIf { it.isNotEmpty() }

/** Asks [runtime] for its health once and returns [supervisorOf] of the answer. */
internal suspend fun readSupervisor(runtime: AgentRuntime): String? = supervisorOf(runtime.health())
