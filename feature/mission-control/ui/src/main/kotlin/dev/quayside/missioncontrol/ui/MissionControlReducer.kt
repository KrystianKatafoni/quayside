package dev.quayside.missioncontrol.ui

import dev.quayside.core.designsystem.StatusTone
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectGroups
import dev.quayside.projects.api.SubfolderName
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object MissionControlReducer {
    /** Whole screen from inputs; a selected session that is gone or filtered out is dropped. */
    fun reduce(
        snapshot: SessionsSnapshot,
        projects: List<Project>,
        selection: Selection,
        now: Instant,
        zone: ZoneId,
    ): MissionControlUiState {
        val project = projects.find { it.id == selection.project }.takeIf { selection.view == null }
        val screen =
            when {
                selection.view != null -> selection.view.screen(snapshot, projects, selection, now, zone)
                projects.isEmpty() -> Screen.NoProjects
                project == null -> Screen.NoSelection
                else -> page(snapshot, project, selection, now, zone)
            }
        val modeBar =
            ModeBar(
                project = project?.let { shownName(it, selection.subfolder) },
                waiting = snapshot.sessions.count { it.state.isWaiting },
                working = snapshot.sessions.count { it.state == SessionState.Working },
                freshness = Freshness.of(snapshot.status, zone, short = true),
            )
        val views =
            GlobalView.entries.map { view ->
                ViewEntry(view, snapshot.sessions.count { view.matches(it.state) }, selected = view == selection.view)
            }
        return MissionControlUiState(screen, modeBar, views = views)
    }

    private fun GlobalView.screen(
        snapshot: SessionsSnapshot,
        projects: List<Project>,
        selection: Selection,
        now: Instant,
        zone: ZoneId,
    ): Screen.Global {
        val visible = SessionScope.visible(snapshot.sessions, selection, projects)
        val picked = visible.find { it.id == selection.session }
        val names = projects.associate { it.id to UiText("row.project", listOf(it.displayName)) }
        return Screen.Global(
            view = this,
            sessions = visible.size,
            rows = visible.map { row(it, now, it == picked, names[it.project] ?: UiText("row.noProject")) },
            details = picked?.let(::details),
            banner = (snapshot.status as? SnapshotStatus.Stale)?.let { Freshness.of(it, zone) },
            loading = snapshot.status == SnapshotStatus.Loading,
        )
    }

    private fun page(
        snapshot: SessionsSnapshot,
        project: Project,
        selection: Selection,
        now: Instant,
        zone: ZoneId,
    ): Screen.Project {
        val subfolder = selection.subfolder
        val mine = snapshot.sessions.filter { it.project == project.id && it.isIn(project, subfolder) }
        val visible = SessionScope.visible(snapshot.sessions, selection, listOf(project))
        val picked = visible.find { it.id == selection.session }
        val loading = snapshot.status == SnapshotStatus.Loading
        return Screen.Project(
            header = ProjectHeader(shownName(project, subfolder), shownDir(project, subfolder), mine.size),
            filter = selection.filter,
            rows = visible.map { row(it, now, it == picked, place = place(project, subfolder, it)) },
            details = picked?.let(::details),
            banner = (snapshot.status as? SnapshotStatus.Stale)?.let { Freshness.of(it, zone) },
            loading = loading,
            stats = SessionStats.of(mine.map { it.state }),
            overview = if (subfolder == null && !loading) GroupOverviews.of(project, snapshot.sessions) else null,
            older = if (subfolder == null) snapshot.history[project.id]?.older ?: 0 else 0,
        )
    }

    /** The place badge exists on the group page only, not on a subfolder page. */
    private fun place(
        project: Project,
        subfolder: SubfolderName?,
        session: Session,
    ): UiText? = if (subfolder == null) SessionPlace.of(project, session.cwd) else null

    /** "plant-puls / soft" on a subfolder page, else the project name (FR-P7). */
    private fun shownName(
        project: Project,
        subfolder: SubfolderName?,
    ): String = if (subfolder == null) project.displayName else "${project.displayName} / ${subfolder.value}"

    private fun shownDir(
        project: Project,
        subfolder: SubfolderName?,
    ): Path = if (subfolder == null) project.dir else ProjectGroups.dirOf(project, subfolder)

    private fun row(
        session: Session,
        now: Instant,
        selected: Boolean,
        project: UiText? = null,
        place: UiText? = null,
    ): SessionRow =
        SessionRow(
            id = session.id,
            glyph = StatusLook.of(session.state),
            name = session.name,
            summary = session.summary,
            lastEvent = LastEvent.of(session.state),
            age = AgeText.of(session.lastActivityAt, now),
            selected = selected,
            project = project,
            place = place,
            fromDisk = session.origin == SessionOrigin.DISK,
        )

    private fun details(session: Session): SessionDetails =
        SessionDetails(
            id = session.id,
            name = session.name,
            glyph = StatusLook.of(session.state),
            summary = session.summary,
            worktree = session.cwd,
            startedAt = session.startedAt,
            lastActivityAt = session.lastActivityAt,
            meta = session.meta,
            fromDisk = session.origin == SessionOrigin.DISK,
        )
}

/** The "last event" column, e.g. "prosi o zgodę: Bash". */
internal object LastEvent {
    // The question of AwaitingInput is user data: the column only says that the session waits.
    fun of(state: SessionState): UiText =
        when (state) {
            is SessionState.AwaitingPermission -> permission(state.tool)
            is SessionState.AwaitingInput -> UiText("event.input")
            SessionState.Working -> UiText("event.working")
            SessionState.Done -> UiText("event.done")
            SessionState.Failed -> UiText("event.failed")
            SessionState.Stopped -> UiText("event.stopped")
        }

    private fun permission(tool: String?): UiText =
        tool?.let { UiText("event.permission", listOf(it)) } ?: UiText("event.permission.unknown")
}

/** Age of a session in s / min / h / d, rounded down; a negative age is "now". */
internal object AgeText {
    private const val MINUTE = 60L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun of(
        from: Instant,
        now: Instant,
    ): UiText {
        val seconds = Duration.between(from, now).seconds
        return when {
            seconds <= 0 -> UiText("age.now")
            seconds < MINUTE -> UiText("age.s", listOf("$seconds"))
            seconds < HOUR -> UiText("age.min", listOf("${seconds / MINUTE}"))
            seconds < DAY -> UiText("age.h", listOf("${seconds / HOUR}"))
            else -> UiText("age.d", listOf("${seconds / DAY}"))
        }
    }
}

/**
 * Freshness of the snapshot; times as HH:mm:ss in [zone]. The long text names the [RuntimeProblem] (stale banner),
 * the [short] one only the time (mode bar).
 */
internal object Freshness {
    private val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** Tone of the freshness dot for the [short] text: OK when live, NEUTRAL while loading, ERR when stale. */
    fun tone(short: UiText): StatusTone =
        when (short.key) {
            "fresh.live" -> StatusTone.OK
            "fresh.loading" -> StatusTone.NEUTRAL
            else -> StatusTone.ERR
        }

    fun of(
        status: SnapshotStatus,
        zone: ZoneId,
        short: Boolean = false,
    ): UiText =
        when (status) {
            SnapshotStatus.Loading -> UiText("fresh.loading")
            is SnapshotStatus.Fresh -> UiText("fresh.live")
            is SnapshotStatus.Stale -> stale(status, zone, short)
        }

    private fun stale(
        status: SnapshotStatus.Stale,
        zone: ZoneId,
        short: Boolean,
    ): UiText {
        val kind = if (status.lastFreshAt == null) "never" else "stale"
        val key = if (short) "fresh.short.$kind" else "fresh.$kind.${status.problem.name}"
        return UiText(key, listOfNotNull(status.lastFreshAt?.let { time.withZone(zone).format(it) }))
    }
}
