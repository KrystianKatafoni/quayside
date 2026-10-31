package dev.quayside.sessions.impl

import dev.quayside.sessions.api.SessionMonitorContract
import dev.quayside.sessions.api.SessionsHarness
import dev.quayside.sessions.api.SessionsWorld
import kotlinx.coroutines.test.TestScope

/** The polling monitor of `sessionsModule` passes [SessionMonitorContract] (ENG-T2). */
class PollingSessionMonitorContractTest : SessionMonitorContract() {
    override fun TestScope.create(world: SessionsWorld): SessionsHarness = PollingHarness(this, world)
}
