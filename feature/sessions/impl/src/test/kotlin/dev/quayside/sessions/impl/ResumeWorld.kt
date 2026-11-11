@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.FakeSessionHistory
import dev.quayside.agentruntime.api.PastConversation
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.FakeSessionHistorySettings
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.impl.MonitorFixtures.P1
import dev.quayside.sessions.impl.MonitorFixtures.P1_DIR
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import java.nio.file.Path

/**
 * The resume under test (FR-P9, AM-313): [RuntimeSessionCommands] over one [SessionsState] with a [HistoryScan] over
 * [history], project P1 at [P1_DIR], no polling loop. [missingDirs] are the directories the disk check reports as
 * not being a directory. The runtime is a [LaggingRuntime], so a test can keep the supervisor from listing the
 * resumed job yet.
 */
internal class ResumeWorld(
    val runtime: LaggingRuntime,
    val history: FakeSessionHistory,
    val logger: RecordingLogger,
    val state: SessionsState,
    val commands: RuntimeSessionCommands,
    val missingDirs: MutableSet<Path>,
) {
    val fake: FakeAgentRuntime get() = runtime.fake

    val snapshot: SessionsSnapshot get() = state.snapshot.value

    /** The runtime calls that resumed a conversation (`resume <uuid>`), in order. */
    fun resumeCalls(): List<String> = fake.calls.filter { it.startsWith("resume") }

    fun session(id: String): Session = snapshot.sessions.single { it.id == SessionId(id) }

    fun diskIds(): List<String> = snapshot.sessions.filter { it.origin == SessionOrigin.DISK }.map { it.id.value }

    fun supervisorIds(): List<String> =
        snapshot.sessions.filter { it.origin == SessionOrigin.SUPERVISOR }.map { it.id.value }
}

/** [ResumeWorld] with the session files [conversations] and the supervisor sessions [sessions]; scanned and listed. */
internal suspend fun TestScope.resumeWorld(
    conversations: List<PastConversation> = emptyList(),
    sessions: List<RuntimeSession> = emptyList(),
): ResumeWorld {
    val clock = virtualClock()
    val runtime = LaggingRuntime(FakeAgentRuntime(RuntimeWorld(sessions = sessions), clock))
    val history = FakeSessionHistory(conversations)
    val projects = FakeProjectsApi(listOf(Project(P1, P1_DIR, "p1")))
    val logger = RecordingLogger()
    val scan = HistoryScan(history, FakeSessionHistorySettings(), projects, clock, logger)
    val state = SessionsState(runtime, projects, clock, COMMAND_OVERLAY_TTL, disk = scan)
    val missing = mutableSetOf<Path>()
    val commands = RuntimeSessionCommands(runtime, StartDirs(projects) { it !in missing }, state, clock, logger)
    scan.start(backgroundScope, state::republishDisk)
    runCurrent()
    state.refresh()
    return ResumeWorld(runtime, history, logger, state, commands, missing)
}

/** The conversation id of the file [n] of [conversation]. */
internal fun conversationOf(n: Int): ConversationId = ConversationId(uuidOf(n))
