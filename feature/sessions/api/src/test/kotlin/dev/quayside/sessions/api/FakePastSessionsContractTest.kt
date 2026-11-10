package dev.quayside.sessions.api

import kotlinx.coroutines.test.TestScope

class FakePastSessionsContractTest : PastSessionsContract() {
    override fun TestScope.create(world: SessionsWorld): PastSessionsHarness =
        FakePastSessionsHarness(FakeSessions(world), this)
}
