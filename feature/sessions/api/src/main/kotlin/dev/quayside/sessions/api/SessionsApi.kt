package dev.quayside.sessions.api

/**
 * Both sides of the sessions feature as returned by `sessionsModule` (docs/11 §9). They share one
 * state: [commands] publish their results into [monitor] before they return.
 * Both sides may be used from any dispatcher (ENG-I1).
 *
 * @property monitor read side: snapshot and events.
 * @property commands write side: start, stop, respawn, remove and stopAllIn.
 */
public data class SessionsApi(
    val monitor: SessionMonitor,
    val commands: SessionCommands,
)
