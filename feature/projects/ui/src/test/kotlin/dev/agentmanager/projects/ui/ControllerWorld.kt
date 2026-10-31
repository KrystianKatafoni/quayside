@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.agentmanager.projects.ui

import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.projects.api.FakeProjectInspector
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.InspectionWorld
import dev.agentmanager.projects.api.PathKind
import dev.agentmanager.sessions.api.FakeSessions
import dev.agentmanager.sessions.api.SessionCounts
import dev.agentmanager.sessions.api.SessionsApi
import dev.agentmanager.sessions.api.SessionsWorld
import dev.agentmanager.sessions.api.WorldProject
import dev.agentmanager.sessions.api.WorldSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import java.nio.file.Path

/**
 * Fakes around a [ProjectsController]: the project list, the sessions, the folder chooser and an inspector
 * that answers from [inspection] and sees the same list as [projects], so `alreadyAdded` is real.
 *
 * [sessions] knows the projects [projects] starts with plus [extraProjects] (for projects the test adds later)
 * and runs [running]; which project a session belongs to follows from its directory.
 */
internal class ControllerWorld(
    val projects: FakeProjectsApi = FakeProjectsApi(),
    inspection: InspectionWorld = InspectionWorld(),
    pathKind: (Path) -> PathKind = { PathKind.DIRECTORY },
    gatedInspection: Boolean = false,
    running: List<WorldSession> = emptyList(),
    extraProjects: List<WorldProject> = emptyList(),
) {
    val sessions =
        FakeSessions(
            SessionsWorld(
                projects = projects.projects.value.map { WorldProject(it.id, it.dir) } + extraProjects,
                sessions = running,
            ),
        )
    val picker = FakeDirectoryPicker()
    val inspector = RecordingInspector(FakeProjectInspector(inspection, projects, pathKind), gatedInspection)
    val logger = RecordingLogger()

    fun controller(scope: CoroutineScope): ProjectsController =
        ProjectsController(projects, inspector, SessionsApi(sessions, sessions), picker, scope, logger)
}

/** The controller of [world] running in `backgroundScope`, after it has read the initial state. */
internal fun TestScope.start(world: ControllerWorld): ProjectsController =
    world.controller(backgroundScope).also { runCurrent() }

/**
 * Runs [block], usually one intent or event, and lets the controller finish what it started. `runCurrent`,
 * not `advanceUntilIdle`: the latter skips tasks of `backgroundScope`, where the controller runs.
 */
internal fun TestScope.act(block: () -> Unit) {
    block()
    runCurrent()
}

/** The sessions view of a controller whose snapshot is fresh and holds no active session. */
internal val NO_ACTIVE_SESSIONS: SessionsView = SessionsView(counts = emptyMap(), fresh = true)

/** [SessionsView] of a fresh snapshot with these [counts]. */
internal fun freshView(vararg counts: Pair<ProjectId, SessionCounts>): SessionsView =
    SessionsView(counts = counts.toMap(), fresh = true)
