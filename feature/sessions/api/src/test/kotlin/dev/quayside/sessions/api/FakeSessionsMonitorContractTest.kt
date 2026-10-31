package dev.quayside.sessions.api

import kotlinx.coroutines.test.TestScope

class FakeSessionsMonitorContractTest : SessionMonitorContract() {
    override fun TestScope.create(world: SessionsWorld): SessionsHarness =
        FakeSessionsHarness(FakeSessions(world), this)
}
