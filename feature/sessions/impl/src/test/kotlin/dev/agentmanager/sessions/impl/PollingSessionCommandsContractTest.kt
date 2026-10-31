package dev.agentmanager.sessions.impl

import dev.agentmanager.sessions.api.SessionCommandsContract
import dev.agentmanager.sessions.api.SessionsHarness
import dev.agentmanager.sessions.api.SessionsWorld
import kotlinx.coroutines.test.TestScope

/** The commands of `sessionsModule` pass [SessionCommandsContract] over the running monitor (ENG-T2). */
class PollingSessionCommandsContractTest : SessionCommandsContract() {
    override fun TestScope.create(world: SessionsWorld): SessionsHarness = PollingHarness(this, world)
}
