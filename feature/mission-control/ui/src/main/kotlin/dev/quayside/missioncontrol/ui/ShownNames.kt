package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId

/**
 * Display names for the crumbs of the window: [project] is the shown project (null in a global view or without a
 * selection), [sessions] maps the id of every listed session to its name.
 */
public data class ShownNames(
    val project: String?,
    val sessions: Map<SessionId, String>,
)
