package dev.agentmanager.missioncontrol.ui

import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.projects.api.aProject
import dev.agentmanager.sessions.api.Session
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.api.aSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** When `⏎` and `r` can attach: the pure rule of the plan, table 1 (AM-505, AM-506). */
internal class SessionAttachTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("states")
    fun `a session of a listed project is the target in every state and knows if it waits`(
        @Suppress("UNUSED_PARAMETER") name: String,
        state: SessionState,
        waiting: Boolean,
    ) {
        val session = aSession(id = "s1", project = project.id, state = state, name = "fix-login")

        SessionAttach.targetOf(session, listOf(project), dialogOpen = false) shouldBe
            AttachTarget(SessionId("s1"), "fix-login", project, waiting)
    }

    @Test
    fun `no session gives no target`() {
        SessionAttach.targetOf(null, listOf(project), dialogOpen = false) shouldBe null
    }

    @Test
    fun `a session without a project gives no target`() {
        SessionAttach.targetOf(aSession(project = null), listOf(project), dialogOpen = false) shouldBe null
    }

    @Test
    fun `a session of a removed project gives no target`() {
        val session = aSession(project = ProjectId("gone"))

        SessionAttach.targetOf(session, listOf(project), dialogOpen = false) shouldBe null
    }

    @Test
    fun `an open dialog gives no target`() {
        val session: Session = aSession(project = project.id)

        SessionAttach.targetOf(session, listOf(project), dialogOpen = true) shouldBe null
    }

    @Test
    fun `the project is taken from the list`() {
        val other = aProject(id = "project0002", displayName = "Other")
        val session = aSession(project = other.id)

        SessionAttach.targetOf(session, listOf(project, other), dialogOpen = false)?.project shouldBe other
    }

    private val project = aProject(id = "project0001", displayName = "Agent Manager")

    companion object {
        @JvmStatic
        fun states(): List<Arguments> =
            listOf(
                arguments("working", SessionState.Working, false),
                arguments("awaiting input", SessionState.AwaitingInput("Continue?"), true),
                arguments("awaiting input without a question", SessionState.AwaitingInput(null), true),
                arguments("awaiting permission", SessionState.AwaitingPermission("Bash"), true),
                arguments("done", SessionState.Done, false),
                arguments("failed", SessionState.Failed, false),
                arguments("stopped", SessionState.Stopped, false),
            )
    }
}
