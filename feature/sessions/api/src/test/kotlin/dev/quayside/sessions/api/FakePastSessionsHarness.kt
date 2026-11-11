@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.api

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/** [ResumeHarness] over a [FakeSessions]: files and settings show at once, [settle] runs scheduled tasks. */
internal class FakePastSessionsHarness(
    private val fake: FakeSessions,
    scope: TestScope,
) : PlaceHarness,
    SessionsHarness by FakeSessionsHarness(fake, scope) {
    override val settings: SessionHistorySettingsRepo get() = fake.historySettings

    override suspend fun givenPastSession(file: WorldPastSession) {
        fake.addPastSession(file)
    }

    override fun removeSessionFile(id: SessionId) {
        fake.removePastSession(id)
    }

    override fun openElsewhere(id: SessionId) {
        fake.opensElsewhere(id)
    }

    override suspend fun setSubfolders(
        project: ProjectId,
        names: List<String>,
    ) {
        fake.setSubfolders(project, names)
    }

    override fun failNextSettingsSave() {
        fake.historySettings.failNext(HistorySettingsError.WRITE_FAILED)
    }
}
