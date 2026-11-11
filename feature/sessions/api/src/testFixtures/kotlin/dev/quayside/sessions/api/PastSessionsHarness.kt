package dev.quayside.sessions.api

import dev.quayside.core.domain.SessionId

/**
 * A [SessionsHarness] over a system that also lists session files of the disk (FR-P9). [settle] lets it catch up
 * with the files, the settings and the supervisor: the impl advances virtual time by more than one scan interval.
 */
public interface PastSessionsHarness : SessionsHarness {
    /** The settings of the disk sessions under test; changes show in [monitor] after [settle]. */
    public val settings: SessionHistorySettingsRepo

    /** A session file appears on the disk (written by an interactive `claude`); seen after [settle]. */
    public suspend fun givenPastSession(file: WorldPastSession)

    /** The next save of [settings] fails with [HistorySettingsError.WRITE_FAILED]; later ones work again. */
    public fun failNextSettingsSave()

    /**
     * The session file of [id] holds exactly [messages] (oldest first) as user and assistant messages; seen by
     * [SessionMonitor.conversation] at once. The file itself comes from [givenPastSession].
     */
    public suspend fun givenConversation(
        id: SessionId,
        messages: List<SessionMessage>,
    )

    /** The next [SessionMonitor.conversation] fails because the file cannot be read; later reads work again. */
    public fun failNextConversationRead()
}
