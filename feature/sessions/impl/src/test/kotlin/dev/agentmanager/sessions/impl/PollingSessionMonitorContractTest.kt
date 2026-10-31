package dev.agentmanager.sessions.impl

import dev.agentmanager.sessions.api.SessionMonitorContract
import dev.agentmanager.sessions.api.SessionsHarness
import dev.agentmanager.sessions.api.SessionsWorld
import kotlinx.coroutines.test.TestScope

/** The polling monitor of `sessionsModule` passes [SessionMonitorContract] (ENG-T2). */
class PollingSessionMonitorContractTest : SessionMonitorContract() {
    override fun TestScope.create(world: SessionsWorld): SessionsHarness = PollingHarness(this, world)
}
