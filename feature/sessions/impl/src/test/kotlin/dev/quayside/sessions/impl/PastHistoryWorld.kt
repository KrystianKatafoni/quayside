@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.FakeAgentEventSource
import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.FakeSessionHistory
import dev.quayside.agentruntime.api.PastConversation
import dev.quayside.agentruntime.api.aConversationId
import dev.quayside.agentruntime.api.aPastConversation
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.FakeSessionHistorySettings
import dev.quayside.sessions.api.SessionHistorySettingsRepo
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.impl.MonitorFixtures.P1
import dev.quayside.sessions.impl.MonitorFixtures.P1_DIR
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** What a disk-session test drives: the fakes behind `sessionsModule` and the module itself (FR-P9). */
internal class PastHistoryWorld(
    val history: FakeSessionHistory,
    val settings: SessionHistorySettingsRepo,
    val projects: FakeProjectsApi,
    val runtime: FakeAgentRuntime,
    val logger: RecordingLogger,
    val api: SessionsApi,
)

/** The file of conversation [n] that started in [cwd]; modified [n] minutes before [T0]. */
internal fun conversation(
    n: Int,
    cwd: Path = P1_DIR,
    title: String? = "title $n",
): PastConversation =
    aPastConversation(
        conversationId = aConversationId(uuidOf(n)),
        startCwd = cwd,
        startedAt = T0.minusSeconds(n * MINUTE + MINUTE),
        lastActivityAt = T0.minusSeconds(n * MINUTE),
        modifiedAt = T0.minusSeconds(n * MINUTE),
        title = title,
    )

internal fun uuidOf(n: Int): String = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

private const val MINUTE = 60L

/**
 * `sessionsModule` over [conversations] and project P1 at [P1_DIR] (more in [extraProjects]), with the poll interval
 * of 2 s; the first listing and the first scan have run when this returns.
 */
internal fun TestScope.pastWorld(
    conversations: List<PastConversation> = emptyList(),
    extraProjects: List<Project> = emptyList(),
    settings: SessionHistorySettingsRepo = FakeSessionHistorySettings(),
    history: FakeSessionHistory = FakeSessionHistory(conversations),
): PastHistoryWorld {
    val runtime = FakeAgentRuntime()
    val projects = FakeProjectsApi(listOf(Project(P1, P1_DIR, "p1")) + extraProjects)
    val logger = RecordingLogger()
    val api =
        sessionsModule(
            SwitchableRuntime(runtime, now = { testScheduler.currentTime }),
            FakeAgentEventSource(),
            projects,
            backgroundScope,
            virtualClock(),
            logger,
            pollInterval = 2.seconds,
            maxRetryInterval = 15.seconds,
            history = history,
            historySettings = settings,
        )
    runCurrent()
    return PastHistoryWorld(history, settings, projects, runtime, logger, api)
}
