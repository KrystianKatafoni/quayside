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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Scriptable in-memory sessions feature for tests of other modules (ENG-I7): one state behind both
 * [SessionMonitor] and [SessionCommands]. Passes [SessionMonitorContract] and [SessionCommandsContract].
 *
 * Sessions of [world] are attributed to its projects by [SessionRules.projectFor]. The snapshot is
 * [SnapshotStatus.Fresh] at [Clock.now] from the start, and every change is visible in it at once.
 * Started sessions get ids `00000001`, `00000002`, … and [Clock.now] as their start time.
 */
public class FakeSessions(
    world: SessionsWorld = SessionsWorld(),
    private val clock: Clock = TestClock(),
) : SessionMonitor,
    SessionCommands {
    private val projectDirs: Map<ProjectId, Path> = world.projects.associate { it.id to it.dir }
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

    override val snapshot: StateFlow<SessionsSnapshot> = state.asStateFlow()

    override val events: Flow<SessionEvent> = transitions.asSharedFlow()

    /** Commands called so far, in order, e.g. `start agent-manager`, `stop 7c5dcf5d`, `stopAllIn agent-manager`. */
    public val calls: List<String> get() = recorded.value

    /** Sessions whose output was read so far, in order. Reads are not [calls]. */
    public val outputReads: List<SessionId> get() = reads.value

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
        insert(added)
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
        if (find(id) == null) return Outcome.Err(SessionError.UnknownSession(id))
        return Outcome.Ok(RecentOutput(outputs.value[id].orEmpty()))
    }

    override suspend fun start(
        project: ProjectId,
        spec: NewSessionSpec,
    ): Outcome<SessionId, SessionError> {
        begin("start ${project.value}")?.let { return Outcome.Err(it) }
        val dir = projectDirs[project] ?: return Outcome.Err(SessionError.UnknownProject(project))
        val id = SessionId(ID_FORMAT.format(lastId.incrementAndGet()))
        val now = clock.now()
        insert(Session(id, project, spec.name ?: id.value, dir, SessionState.Working, now, now, summary = null))
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
        replace(session.copy(state = SessionState.Working, lastActivityAt = clock.now()))
        return Outcome.Ok(Unit)
    }

    override suspend fun remove(id: SessionId): Outcome<Unit, SessionError> {
        begin("remove ${id.value}")?.let { return Outcome.Err(it) }
        inactive(id).getOrElse { return Outcome.Err(it) }
        state.update { current -> current.copy(sessions = current.sessions.filterNot { it.id == id }) }
        return Outcome.Ok(Unit)
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
