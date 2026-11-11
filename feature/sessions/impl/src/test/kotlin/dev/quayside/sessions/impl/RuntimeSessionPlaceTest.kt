@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.FakeAgentEventSource
import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.FakeSessionHistory
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.agentruntime.api.WaitingFor
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.FakeSessionHistorySettings
import dev.quayside.sessions.api.HistorySettingsError
import dev.quayside.sessions.api.PlaceAssignment
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.StoredPlace
import dev.quayside.sessions.impl.MonitorFixtures.P1
import dev.quayside.sessions.impl.MonitorFixtures.P1_DIR
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * `assignPlace` of `sessionsModule` (FR-P9, AM-314 plan, "Testy"): never calls Claude Code, needs no fresh listing,
 * writes nothing for "Automatycznie" without an entry, shares one settings repo with the scan, and logs the kind
 * of the place without the subfolder name (ENG-C8). The sessions run in `marketing`, the places are elsewhere.
 */
class RuntimeSessionPlaceTest {
    private val uuid = ConversationId("0b6f0000-0000-4000-8000-000000000000")
    private val id = SessionId("0b6f0000")
    private val marketing: Path = P1_DIR.resolve("marketing")
    private val secret = "secret-folder"

    private inner class Module(
        scope: TestScope,
        history: Boolean = true,
        conversation: ConversationId? = uuid,
    ) {
        val fake =
            FakeAgentRuntime(
                RuntimeWorld(sessions = listOf(aRuntimeSession(id, conversation, cwd = marketing))),
            )
        val runtime = SwitchableRuntime(fake, now = { scope.testScheduler.currentTime })
        val settings = FakeSessionHistorySettings()
        val logger = RecordingLogger()
        val api: SessionsApi =
            sessionsModule(
                runtime,
                FakeAgentEventSource(),
                FakeProjectsApi(listOf(Project(P1, P1_DIR, "p1", subfolderNames("soft", secret)))),
                scope.backgroundScope,
                scope.virtualClock(),
                logger,
                pollInterval = 2.seconds,
                maxRetryInterval = 15.seconds,
                history = if (history) FakeSessionHistory(emptyList()) else null,
                historySettings = settings,
            )
        val scope = scope

        fun session() =
            api.monitor.snapshot.value.sessions
                .single { it.id == id }

        fun settle() = scope.advance(POLL)
    }

    private val soft = PlaceAssignment.Subfolder("soft")

    @Test
    fun `assign never calls the runtime`() =
        runTest {
            val module = Module(this)
            module.settle()
            val before = module.fake.calls

            module.api.commands.assignPlace(id, soft) shouldBe Outcome.Ok(Unit)
            module.api.commands.assignPlace(id, null) shouldBe Outcome.Ok(Unit)

            module.fake.calls shouldBe before
        }

    @Test
    fun `assign works while the listing fails`() =
        runTest {
            val module = Module(this)
            module.settle()
            module.runtime.available(false)
            module.settle()
            module.api.monitor.snapshot.value.status
                .shouldBeInstanceOf<SnapshotStatus.Stale>()

            module.api.commands.assignPlace(id, soft) shouldBe Outcome.Ok(Unit)

            module.session().assignedDir shouldBe P1_DIR.resolve("soft")
        }

    @Test
    fun `automatic without an entry writes nothing`() =
        runTest {
            val module = Module(this)
            module.settle()
            module.settings.failNext(HistorySettingsError.WRITE_FAILED)

            module.api.commands.assignPlace(id, null) shouldBe Outcome.Ok(Unit)

            module.settings.updates shouldBe 0
        }

    @Test
    fun `automatic with an entry removes it from the settings`() =
        runTest {
            val module = Module(this)
            module.settle()
            module.api.commands.assignPlace(id, soft)

            module.api.commands.assignPlace(id, null)

            module.settings.settings.value.places shouldBe emptyMap()
            module.session().assignedDir shouldBe null
        }

    @Test
    fun `assign saves under the conversation uuid`() =
        runTest {
            val module = Module(this)
            module.settle()

            module.api.commands.assignPlace(id, soft)

            module.settings.settings.value.places shouldBe
                mapOf(SessionId(uuid.value) to StoredPlace(P1, PlaceAssignment.Subfolder("soft")))
        }

    @Test
    fun `assign saves under the short id when the conversation is unknown`() =
        runTest {
            val module = Module(this, conversation = null)
            module.settle()

            module.api.commands.assignPlace(id, PlaceAssignment.ProjectRoot)

            module.settings.settings.value.places shouldBe mapOf(id to StoredPlace(P1, PlaceAssignment.ProjectRoot))
            module.session().assignedDir shouldBe P1_DIR
        }

    @Test
    fun `an entry under the short id goes away when the conversation becomes known`() =
        runTest {
            val module = Module(this, conversation = null)
            module.settle()
            module.api.commands.assignPlace(id, soft)
            module.settings.settings.value.places.keys shouldBe setOf(id)
            module.fake.hasSession(aRuntimeSession(id, uuid, cwd = marketing))
            module.settle()
            module.api.commands.assignPlace(id, PlaceAssignment.ProjectRoot)
            module.settings.settings.value.places shouldBe
                mapOf(SessionId(uuid.value) to StoredPlace(P1, PlaceAssignment.ProjectRoot))

            module.api.commands.assignPlace(id, null)

            module.settings.settings.value.places shouldBe emptyMap()
            module.session().assignedDir shouldBe null
        }

    @Test
    fun `events carry the assigned dir`() =
        runTest {
            val module = Module(this)
            module.settle()
            val events = record(module.api.monitor.events)
            module.api.commands.assignPlace(id, soft)

            module.fake.setPhase(id, RuntimePhase.BLOCKED, WaitingFor.Input)
            module.settle()

            val blocked = events.filterIsInstance<SessionEvent.BecameBlocked>().single()
            blocked.session.assignedDir shouldBe P1_DIR.resolve("soft")
            blocked.session.cwd shouldBe marketing
        }

    @Test
    fun `assign emits no event`() =
        runTest {
            val module = Module(this)
            module.settle()
            val events = record(module.api.monitor.events)

            module.api.commands.assignPlace(id, soft)
            module.settle()

            events.shouldBeEmpty()
        }

    @Test
    fun `assign logs the kind without the subfolder name`() =
        runTest {
            val module = Module(this)
            module.settle()

            module.api.commands.assignPlace(id, PlaceAssignment.Subfolder(secret))
            module.settings.failNext(HistorySettingsError.WRITE_FAILED)
            module.api.commands.assignPlace(id, PlaceAssignment.Subfolder(secret))

            val text = module.logger.entries.toString()
            text shouldNotContain secret
            text shouldNotContain marketing.toString()
            module.logger.entries
                .any { entry -> entry.fields.values.contains("subfolder") } shouldBe true
        }

    @Test
    fun `assign shares the settings with the scan without history`() =
        runTest {
            val module = Module(this, history = false)
            module.settle()

            module.api.commands.assignPlace(id, soft) shouldBe Outcome.Ok(Unit)

            module.settings.settings.value.places.size shouldBe 1
            module.session().assignedDir shouldBe P1_DIR.resolve("soft")
            module.settle()
            module.session().assignedDir shouldBe P1_DIR.resolve("soft")
        }

    @Test
    fun `a change of the places in the settings shows without a command`() =
        runTest {
            val module = Module(this)
            module.settle()

            module.settings.update {
                it.copy(
                    places =
                        mapOf(SessionId(uuid.value) to StoredPlace(P1, PlaceAssignment.ProjectRoot)),
                )
            }
            module.settle()

            module.session().assignedDir shouldBe P1_DIR
        }

    private companion object {
        val POLL = 3.seconds
    }
}
