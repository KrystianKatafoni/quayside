package dev.agentmanager.sessions.api

import kotlinx.coroutines.test.TestScope

class FakeSessionsCommandsContractTest : SessionCommandsContract() {
    override fun TestScope.create(world: SessionsWorld): SessionsHarness =
        FakeSessionsHarness(FakeSessions(world), this)
}
