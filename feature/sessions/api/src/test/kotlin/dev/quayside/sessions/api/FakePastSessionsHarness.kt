@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/** [PastSessionsHarness] over a [FakeSessions]: files and settings show at once, [settle] runs scheduled tasks. */
internal class FakePastSessionsHarness(
    private val fake: FakeSessions,
    scope: TestScope,
) : PastSessionsHarness,
    SessionsHarness by FakeSessionsHarness(fake, scope) {
    override val settings: SessionHistorySettingsRepo get() = fake.historySettings

    override suspend fun givenPastSession(file: WorldPastSession) {
        fake.addPastSession(file)
    }

    override fun failNextSettingsSave() {
        fake.historySettings.failNext(HistorySettingsError.WRITE_FAILED)
    }
}
