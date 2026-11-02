package dev.quayside.app

import dev.quayside.agentruntime.api.A_PROJECT_DIR
import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.FakeAgentEventSource
import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.FakeOneShotPrompt
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.agentruntime.api.aConversationId
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.agentruntime.api.anEventMeta
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.impl.sessionsModule
import dev.quayside.summaries.impl.summariesModule
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

/**
 * FR-S7: the summary of a session after its agent stops, with the real summarizer behind the real sessions module.
 * The model summary (ADR 0019) comes from [FakeOneShotPrompt] and is on only when `summaries.json` says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SummaryFlowTest {
    private val clock = TestClock()
    private val session = aRuntimeSession()
    private val runtime = FakeAgentRuntime(RuntimeWorld(sessions = listOf(session)))
    private val hooks = FakeAgentEventSource()
    private val model = FakeOneShotPrompt { Outcome.Ok("Haiku says the login form works now.") }
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @TempDir
    lateinit var dir: Path

    @AfterEach
    fun closeDispatcher() = io.close()

    private suspend fun TestScope.start(
        useModel: Boolean = false,
        prompt: FakeOneShotPrompt = model,
    ) = sessionsModule(
        runtime = runtime,
        hooks = hooks,
        projects = FakeProjectsApi(listOf(Project(ProjectId("p"), A_PROJECT_DIR, "p"))),
        scope = backgroundScope,
        clock = clock,
        logger = NoOpLogger,
        summaries = summariesModule(storage(useModel), prompt, NoOpLogger),
    ).monitor.also { runCurrent() }

    private fun storage(useModel: Boolean): JsonStorage {
        dir.resolve("summaries.json").writeText("""{"schemaVersion":1,"data":{"useModel":$useModel}}""")
        return JsonStorage(dir, io)
    }

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

    @Test
    fun `FR-S7 with the model on the row shows the Haiku summary`() =
        runTest {
            val monitor = start(useModel = true)

            hooks.emit(stop("Fixed the login form. Added a test. Updated the docs."))
            runCurrent()

            val shown =
                monitor.snapshot.value.sessions
                    .single()
            shown.state shouldBe SessionState.Done
            shown.summary shouldBe "Haiku says the login form works now."
            model.calls.single().model shouldBe "haiku"
        }

    @Test
    fun `FR-S7 with the model off the quick summary stays and the model is never asked`() =
        runTest {
            val monitor = start(useModel = false)

            hooks.emit(stop("Fixed the login form. Added a test. Updated the docs."))
            runCurrent()

            monitor.snapshot.value.sessions
                .single()
                .summary shouldBe "Fixed the login form. Added a test."
            model.calls.shouldBeEmpty()
        }

    @Test
    fun `FR-S7 a failing model keeps the quick summary on the row`() =
        runTest {
            val failing = FakeOneShotPrompt { Outcome.Err(RuntimeError.NotInstalled) }
            val monitor = start(useModel = true, prompt = failing)

            hooks.emit(stop("Fixed the login form. Added a test. Updated the docs."))
            runCurrent()

            monitor.snapshot.value.sessions
                .single()
                .summary shouldBe "Fixed the login form. Added a test."
            failing.calls.size shouldBe 1
        }
}
