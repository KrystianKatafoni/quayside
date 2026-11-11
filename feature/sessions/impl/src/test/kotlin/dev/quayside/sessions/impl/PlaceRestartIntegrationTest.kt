@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.FakeAgentEventSource
import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.FakeSessionHistory
import dev.quayside.agentruntime.api.PastConversation
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.PlaceAssignment
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.impl.MonitorFixtures.P1
import dev.quayside.sessions.impl.MonitorFixtures.P1_DIR
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

/**
 * A manual place survives a restart of the module (FR-P9, AM-314, ADR 0033): `sessionsModule` over the real
 * `JsonStorage` in a temp directory, built twice over the same directory. The behaviour lives behind the serializer,
 * so a test over an in-memory store would not prove it (lesson of AM-312). Nothing but local files is used.
 */
class PlaceRestartIntegrationTest {
    @TempDir
    lateinit var dir: Path

    // Real threads: JsonStorage blocks on file IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    private val live = SessionId("0b6f0000")
    private val diskId = SessionId("c0de0002-0000-4000-8000-000000000002")
    private val marketing: Path = P1_DIR.resolve("marketing")

    private suspend fun TestScope.start(): SessionsApi {
        val past =
            PastConversation(ConversationId(diskId.value), marketing, T0, T0, T0.minusSeconds(60), false, "old chat")
        return sessionsModule(
            FakeAgentRuntime(
                RuntimeWorld(
                    sessions =
                        listOf(
                            aRuntimeSession(
                                live,
                                ConversationId("0b6f0000-0000-4000-8000-000000000000"),
                                cwd = marketing,
                            ),
                        ),
                ),
            ),
            FakeAgentEventSource(),
            FakeProjectsApi(listOf(Project(P1, P1_DIR, "p1", subfolderNames("soft", "docs")))),
            backgroundScope,
            virtualClock(),
            NoOpLogger,
            pollInterval = 2.seconds,
            maxRetryInterval = 15.seconds,
            history = FakeSessionHistory(listOf(past)),
            historySettings = sessionHistorySettingsModule(JsonStorage(dir, io), NoOpLogger),
        ).also { advance(SETTLE) }
    }

    private fun SessionsApi.dirOf(id: SessionId): Path? =
        monitor.snapshot.value.sessions
            .single { it.id == id }
            .assignedDir

    @Test
    fun `place survives a restart of the module`() =
        runTest {
            val first = start()
            first.commands.assignPlace(live, PlaceAssignment.Subfolder("soft")) shouldBe Outcome.Ok(Unit)
            first.commands.assignPlace(diskId, PlaceAssignment.ProjectRoot) shouldBe Outcome.Ok(Unit)

            val second = start()

            second.dirOf(live) shouldBe P1_DIR.resolve("soft")
            second.dirOf(diskId) shouldBe P1_DIR
        }

    @Test
    fun `automatic is saved too and survives a restart`() =
        runTest {
            val first = start()
            first.commands.assignPlace(live, PlaceAssignment.Subfolder("soft"))
            first.commands.assignPlace(live, null)

            start().dirOf(live) shouldBe null
        }

    private companion object {
        val SETTLE = 3.seconds
    }
}
