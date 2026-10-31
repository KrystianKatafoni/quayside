package dev.agentmanager.app

import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.testing.T0
import dev.agentmanager.projects.api.A_PROJECT_DIR
import dev.agentmanager.sessions.api.RuntimeProblem
import dev.agentmanager.sessions.api.Session
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.api.SessionsSnapshot
import dev.agentmanager.sessions.api.SnapshotStatus
import dev.agentmanager.terminal.ui.TabKey
import dev.agentmanager.terminal.ui.TabStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Keys and dots of the attach tabs, mapped from the sessions snapshot (plan AM-510, table 3). */
class AttachTabsTest {
    private fun session(
        id: String,
        state: SessionState,
    ): Session = Session(SessionId(id), ProjectId("project0001"), id, A_PROJECT_DIR, state, T0, T0, summary = null)

    private fun fresh(vararg sessions: Session) = SessionsSnapshot(sessions.toList(), SnapshotStatus.Fresh(T0))

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `session state maps to a tab status`(
        @Suppress("UNUSED_PARAMETER") name: String,
        state: SessionState,
        expected: TabStatus,
    ) {
        tabStatusOf(state) shouldBe expected
    }

    @Test
    fun `attach key is attach and the session id`() {
        attachTabKey(SessionId("s1")) shouldBe TabKey("attach:s1")
    }

    @Test
    fun `attach keys differ per session`() {
        attachTabKey(SessionId("s1")) shouldNotBe attachTabKey(SessionId("s2"))
    }

    @Test
    fun `statuses key every session by its attach key`() {
        val snapshot = fresh(session("s1", SessionState.Working), session("s2", SessionState.Done))

        attachTabStatuses(snapshot) shouldBe
            mapOf(
                TabKey("attach:s1") to TabStatus.WORKING,
                TabKey("attach:s2") to TabStatus.DONE,
            )
    }

    @Test
    fun `loading snapshot gives no statuses`() {
        attachTabStatuses(SessionsSnapshot.Initial) shouldBe emptyMap()
    }

    @Test
    fun `stale snapshot keeps the statuses of its sessions`() {
        val stale = SnapshotStatus.Stale(T0, RuntimeProblem.NOT_INSTALLED)
        val snapshot = SessionsSnapshot(listOf(session("s1", SessionState.Failed)), stale)

        attachTabStatuses(snapshot) shouldBe mapOf(TabKey("attach:s1") to TabStatus.FAILED)
    }

    companion object {
        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                arguments("AwaitingInput with a question", SessionState.AwaitingInput("Continue?"), TabStatus.WAITING),
                arguments("AwaitingInput without a question", SessionState.AwaitingInput(null), TabStatus.WAITING),
                arguments("AwaitingPermission with a tool", SessionState.AwaitingPermission("Bash"), TabStatus.WAITING),
                arguments(
                    "AwaitingPermission without a tool",
                    SessionState.AwaitingPermission(null),
                    TabStatus.WAITING,
                ),
                arguments("Working", SessionState.Working, TabStatus.WORKING),
                arguments("Done", SessionState.Done, TabStatus.DONE),
                arguments("Failed", SessionState.Failed, TabStatus.FAILED),
                arguments("Stopped", SessionState.Stopped, TabStatus.STOPPED),
            )
    }
}
