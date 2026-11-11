package dev.quayside.sessions.impl

import dev.quayside.sessions.api.ConversationContract
import dev.quayside.sessions.api.ResumeHarness
import dev.quayside.sessions.api.SessionsWorld
import kotlinx.coroutines.test.TestScope

/** `sessionsModule` over a `FakeSessionHistory` passes [ConversationContract], like `FakeSessions` (ENG-T2). */
class PollingConversationContractTest : ConversationContract() {
    override fun TestScope.create(world: SessionsWorld): ResumeHarness = PollingPastHarness(this, world)
}
