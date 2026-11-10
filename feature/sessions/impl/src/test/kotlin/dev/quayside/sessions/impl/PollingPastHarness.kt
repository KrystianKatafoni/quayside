@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.FakeAgentEventSource
import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.FakeSessionHistory
import dev.quayside.agentruntime.api.PastConversation
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.FakeSessionHistorySettings
import dev.quayside.sessions.api.HistorySettingsError
import dev.quayside.sessions.api.PastSessionsHarness
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionHistorySettingsRepo
import dev.quayside.sessions.api.SessionMonitor
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldPastSession
import dev.quayside.sessions.api.WorldSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration.Companion.seconds

/**
 * [PastSessionsHarness] over `sessionsModule` with a [FakeSessionHistory] and a [FakeSessionHistorySettings]: the
 * files of the world are conversations, [settle] advances virtual time by 61 s (more than one scan interval and many
 * polls). Built on `sessionsModule` itself, so the contract also covers the wiring of the factory.
 */
internal class PollingPastHarness(
    private val scope: TestScope,
    world: SessionsWorld,
) : PastSessionsHarness {
    private val fake =
        FakeAgentRuntime(RuntimeWorld(sessions = world.sessions.map { it.toRuntime() }, outputs = world.outputs))
    private val runtime = SwitchableRuntime(fake, now = { scope.testScheduler.currentTime })
    private val projects = FakeProjectsApi(world.projects.map { Project(it.id, it.dir, it.id.value) })
    private val history = FakeSessionHistory(world.pastSessions.map { it.toConversation() })
    private val fakeSettings = FakeSessionHistorySettings()

    private val api =
        sessionsModule(
            runtime,
            FakeAgentEventSource(),
            projects,
            scope.backgroundScope,
            scope.virtualClock(),
            NoOpLogger,
            pollInterval = 2.seconds,
            maxRetryInterval = 15.seconds,
            history = history,
            historySettings = fakeSettings,
        )

    override val monitor: SessionMonitor = api.monitor

    override val commands: SessionCommands = api.commands

    override val settings: SessionHistorySettingsRepo = fakeSettings

    override suspend fun givenPastSession(file: WorldPastSession) {
        history.put(file.toConversation())
    }

    override fun failNextSettingsSave() {
        fakeSettings.failNext(HistorySettingsError.WRITE_FAILED)
    }

    override suspend fun change(
        id: SessionId,
        state: SessionState,
    ) {
        val (phase, waitingFor) = state.toRuntimePhase()
        fake.setPhase(id, phase, waitingFor)
    }

    override suspend fun appear(session: WorldSession) {
        fake.hasSession(session.toRuntime())
    }

    override fun runtimeAvailable(available: Boolean) {
        runtime.available(available)
    }

    override fun failStop(id: SessionId) {
        runtime.failStop(id)
    }

    override suspend fun settle() {
        scope.advance(SETTLE)
    }

    private companion object {
        val SETTLE = 61.seconds
    }
}

private fun WorldPastSession.toConversation(): PastConversation =
    PastConversation(ConversationId(id.value), cwd, startedAt, lastActivityAt, modifiedAt, cwdChanged = false, title)
