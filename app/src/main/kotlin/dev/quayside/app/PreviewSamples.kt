package dev.quayside.app

import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.MissionControlIntent
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.projects.api.StorageIssue
import dev.quayside.projects.api.SubfolderName
import dev.quayside.sessions.api.MessageCursor
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.PlaceAssignment
import dev.quayside.sessions.api.RecentOutput
import dev.quayside.sessions.api.ResumedSession
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionMessagePage
import dev.quayside.sessions.api.SessionMonitor
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.nio.file.Path
import java.time.Instant

private val PREVIEW_NOW: Instant = Instant.parse("2026-01-15T10:00:00Z")

/** The one project of the preview; its directory is no real path of the user. */
internal val PREVIEW_PROJECT: Project =
    Project(ProjectId("preview-project"), Path.of("/preview/quayside"), displayName = "Quayside")

/** The fixed time of the preview, so that the ages in the rows never change. */
internal val PREVIEW_CLOCK: Clock = Clock { PREVIEW_NOW }

private val REFUSED = SessionError.Runtime(RuntimeProblem.COMMAND_FAILED)

private class PreviewMonitor(
    sessions: List<Session>,
) : SessionMonitor {
    override val snapshot: StateFlow<SessionsSnapshot> =
        MutableStateFlow(SessionsSnapshot(sessions, SnapshotStatus.Fresh(PREVIEW_NOW)))
    override val events: Flow<SessionEvent> = emptyFlow()

    override suspend fun recentOutput(id: SessionId): Outcome<RecentOutput, SessionError> =
        Outcome.Err(SessionError.UnknownSession(id))

    override suspend fun conversation(
        id: SessionId,
        before: MessageCursor?,
    ): Outcome<SessionMessagePage, SessionError> = Outcome.Err(SessionError.UnknownSession(id))
}

private object RefusingCommands : SessionCommands {
    override suspend fun start(
        project: ProjectId,
        spec: NewSessionSpec,
    ): Outcome<SessionId, SessionError> = Outcome.Err(REFUSED)

    override suspend fun stop(id: SessionId): Outcome<Unit, SessionError> = Outcome.Err(REFUSED)

    override suspend fun stopAllIn(project: ProjectId): Outcome<Int, SessionError> = Outcome.Err(REFUSED)

    override suspend fun respawn(id: SessionId): Outcome<Unit, SessionError> = Outcome.Err(REFUSED)

    override suspend fun resume(id: SessionId): Outcome<ResumedSession, SessionError> = Outcome.Err(REFUSED)

    override suspend fun remove(id: SessionId): Outcome<Unit, SessionError> = Outcome.Err(REFUSED)

    override suspend fun assignPlace(
        id: SessionId,
        place: PlaceAssignment?,
    ): Outcome<Unit, SessionError> = Outcome.Err(REFUSED)
}

private fun sample(
    id: String,
    state: SessionState,
    minutesAgo: Long,
): Session =
    Session(
        id = SessionId(id),
        project = PREVIEW_PROJECT.id,
        name = id,
        cwd = PREVIEW_PROJECT.dir.resolve(id),
        state = state,
        startedAt = PREVIEW_NOW.minusSeconds(SAMPLE_START_SECONDS),
        lastActivityAt = PREVIEW_NOW.minusSeconds(minutesAgo * SECONDS_PER_MINUTE),
        summary = Strings["preview.summary.$id"],
    )

private const val SECONDS_PER_MINUTE = 60L
private const val SAMPLE_START_SECONDS = 7_200L

/** Sample sessions of [PREVIEW_PROJECT], one in every status; reading only, every command is refused. */
internal fun previewSessions(): SessionsApi =
    SessionsApi(
        PreviewMonitor(
            listOf(
                sample("fix-login", SessionState.AwaitingInput(question = null), minutesAgo = 2),
                sample("migrate-db", SessionState.AwaitingPermission(tool = "Bash"), minutesAgo = 5),
                sample("add-tests", SessionState.Working, minutesAgo = 1),
                sample("update-docs", SessionState.Done, minutesAgo = 30),
                sample("build-image", SessionState.Failed, minutesAgo = 45),
                sample("refactor-ui", SessionState.Stopped, minutesAgo = 90),
            ),
        ),
        RefusingCommands,
    )

private class PreviewProjects : ProjectsApi {
    override val projects: StateFlow<List<Project>> = MutableStateFlow(listOf(PREVIEW_PROJECT))
    override val storageIssue: StateFlow<StorageIssue?> = MutableStateFlow(value = null)

    override suspend fun add(
        dir: Path,
        displayName: String?,
    ): Outcome<Project, ProjectError> = Outcome.Err(ProjectError.UnknownProject(PREVIEW_PROJECT.id))

    override suspend fun remove(id: ProjectId): Outcome<Unit, ProjectError> =
        Outcome.Err(ProjectError.UnknownProject(id))

    override suspend fun rename(
        id: ProjectId,
        displayName: String,
    ): Outcome<Unit, ProjectError> = Outcome.Err(ProjectError.UnknownProject(id))

    override suspend fun setSubfolders(
        id: ProjectId,
        subfolders: List<SubfolderName>,
    ): Outcome<Unit, ProjectError> = Outcome.Err(ProjectError.UnknownProject(id))

    override suspend fun branch(id: ProjectId): String? = null
}

/** [PREVIEW_PROJECT] only; reading only, adding, renaming and removing are refused. */
internal fun previewProjects(): ProjectsApi = PreviewProjects()

/**
 * Mission Control over the sample data with [PREVIEW_PROJECT] selected and the session that waits for an answer
 * selected too, so that the preview shows a selected row and its details; runs in [scope] (the scope of the slot).
 */
internal fun previewController(scope: CoroutineScope): MissionControlController =
    MissionControlController(
        previewSessions(),
        previewProjects(),
        MutableStateFlow<ProjectFocus?>(ProjectFocus(PREVIEW_PROJECT.id)),
        PREVIEW_CLOCK,
        scope,
        NoOpLogger,
    ).also { it.onIntent(MissionControlIntent.SelectSession(PREVIEW_PROJECT.id, SessionId("fix-login"))) }
