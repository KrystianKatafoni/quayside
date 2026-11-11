package dev.quayside.sessions.impl

import dev.quayside.sessions.api.PlaceContract
import dev.quayside.sessions.api.PlaceHarness
import dev.quayside.sessions.api.SessionsWorld
import kotlinx.coroutines.test.TestScope

/** `sessionsModule` with a history source passes [PlaceContract], like `FakeSessions` (ENG-T2). */
class PollingPlaceContractTest : PlaceContract() {
    override fun TestScope.create(world: SessionsWorld): PlaceHarness = PollingPastHarness(this, world)
}
