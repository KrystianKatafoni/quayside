package dev.quayside.sessions.impl

import dev.quayside.sessions.api.ResumeContract
import dev.quayside.sessions.api.ResumeHarness
import dev.quayside.sessions.api.SessionsWorld
import kotlinx.coroutines.test.TestScope

/** `sessionsModule` with a history source passes [ResumeContract], like `FakeSessions` (ENG-T2). */
class PollingResumeContractTest : ResumeContract() {
    override fun TestScope.create(world: SessionsWorld): ResumeHarness = PollingPastHarness(this, world)
}
