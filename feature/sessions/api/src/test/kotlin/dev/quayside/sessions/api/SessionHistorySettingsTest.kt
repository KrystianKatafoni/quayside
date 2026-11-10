package dev.quayside.sessions.api

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The values behind the choices of the disk-session settings (FR-P9, ADR 0032). */
class SessionHistorySettingsTest {
    @Test
    fun `age choices are 7, 30 and 90 days or no limit`() {
        HistoryAge.entries.map { it.days } shouldBe listOf(7, 30, 90, null)
    }

    @Test
    fun `count choices are 50, 200 and 1000`() {
        HistoryLimit.entries.map { it.count } shouldBe listOf(50, 200, 1000)
    }

    @Test
    fun `defaults are 30 days, 200 per project and nothing hidden`() {
        SessionHistorySettings() shouldBe
            SessionHistorySettings(HistoryAge.DAYS_30, HistoryLimit.SESSIONS_200, emptySet())
    }

    @Test
    fun `a session is from the supervisor unless stated`() {
        aSession().origin shouldBe SessionOrigin.SUPERVISOR
        pastSession().origin shouldBe SessionOrigin.DISK
    }

    @Test
    fun `the initial snapshot has no history`() {
        SessionsSnapshot.Initial.history shouldBe emptyMap()
    }
}
