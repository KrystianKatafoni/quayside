package dev.quayside.sessions.api

import kotlinx.coroutines.test.TestScope

class FakePlaceContractTest : PlaceContract() {
    override fun TestScope.create(world: SessionsWorld): PlaceHarness =
        FakePastSessionsHarness(FakeSessions(world), this)
}
