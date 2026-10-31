package dev.quayside.sessions.api

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.T0
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class SessionsSnapshotTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("snapshots")
    fun `countsFor counts active sessions of the project only`(
        snapshot: SessionsSnapshot,
        expected: SessionCounts,
    ) {
        snapshot.countsFor(P1) shouldBe expected
    }

    @Test
    fun `active is working plus waiting`() {
        SessionCounts(working = 1, waiting = 2).active shouldBe 3
    }

    @Test
    fun `Initial has no sessions and is loading`() {
        SessionsSnapshot.Initial shouldBe SessionsSnapshot(emptyList(), SnapshotStatus.Loading)
    }

    private companion object {
        val P1 = ProjectId("p1")
        val P2 = ProjectId("p2")

        fun fresh(vararg sessions: Session) = SessionsSnapshot(sessions.toList(), SnapshotStatus.Fresh(T0))

        val everyStateInP1 =
            fresh(
                aSession(id = "aaaa0001", project = P1, state = SessionState.Working),
                aSession(id = "aaaa0002", project = P1, state = SessionState.AwaitingInput(null)),
                aSession(id = "aaaa0003", project = P1, state = SessionState.AwaitingPermission("Bash")),
                aSession(id = "aaaa0004", project = P1, state = SessionState.Done),
                aSession(id = "aaaa0005", project = P1, state = SessionState.Stopped),
                aSession(id = "aaaa0006", project = P1, state = SessionState.Failed),
            )

        val otherProjectAndNone =
            fresh(
                aSession(id = "aaaa0001", project = P2, state = SessionState.Working),
                aSession(id = "aaaa0002", project = null, state = SessionState.Working),
            )

        val staleInP1 =
            SessionsSnapshot(
                listOf(aSession(id = "aaaa0001", project = P1, state = SessionState.Working)),
                SnapshotStatus.Stale(lastFreshAt = T0, problem = RuntimeProblem.TIMEOUT),
            )

        @JvmStatic
        fun snapshots(): List<Arguments> =
            listOf(
                arguments(named("no sessions", fresh()), SessionCounts(0, 0)),
                arguments(named("Initial", SessionsSnapshot.Initial), SessionCounts(0, 0)),
                arguments(named("p1: every state", everyStateInP1), SessionCounts(working = 1, waiting = 2)),
                arguments(named("other project and no project", otherProjectAndNone), SessionCounts(0, 0)),
                arguments(named("stale snapshot still counts", staleInP1), SessionCounts(working = 1, waiting = 0)),
            )
    }
}
