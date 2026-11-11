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
    private val places = FakePlaces(world.projects, historySettings)
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
    private val openElsewhere = MutableStateFlow(emptySet<SessionId>())
    private val missingDirs = MutableStateFlow(emptySet<Path>())

    init {
        historySettings.onSaved {
            publishDisk()
            applyPlaces()
        }
        publishDisk()
    }

    override val snapshot: StateFlow<SessionsSnapshot> = state.asStateFlow()

    override val events: Flow<SessionEvent> = transitions.asSharedFlow()

    /** Commands called so far, in order, e.g. `start agent-manager`, `stop 7c5dcf5d`, `stopAllIn agent-manager`. */
    public val calls: List<String> get() = recorded.value

    /** Sessions whose output was read so far, in order. Reads are not [calls]. */
    public val outputReads: List<SessionId> get() = reads.value

    /** The session files' messages ([FakeConversations.set]), the reads of [conversation] and its scripted failure. */
    public val conversationFiles: FakeConversations = FakeConversations()

    /** Adds the session file [file] to the disk, as if it had been written outside the app; shown by the next scan. */
    public fun addPastSession(file: WorldPastSession) {
        pastFiles.update { it.filterNot { old -> old.id == file.id } + file }
        publishDisk()
    }

    /**
     * The session file [id] is deleted outside the app. The disk session stays listed until the next scan (a change
     * of the files or settings), as in the app; [resume] looks at the files and fails with
     * [ResumeProblem.FILE_MISSING].
     */
    public fun removePastSession(id: SessionId) {
        pastFiles.update { it.filterNot { old -> old.id == id } }
    }

    /** The directory [dir] is gone: [resume] of a session started in it fails with [ResumeProblem.DIRECTORY_MISSING]. */
    public fun removeDirectory(dir: Path) {
        // `Path` is an `Iterable<Path>`: `set + dir` would add its name components, not the path
        missingDirs.update { it + setOf(dir) }
    }

    /** The conversation [id] is open in another Claude Code process: its next [resume] runs a copy (R4). */
    public fun opensElsewhere(id: SessionId) {
        openElsewhere.update { it + id }
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
        FakeSessionRules.eventFor(after)?.let { transitions.emit(it) }
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
        state.update { it.copy(status = FakeSessionRules.statusAfter(it.status, available, clock.now())) }
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

    override suspend fun conversation(
        id: SessionId,
        before: MessageCursor?,
    ): Outcome<SessionMessagePage, SessionError> {
        val visible = find(id)?.takeIf { it.origin == SessionOrigin.DISK && pastFiles.value.any { f -> f.id == id } }
        return conversationFiles.read(id, before, visible != null)
    }

    override suspend fun start(
        project: ProjectId,
        spec: NewSessionSpec,
    ): Outcome<SessionId, SessionError> {
        begin("start ${project.value}")?.let { return Outcome.Err(it) }
        val projectDir = projectDirs[project] ?: return Outcome.Err(SessionError.UnknownProject(project))
        val dir = places.startDir(project, projectDir, spec.subfolder).getOrElse { return Outcome.Err(it) }
        val id = SessionId(ID_FORMAT.format(lastId.incrementAndGet()))
        val now = clock.now()
        val owner = SessionRules.projectFor(dir, projectDirs) ?: project
        insert(Session(id, owner, spec.name ?: id.value, dir, SessionState.Working, now, now, summary = null))
        return Outcome.Ok(id)
    }

    /**
     * Same rules as `sessions:impl`: only a visible [SessionOrigin.DISK] session; its file must still exist
     * ([ResumeProblem.FILE_MISSING]) and its start directory too ([ResumeProblem.DIRECTORY_MISSING]). The live session
     * keeps the project of the disk one and waits for input (emits [SessionEvent.BecameBlocked]); a conversation
     * open elsewhere ([opensElsewhere]) runs as a copy under a new id and the disk session stays.
     */
    override suspend fun resume(id: SessionId): Outcome<ResumedSession, SessionError> {
        begin("resume ${id.value}")?.let { return Outcome.Err(it) }
        val disk =
            find(id)?.takeIf { it.origin == SessionOrigin.DISK } ?: return Outcome.Err(SessionError.UnknownSession(id))
        val problem =
            when {
                pastFiles.value.none { it.id == id } -> ResumeProblem.FILE_MISSING
                disk.cwd in missingDirs.value -> ResumeProblem.DIRECTORY_MISSING
                else -> null
            }
        if (problem != null) return Outcome.Err(SessionError.ResumeUnavailable(id, problem))
        val copy = id in openElsewhere.value
        val live = SessionId(if (copy) ID_FORMAT.format(lastId.incrementAndGet()) else id.value.take(UUID_PREFIX))
        val now = clock.now()
        val session =
            disk.copy(
                id = live,
                state = SessionState.AwaitingInput(null),
                startedAt = now,
                lastActivityAt = now,
                origin = SessionOrigin.SUPERVISOR,
            )
        places.resumed(live, id)
        insert(session)
        publishDisk()
        transitions.emit(SessionEvent.BecameBlocked(session))
        return Outcome.Ok(ResumedSession(live, copy))
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

    /**
     * Same rules as `sessions:impl` (ADR 0033): any visible session of a project, in any state; saved in
     * [historySettings] under the conversation, so the place survives a [resume]; never needs the runtime, so it works
     * while [runtimeAvailable] is false. A save that fails ([FakeSessionHistorySettings.failNext]) is
     * `Runtime(COMMAND_FAILED)` and changes nothing.
     */
    override suspend fun assignPlace(
        id: SessionId,
        place: PlaceAssignment?,
    ): Outcome<Unit, SessionError> {
        recorded.update { it + "assignPlace ${id.value}" }
        pendingFailure.getAndUpdate { null }?.let { return Outcome.Err(it) }
        return places.assign(id, find(id), place, conversations.value)
    }

    /** The subfolders of [project] become [names] (a folder added to or removed from the group); places follow. */
    public fun setSubfolders(
        project: ProjectId,
        names: List<String>,
    ) {
        places.setSubfolders(project, names)
        applyPlaces()
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
            val known = supervisor.map { it.id } + conversations.value.values.map(::SessionId)
            val since = settings.maxAge.days?.let { clock.now().minus(it.toLong(), ChronoUnit.DAYS) }
            val (disk, history) = FakeDiskScan.scan(pastFiles.value, known, projectDirs, settings, since)
            current.copy(sessions = ordered(supervisor + disk), history = history)
        }
        applyPlaces()
    }

    private fun applyPlaces() {
        state.update { it.copy(sessions = places.applied(it.sessions, conversations.value)) }
    }

    private fun find(id: SessionId): Session? = state.value.sessions.firstOrNull { it.id == id }

    private fun insert(session: Session) {
        state.update { it.copy(sessions = ordered(it.sessions + session)) }
        applyPlaces()
    }

    private fun replace(session: Session) {
        state.update { current ->
            current.copy(
                sessions =
                    current.sessions.map { if (it.id == session.id) session else it },
            )
        }
        applyPlaces()
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

    private companion object {
        const val ID_FORMAT = "%08d"
        const val UUID_PREFIX = 8
        const val EVENT_BUFFER = 64

        fun ordered(sessions: List<Session>): List<Session> =
            sessions.sortedWith(compareBy<Session>({ it.startedAt }, { it.id.value }))
    }
}
