package dev.quayside.sessions.api

import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.getOrElse
import dev.quayside.core.testing.TestClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import java.nio.file.Path
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Scriptable in-memory sessions feature for tests of other modules (ENG-I7): one state behind both
 * [SessionMonitor] and [SessionCommands]. Passes [SessionMonitorContract] and [SessionCommandsContract].
 *
 * Sessions of [world] are attributed to its projects by [SessionRules.projectFor]. The snapshot is
 * [SnapshotStatus.Fresh] at [Clock.now] from the start, and every change is visible in it at once.
 * Started sessions get ids `00000001`, `00000002`, … and [Clock.now] as their start time.
 *
 * Session files of the world ([WorldPastSession]) are listed as disk sessions (FR-P9, [SessionOrigin.DISK]) by the
 * rules of `sessions:impl`: age and count limit from [historySettings], hidden ones left out, a conversation of a
 * supervisor session not listed twice, the deepest project owns the session. [SessionsSnapshot.history] has a key for
 * every project of the world. The scan is instant; [pauseScan] holds it back, [resumeScan] runs it.
 */
public class FakeSessions(
    world: SessionsWorld = SessionsWorld(),
    private val clock: Clock = TestClock(),
    /** The settings of the disk sessions; a change shows in [snapshot] at once. */
    public val historySettings: FakeSessionHistorySettings = FakeSessionHistorySettings(),
    /** true: the first scan is held back like after [pauseScan] until [resumeScan]; [SessionsSnapshot.history] is empty. */
    startPaused: Boolean = false,
) : SessionMonitor,
    SessionCommands {
    private val projectDirs: Map<ProjectId, Path> = world.projects.associate { it.id to it.dir }
    private val worldProjects: Map<ProjectId, WorldProject> = world.projects.associateBy { it.id }
    private val state =
        MutableStateFlow(SessionsSnapshot(ordered(world.sessions.map(::toSession)), SnapshotStatus.Fresh(clock.now())))
    private val transitions = MutableSharedFlow<SessionEvent>(extraBufferCapacity = EVENT_BUFFER)
    private val recorded = MutableStateFlow(emptyList<String>())
    private val pendingFailure = MutableStateFlow<SessionError?>(null)
    private val runtimeDown = MutableStateFlow(false)
    private val failingStops = MutableStateFlow(emptySet<SessionId>())
    private val lastId = AtomicInteger()
    private val outputs = MutableStateFlow(world.outputs)
    private val pendingOutputFailure = MutableStateFlow<SessionError?>(null)
    private val reads = MutableStateFlow(emptyList<SessionId>())
    private val pastFiles = MutableStateFlow(world.pastSessions)
    private val conversations =
        MutableStateFlow(
            world.sessions
                .mapNotNull { s ->
                    s.conversationId?.let { s.id to it }
                }.toMap(),
        )
    private val scanPaused = MutableStateFlow(startPaused)

    init {
        historySettings.onSaved { publishDisk() }
        publishDisk()
    }

    override val snapshot: StateFlow<SessionsSnapshot> = state.asStateFlow()

    override val events: Flow<SessionEvent> = transitions.asSharedFlow()

    /** Commands called so far, in order, e.g. `start agent-manager`, `stop 7c5dcf5d`, `stopAllIn agent-manager`. */
    public val calls: List<String> get() = recorded.value

    /** Sessions whose output was read so far, in order. Reads are not [calls]. */
    public val outputReads: List<SessionId> get() = reads.value

    /** Adds the session file [file] to the disk, as if it had been written outside the app; shown by the next scan. */
    public fun addPastSession(file: WorldPastSession) {
        pastFiles.update { it.filterNot { old -> old.id == file.id } + file }
        publishDisk()
    }

    /** The scan holds back its result: [SessionsSnapshot.history] and the disk sessions stay as they are. */
    public fun pauseScan() {
        scanPaused.value = true
    }

    /** The held back scan finishes and its result shows in [snapshot]; later changes show at once again. */
    public fun resumeScan() {
        scanPaused.value = false
        publishDisk()
    }

    /** The next [recentOutput] returns `Err(error)`; later reads work again. */
    public fun failNextOutput(error: SessionError) {
        pendingOutputFailure.value = error
    }

    /** Later reads of the output of [id] return [text]. */
    public fun setOutput(
        id: SessionId,
        text: String,
    ) {
        outputs.update { it + (id to text) }
    }

    /** The next command returns `Err(error)` and changes nothing; later commands work again. */
    public fun failNext(error: SessionError) {
        pendingFailure.value = error
    }

    /** The existing session [id] moves to [state]; emits the matching [SessionEvent], if any. */
    public suspend fun setState(
        id: SessionId,
        state: SessionState,
    ) {
        val before = checkNotNull(find(id)) { "FakeSessions has no session ${id.value}" }
        if (before.state == state) return
        val after = before.copy(state = state, lastActivityAt = clock.now())
        replace(after)
        eventFor(after)?.let { transitions.emit(it) }
    }

    /**
     * Adds [session] as if it had been started outside the app. A session that appears waiting emits
     * [SessionEvent.BecameBlocked] (a new request for the user); any other state emits nothing.
     */
    public suspend fun appear(session: WorldSession) {
        val added = toSession(session)
        session.conversationId?.let { uuid -> conversations.update { it + (session.id to uuid) } }
        insert(added)
        publishDisk()
        if (added.state.isWaiting) transitions.emit(SessionEvent.BecameBlocked(added))
    }

    /**
     * false: the snapshot keeps its sessions and turns [SnapshotStatus.Stale] with
     * [RuntimeProblem.NOT_INSTALLED], and every command fails with it; true: fresh again.
     */
    public fun runtimeAvailable(available: Boolean) {
        runtimeDown.value = !available
        state.update { it.copy(status = statusAfter(it.status, available)) }
    }

    /** Every later stop of [id] fails with [RuntimeProblem.COMMAND_FAILED] and leaves it running. */
    public fun failStop(id: SessionId) {
        failingStops.update { it + id }
    }

    override suspend fun recentOutput(id: SessionId): Outcome<RecentOutput, SessionError> {
        reads.update { it + id }
        pendingOutputFailure.getAndUpdate { null }?.let { return Outcome.Err(it) }
        if (runtimeDown.value) return Outcome.Err(SessionError.Runtime(RuntimeProblem.NOT_INSTALLED))
        val session = find(id)
        if (session == null || session.origin == SessionOrigin.DISK) return Outcome.Err(SessionError.UnknownSession(id))
        return Outcome.Ok(RecentOutput(outputs.value[id].orEmpty()))
    }

    override suspend fun start(
        project: ProjectId,
        spec: NewSessionSpec,
    ): Outcome<SessionId, SessionError> {
        begin("start ${project.value}")?.let { return Outcome.Err(it) }
        val projectDir = projectDirs[project] ?: return Outcome.Err(SessionError.UnknownProject(project))
        val dir = startDir(project, projectDir, spec.subfolder).getOrElse { return Outcome.Err(it) }
        val id = SessionId(ID_FORMAT.format(lastId.incrementAndGet()))
        val now = clock.now()
        val owner = SessionRules.projectFor(dir, projectDirs) ?: project
        insert(Session(id, owner, spec.name ?: id.value, dir, SessionState.Working, now, now, summary = null))
        return Outcome.Ok(id)
    }

    override suspend fun stop(id: SessionId): Outcome<Unit, SessionError> {
        begin("stop ${id.value}")?.let { return Outcome.Err(it) }
        val session = find(id) ?: return Outcome.Err(SessionError.UnknownSession(id))
        return stopIfActive(session)
    }

    override suspend fun stopAllIn(project: ProjectId): Outcome<Int, SessionError> {
        begin("stopAllIn ${project.value}")?.let { return Outcome.Err(it) }
        if (project !in projectDirs) return Outcome.Err(SessionError.UnknownProject(project))
        val targets = state.value.sessions.filter { it.project == project && it.state.isActive }
        val failed = targets.filter { stopIfActive(it) is Outcome.Err }.map { it.id }
        val stopped = targets.size - failed.size
        return if (failed.isEmpty()) Outcome.Ok(stopped) else Outcome.Err(SessionError.StopIncomplete(stopped, failed))
    }

    override suspend fun respawn(id: SessionId): Outcome<Unit, SessionError> {
        begin("respawn ${id.value}")?.let { return Outcome.Err(it) }
        val session = inactive(id).getOrElse { return Outcome.Err(it) }
        if (session.origin == SessionOrigin.DISK) return Outcome.Err(SessionError.UnknownSession(id))
        replace(session.copy(state = SessionState.Working, lastActivityAt = clock.now()))
        return Outcome.Ok(Unit)
    }

    override suspend fun remove(id: SessionId): Outcome<Unit, SessionError> {
        begin("remove ${id.value}")?.let { return Outcome.Err(it) }
        val session = inactive(id).getOrElse { return Outcome.Err(it) }
        if (session.origin == SessionOrigin.DISK) {
            return when (historySettings.update { it.copy(hidden = it.hidden + id) }) {
                is Outcome.Ok -> Outcome.Ok(Unit)
                is Outcome.Err -> Outcome.Err(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
            }
        }
        state.update { current -> current.copy(sessions = current.sessions.filterNot { it.id == id }) }
        return Outcome.Ok(Unit)
    }

    /** [projectDir], or its subfolder [name] when it is one of the project's and not [WorldProject.missing]. */
    private fun startDir(
        project: ProjectId,
        projectDir: Path,
        name: String?,
    ): Outcome<Path, SessionError> {
        if (name == null) return Outcome.Ok(projectDir)
        val registered = worldProjects.getValue(project)
        val problem =
            when {
                name !in registered.subfolders -> SubfolderProblem.NOT_IN_PROJECT
                name in registered.missing -> SubfolderProblem.MISSING
                else -> return Outcome.Ok(projectDir.resolve(name))
            }
        return Outcome.Err(SessionError.SubfolderUnavailable(project, problem))
    }

    /** Records [call] and returns the pending failure, if any, else the runtime failure, if any. */
    private fun begin(call: String): SessionError? {
        recorded.update { it + call }
        val runtimeFailure = if (runtimeDown.value) SessionError.Runtime(RuntimeProblem.NOT_INSTALLED) else null
        return pendingFailure.getAndUpdate { null } ?: runtimeFailure
    }

    private fun stopIfActive(session: Session): Outcome<Unit, SessionError> =
        when {
            !session.state.isActive -> {
                Outcome.Ok(Unit)
            }

            session.id in failingStops.value -> {
                Outcome.Err(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
            }

            else -> {
                replace(session.copy(state = SessionState.Stopped, lastActivityAt = clock.now()))
                Outcome.Ok(Unit)
            }
        }

    private fun inactive(id: SessionId): Outcome<Session, SessionError> {
        val session = find(id) ?: return Outcome.Err(SessionError.UnknownSession(id))
        return if (session.state.isActive) Outcome.Err(SessionError.SessionActive(id)) else Outcome.Ok(session)
    }

    /** Runs the scan: the disk sessions and [SessionsSnapshot.history] from the files, the settings and the supervisor. */
    private fun publishDisk() {
        if (scanPaused.value) return
        val settings = historySettings.settings.value
        state.update { current ->
            val supervisor = current.sessions.filter { it.origin == SessionOrigin.SUPERVISOR }
            val (disk, history) = scan(supervisor, settings)
            current.copy(sessions = ordered(supervisor + disk), history = history)
        }
    }

    private fun scan(
        supervisor: List<Session>,
        settings: SessionHistorySettings,
    ): Pair<List<Session>, Map<ProjectId, ProjectHistory>> {
        val since = settings.maxAge.days?.let { clock.now().minus(it.toLong(), ChronoUnit.DAYS) }
        val known = supervisor.map { it.id.value.take(UUID_PREFIX) }.toSet() + conversations.value.values
        val byProject =
            pastFiles.value
                .filter { it.id !in settings.hidden && (since == null || it.modifiedAt >= since) }
                .filter { it.id.value !in known && it.id.value.take(UUID_PREFIX) !in known }
                .groupBy { SessionRules.projectFor(it.cwd, projectDirs) }
        val shown =
            projectDirs.keys.associateWith { project ->
                byProject[project].orEmpty().sortedByDescending { it.modifiedAt }
            }
        val kept = shown.mapValues { (_, files) -> files.take(settings.maxPerProject.count) }
        val history =
            shown.mapValues { (project, files) ->
                ProjectHistory(
                    kept.getValue(project).size,
                    files.size - kept.getValue(project).size,
                )
            }
        return kept.flatMap { (project, files) -> files.map { toDiskSession(it, project) } } to history
    }

    private fun toDiskSession(
        file: WorldPastSession,
        project: ProjectId,
    ): Session =
        Session(
            id = file.id,
            project = project,
            name = file.title ?: file.id.value.take(UUID_PREFIX),
            cwd = file.cwd,
            state = SessionState.Stopped,
            startedAt = file.startedAt ?: file.modifiedAt,
            lastActivityAt = file.lastActivityAt ?: file.modifiedAt,
            summary = null,
            origin = SessionOrigin.DISK,
        )

    private fun find(id: SessionId): Session? = state.value.sessions.firstOrNull { it.id == id }

    private fun insert(session: Session) {
        state.update { it.copy(sessions = ordered(it.sessions + session)) }
    }

    private fun replace(session: Session) {
        state.update { current ->
            current.copy(
                sessions =
                    current.sessions.map { if (it.id == session.id) session else it },
            )
        }
    }

    private fun toSession(session: WorldSession): Session =
        Session(
            id = session.id,
            project = SessionRules.projectFor(session.cwd, projectDirs),
            name = session.name,
            cwd = session.cwd,
            state = session.state,
            startedAt = session.startedAt,
            lastActivityAt = session.startedAt,
            summary = null,
            meta = session.meta,
        )

    private fun statusAfter(
        status: SnapshotStatus,
        available: Boolean,
    ): SnapshotStatus =
        when {
            available && status is SnapshotStatus.Fresh -> {
                status
            }

            available -> {
                SnapshotStatus.Fresh(clock.now())
            }

            status is SnapshotStatus.Stale -> {
                status
            }

            else -> {
                SnapshotStatus.Stale(
                    lastFreshAt = (status as? SnapshotStatus.Fresh)?.at,
                    RuntimeProblem.NOT_INSTALLED,
                )
            }
        }

    private companion object {
        const val ID_FORMAT = "%08d"
        const val UUID_PREFIX = 8
        const val EVENT_BUFFER = 64

        fun ordered(sessions: List<Session>): List<Session> =
            sessions.sortedWith(compareBy<Session>({ it.startedAt }, { it.id.value }))

        fun eventFor(session: Session): SessionEvent? =
            when (session.state) {
                is SessionState.AwaitingInput, is SessionState.AwaitingPermission -> SessionEvent.BecameBlocked(session)
                SessionState.Done -> SessionEvent.TurnFinished(session)
                SessionState.Failed -> SessionEvent.Failed(session)
                SessionState.Working, SessionState.Stopped -> null
            }
    }
}
