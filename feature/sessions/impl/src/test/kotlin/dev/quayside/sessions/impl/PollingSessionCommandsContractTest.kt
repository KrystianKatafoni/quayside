package dev.quayside.sessions.impl

import dev.quayside.sessions.api.SessionCommandsContract
import dev.quayside.sessions.api.SessionsHarness
import dev.quayside.sessions.api.SessionsWorld
import kotlinx.coroutines.test.TestScope

/** The commands of `sessionsModule` pass [SessionCommandsContract] over the running monitor (ENG-T2). */
class PollingSessionCommandsContractTest : SessionCommandsContract() {
    override fun TestScope.create(world: SessionsWorld): SessionsHarness = PollingHarness(this, world)
}
