package dev.quayside.sessions.impl

import dev.quayside.sessions.api.PastSessionsContract
import dev.quayside.sessions.api.PastSessionsHarness
import dev.quayside.sessions.api.SessionsWorld
import kotlinx.coroutines.test.TestScope

/** `sessionsModule` with a history source passes [PastSessionsContract], like `FakeSessions` (ENG-T2). */
class PollingPastSessionsContractTest : PastSessionsContract() {
    override fun TestScope.create(world: SessionsWorld): PastSessionsHarness = PollingPastHarness(this, world)
}
