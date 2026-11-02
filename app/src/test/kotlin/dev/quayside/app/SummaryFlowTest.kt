package dev.quayside.app

import dev.quayside.agentruntime.api.A_PROJECT_DIR
import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.FakeAgentEventSource
import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.agentruntime.api.aConversationId
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.agentruntime.api.anEventMeta
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.impl.sessionsModule
import dev.quayside.summaries.impl.summariesModule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** FR-S7: the summary of a session after its agent stops, with the real summarizer behind the real sessions module. */
@OptIn(ExperimentalCoroutinesApi::class)
class SummaryFlowTest {
    private val clock = TestClock()
    private val session = aRuntimeSession()
    private val runtime = FakeAgentRuntime(RuntimeWorld(sessions = listOf(session)))
    private val hooks = FakeAgentEventSource()

    private fun TestScope.start() =
        sessionsModule(
            runtime = runtime,
            hooks = hooks,
            projects = FakeProjectsApi(listOf(Project(ProjectId("p"), A_PROJECT_DIR, "p"))),
            scope = backgroundScope,
            clock = clock,
            logger = NoOpLogger,
            summaries = summariesModule(),
        ).monitor.also { runCurrent() }

    private fun stop(message: String) =
        AgentEvent.TurnEnded(anEventMeta(conversationId = aConversationId(), at = clock.now()), message)

    @Test
    fun `FR-S7 a Stop hook gives the session a short summary`() =
        runTest {
            val monitor = start()

            hooks.emit(stop("## Result\n\nFixed the login form. Added a test. Updated the docs."))
            runCurrent()

            val shown =
                monitor.snapshot.value.sessions
                    .single()
            shown.state shouldBe SessionState.Done
            shown.summary shouldBe "Fixed the login form. Added a test."
        }

    @Test
    fun `FR-S7 summary survives the next poll`() =
        runTest {
            val monitor = start()
            hooks.emit(stop("Fixed the login form."))
            runCurrent()

            clock.advanceBy(10.seconds)
            runtime.setPhase(session.id, RuntimePhase.DONE)
            advanceTimeBy(2.seconds)
            runCurrent()

            val shown =
                monitor.snapshot.value.sessions
                    .single()
            shown.state shouldBe SessionState.Done
            shown.summary shouldBe "Fixed the login form."
        }
}
