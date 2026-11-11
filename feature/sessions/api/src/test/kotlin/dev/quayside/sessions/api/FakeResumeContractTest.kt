package dev.quayside.sessions.api

import kotlinx.coroutines.test.TestScope

class FakeResumeContractTest : ResumeContract() {
    override fun TestScope.create(world: SessionsWorld): ResumeHarness =
        FakePastSessionsHarness(FakeSessions(world), this)
}
