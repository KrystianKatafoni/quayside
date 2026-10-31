package dev.quayside.missioncontrol.ui

/**
 * The context menu of a session row (AM-511): the [controls] of the selected session, shown while [open]. The
 * state lives in the page ([setOpen] changes it); the menu draws nothing without [controls], e.g. in the step
 * between a right click on another row and the selection it causes.
 */
internal class RowMenu(
    val controls: List<SessionControl>,
    val open: Boolean,
    val setOpen: (Boolean) -> Unit,
)
