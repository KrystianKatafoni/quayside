package dev.quayside.sessions.api

import dev.quayside.core.domain.SessionId

/**
 * What [SessionMonitorContract] and [SessionCommandsContract] drive. Built from a [SessionsWorld]
 * inside a `TestScope`; long-running loops of an implementation run in its `backgroundScope`.
 *
 * Changes made through the harness are applied to the world outside the system under test (the
 * supervisor); the system sees them at the latest after [settle].
 */
public interface SessionsHarness {
    /** Read side under test. */
    public val monitor: SessionMonitor

    /** Write side under test, sharing state with [monitor]. */
    public val commands: SessionCommands

    /** The agent of the existing session [id] moves to [state] by itself. */
    public suspend fun change(
        id: SessionId,
        state: SessionState,
    )

    /** The supervisor starts reporting [session], as if it had been started outside the app. */
    public suspend fun appear(session: WorldSession)

    /**
     * false: the runtime behaves like a missing Claude Code, every listing and command fails with
     * [RuntimeProblem.NOT_INSTALLED]; true: it works again.
     */
    public fun runtimeAvailable(available: Boolean)

    /** Every later stop of session [id] fails with [RuntimeProblem.COMMAND_FAILED]; the session keeps running. */
    public fun failStop(id: SessionId)

    /**
     * Lets the system under test catch up with the world: the impl advances virtual time by one
     * polling interval (never `advanceUntilIdle`, the polling loop never ends), the fake only runs
     * the tasks that are already scheduled.
     */
    public suspend fun settle()
}
