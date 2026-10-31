@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.api

import dev.quayside.core.domain.SessionId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/** [SessionsHarness] over a [FakeSessions]: changes are visible at once, [settle] only runs scheduled tasks. */
internal class FakeSessionsHarness(
    private val fake: FakeSessions,
    private val scope: TestScope,
) : SessionsHarness {
    override val monitor: SessionMonitor get() = fake
    override val commands: SessionCommands get() = fake

    override suspend fun change(
        id: SessionId,
        state: SessionState,
    ) {
        fake.setState(id, state)
    }

    override suspend fun appear(session: WorldSession) {
        fake.appear(session)
    }

    override fun runtimeAvailable(available: Boolean) {
        fake.runtimeAvailable(available)
    }

    override fun failStop(id: SessionId) {
        fake.failStop(id)
    }

    override suspend fun settle() {
        scope.runCurrent()
    }
}
