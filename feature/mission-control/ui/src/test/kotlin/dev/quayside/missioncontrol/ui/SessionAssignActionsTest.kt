package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/** Which sessions offer "Przypisz do podfolderu…" (FR-P9, AM-314): sessions of a group, in any state and origin. */
internal class SessionAssignActionsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("inGroup")
    fun `assign is offered for a session of a group in any state and origin`(
        @Suppress("UNUSED_PARAMETER") title: String,
        state: SessionState,
        origin: SessionOrigin,
        expected: Set<SessionAction>,
    ) {
        SessionActions.available(state, busy = false, origin, inGroup = true) shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inGroup")
    fun `assign is not offered without subfolders or project`(
        @Suppress("UNUSED_PARAMETER") title: String,
        state: SessionState,
        origin: SessionOrigin,
        offered: Set<SessionAction>,
    ) {
        val without = SessionActions.available(state, busy = false, origin, inGroup = false)

        without shouldBe offered - SessionAction.ASSIGN_PLACE
        SessionActions.available(state, busy = false, origin) shouldBe without
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inGroup")
    fun `nothing while busy`(
        @Suppress("UNUSED_PARAMETER") title: String,
        state: SessionState,
        origin: SessionOrigin,
        @Suppress("UNUSED_PARAMETER") offered: Set<SessionAction>,
    ) {
        SessionActions.available(state, busy = true, origin, inGroup = true) shouldBe emptySet()
    }

    @Test
    fun `the control of the action is in the panel and the row menu`() {
        val details =
            SessionDetails(
                id = SessionId("one"),
                name = "fix-login",
                glyph = StatusLook.of(SessionState.Done),
                summary = null,
                worktree = Path.of("/Users/test/project"),
                startedAt = T0,
                lastActivityAt = T0,
                actions = setOf(SessionAction.ASSIGN_PLACE),
            )

        details.allControls().map { it.id } shouldBe listOf("assign_place", "preview")
        details.allControls().first().event shouldBe MissionControlEvent.Run(SessionAction.ASSIGN_PLACE)
        details.allControls().first().labelKey shouldBe "action.assign_place"
    }

    companion object {
        private val active =
            listOf(
                "working" to SessionState.Working,
                "awaiting input" to SessionState.AwaitingInput("q"),
                "awaiting permission" to SessionState.AwaitingPermission("Bash"),
            )
        private val inactive =
            listOf("done" to SessionState.Done, "failed" to SessionState.Failed, "stopped" to SessionState.Stopped)
        private val assign = SessionAction.ASSIGN_PLACE

        @JvmStatic
        fun inGroup(): List<Arguments> =
            active.map { (n, s) ->
                arguments("supervisor $n", s, SessionOrigin.SUPERVISOR, setOf(SessionAction.STOP, assign))
            } +
                inactive.map { (n, s) ->
                    arguments(
                        "supervisor $n",
                        s,
                        SessionOrigin.SUPERVISOR,
                        setOf(SessionAction.RESPAWN, SessionAction.REMOVE, assign),
                    )
                } +
                (active + inactive).map { (n, s) ->
                    arguments(
                        "disk $n",
                        s,
                        SessionOrigin.DISK,
                        setOf(SessionAction.RESUME, SessionAction.REMOVE, assign),
                    )
                }
    }
}
