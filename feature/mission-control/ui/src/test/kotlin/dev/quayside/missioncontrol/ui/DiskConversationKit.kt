package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionMessage
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldPastSession
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import dev.quayside.sessions.api.aSessionMessage

/** Disk sessions with a history in [MC_PROJECT], for the tests of AM-315 (FR-P9). */
internal object DiskConversationKit {
    val first = SessionId("00000000-0000-4000-8000-000000000001")
    val second = SessionId("00000000-0000-4000-8000-000000000002")
    val supervisor = SessionId("7c5dcf5d")

    /** The messages "message 1" to "message [count]", user and assistant in turn. */
    fun messages(count: Int): List<SessionMessage> = (1..count).map { aSessionMessage(it) }

    /**
     * [FakeSessions] with the project, one working supervisor session and the disk sessions [first] and [second]
     * (named "Old chat" and "Older chat"), whose files hold [firstMessages] and [secondMessages].
     */
    fun world(
        firstMessages: List<SessionMessage> = messages(3),
        secondMessages: List<SessionMessage> = messages(2),
        summary: Pair<String?, String?> = "Old chat" to null,
    ): FakeSessions {
        val clock = TestClock()
        val fake =
            FakeSessions(
                SessionsWorld(
                    projects = listOf(WorldProject(MC_PROJECT.id, MC_PROJECT.dir)),
                    sessions = listOf(WorldSession(supervisor, A_PROJECT_DIR, name = "live-work")),
                    pastSessions =
                        listOf(
                            WorldPastSession(first, A_PROJECT_DIR, clock.now().minusSeconds(60), summary.first)
                                .copy(lastPrompt = summary.second),
                            WorldPastSession(second, A_PROJECT_DIR, clock.now().minusSeconds(120), "Older chat"),
                        ),
                ),
                clock,
            )
        fake.conversationFiles.set(first, firstMessages)
        fake.conversationFiles.set(second, secondMessages)
        return fake
    }
}
