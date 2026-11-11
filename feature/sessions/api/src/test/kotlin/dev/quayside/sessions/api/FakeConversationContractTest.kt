package dev.quayside.sessions.api

import kotlinx.coroutines.test.TestScope

class FakeConversationContractTest : ConversationContract() {
    override fun TestScope.create(world: SessionsWorld): ResumeHarness =
        FakePastSessionsHarness(FakeSessions(world), this)
}
