package dev.agentmanager.projects.impl

import dev.agentmanager.agentruntime.api.HookKind

/**
 * Settings JSON in the shape of docs/03 for tests (ADR 0010). Event names are spelled out here, not taken
 * from production code, so a wrong mapping in the code fails the tests.
 */
internal object HookSettings {
    val EVENT_NAMES: Map<HookKind, String> =
        mapOf(
            HookKind.NOTIFICATION to "Notification",
            HookKind.STOP to "Stop",
            HookKind.STOP_FAILURE to "StopFailure",
            HookKind.SESSION_START to "SessionStart",
            HookKind.SESSION_END to "SessionEnd",
        )

    /** The handler Agent Manager writes (docs/03). */
    fun ourHandler(
        url: String = "http://127.0.0.1:47321/hooks",
        tokenHeader: String = "X-AM-Token",
        type: String = "http",
    ): String =
        """{ "type": "$type", "url": "$url", "headers": { "$tokenHeader": "${'$'}AM_HOOK_TOKEN" }, """ +
            """"allowedEnvVars": ["AM_HOOK_TOKEN"] }"""

    /** A handler of another tool: a shell command. */
    const val FOREIGN_HANDLER: String = """{ "type": "command", "command": "echo hi" }"""

    /** `{"hooks": {<event>: [{"hooks": [handler]}]}}` for each of [events]. */
    fun settingsWithEvents(
        events: List<String>,
        handler: String = ourHandler(),
    ): String =
        events.joinToString(
            separator = ",\n    ",
            prefix = "{\n  \"hooks\": {\n    ",
            postfix = "\n  }\n}\n",
        ) { event -> "\"$event\": [{ \"hooks\": [$handler] }]" }

    /** Our hooks for [kinds]. */
    fun settings(kinds: Collection<HookKind>): String = settingsWithEvents(kinds.map { EVENT_NAMES.getValue(it) })
}
