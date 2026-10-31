@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.agentmanager.missioncontrol.ui

import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.core.testing.T0
import dev.agentmanager.core.testing.TestClock
import dev.agentmanager.projects.api.A_PROJECT_DIR
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.aProject
import dev.agentmanager.sessions.api.FakeSessions
import dev.agentmanager.sessions.api.SessionCommands
import dev.agentmanager.sessions.api.SessionMonitor
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.api.SessionsApi
import dev.agentmanager.sessions.api.SessionsWorld
import dev.agentmanager.sessions.api.WorldProject
import dev.agentmanager.sessions.api.WorldSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Shared world and helpers of the preview controller tests (FR-K2, AM-411). Sessions start at T0 + their index, so
 * rows are newest first. Time moves only by `advanceTimeBy`; the tick is one hour. Test classes use it by
 * delegation: `class XTest : PreviewTestKit by DefaultPreviewTestKit()`.
 */
internal interface PreviewTestKit {
    val log: RecordingLogger
    val a: SessionId
    val b: SessionId

    fun world(vararg sessions: WorldSession): FakeSessions

    fun session(
        id: String,
        index: Long,
        state: SessionState = SessionState.Working,
    ): WorldSession

    fun TestScope.start(
        fake: FakeSessions,
        monitor: SessionMonitor = fake,
        commands: SessionCommands = fake,
    ): MissionControlController

    fun TestScope.select(
        controller: MissionControlController,
        id: SessionId,
    )

    fun TestScope.send(
        controller: MissionControlController,
        intent: MissionControlIntent,
    )

    fun TestScope.pass(duration: Duration)

    fun MissionControlController.preview(): PreviewUi?

    fun text(id: String): PreviewUi
}

internal class DefaultPreviewTestKit : PreviewTestKit {
    private val project = aProject(displayName = "Agent Manager")
    override val log = RecordingLogger()
    override val a = SessionId("a")
    override val b = SessionId("b")

    override fun world(vararg sessions: WorldSession): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(project.id, project.dir)),
                sessions = sessions.toList(),
                outputs = sessions.associate { it.id to "out-${it.id.value}" },
            ),
            TestClock(),
        )

    override fun session(
        id: String,
        index: Long,
        state: SessionState,
    ): WorldSession =
        WorldSession(
            id = SessionId(id),
            cwd = A_PROJECT_DIR,
            state = state,
            name = "name-$id",
            startedAt = T0.plusSeconds(index),
        )

    override fun TestScope.start(
        fake: FakeSessions,
        monitor: SessionMonitor,
        commands: SessionCommands,
    ): MissionControlController =
        MissionControlController(
            SessionsApi(monitor = monitor, commands = commands),
            FakeProjectsApi(initial = listOf(project)),
            MutableStateFlow(project.id),
            TestClock(),
            backgroundScope,
            log,
            1.hours,
        ).also { runCurrent() }

    override fun TestScope.select(
        controller: MissionControlController,
        id: SessionId,
    ) {
        controller.onEvent(MissionControlEvent.SelectSession(id))
        runCurrent()
    }

    override fun TestScope.send(
        controller: MissionControlController,
        intent: MissionControlIntent,
    ) {
        controller.onIntent(intent)
        runCurrent()
    }

    override fun TestScope.pass(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    override fun MissionControlController.preview(): PreviewUi? = state.value.preview

    override fun text(id: String): PreviewUi = PreviewUi(SessionId(id), "name-$id", PreviewContent.Text("out-$id"))
}
