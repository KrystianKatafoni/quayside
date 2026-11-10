package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.SessionMonitor
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * State holder of Mission Control (MVI, docs/11 §1) for the project selected in the sidebar or a global view.
 * Collects in [scope] (the window scope, Main dispatcher; not thread-safe) and re-reads [clock] every [tick]
 * for session ages. Never throws; runs only the stop, respawn and remove commands of [sessions] (attach is
 * a query, [attachTarget]), one at a time, and logs them with session ids and fixed categories (ENG-C8).
 * Shows only [SessionMonitor.snapshot].
 * @throws IllegalArgumentException if [tick] is not positive.
 */
@Suppress("LongParameterList") // tick is a test seam (AM-410 plan, question 3)
public class MissionControlController(
    sessions: SessionsApi,
    private val projects: ProjectsApi,
    selected: StateFlow<ProjectFocus?>,
    clock: Clock,
    private val scope: CoroutineScope,
    logger: Logger,
    tick: Duration = 1.seconds,
) {
    init {
        require(tick.isPositive()) { "tick must be positive" }
    }

    /** Times of the stale banner are shown in the zone of the Mac. */
    private val zone: ZoneId = ZoneId.systemDefault()
    private val selection =
        MutableStateFlow(Selection(project = selected.value?.project, subfolder = selected.value?.subfolder))
    private val now = MutableStateFlow(clock.now())
    private val monitor = sessions.monitor
    private val runner = SessionActionRunner(sessions.commands, logger)
    private val ui = MutableStateFlow(ActionUi())
    private val preview = PreviewState(monitor, selection, projects.projects, scope)

    private val shownProject = selection.map { if (it.view == null) it.project else null }.distinctUntilChanged()

    /**
     * The branch of the shown project (FR-A1): read again when another project is shown and on every snapshot, so a
     * checkout shows up with the next refresh of the sessions.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val branch: StateFlow<String?> =
        shownProject
            .combine(monitor.snapshot) { id, _ -> id }
            .mapLatest { id -> id?.let { projects.branch(it) } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** Read by the composables only. */
    internal val state: StateFlow<MissionControlUiState> =
        combine(
            monitor.snapshot,
            projects.projects,
            selection,
            now,
            combine(ui, preview.shown, branch, ::Triple),
        ) { snapshot, list, picked, at, (acting, shown, onBranch) ->
            MissionControlReducer
                .reduce(snapshot, list, picked, at, zone)
                .withActions(snapshot, picked, acting, list)
                .copy(preview = shown)
                .withBranch(onBranch)
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            MissionControlReducer
                .reduce(monitor.snapshot.value, projects.projects.value, selection.value, now.value, zone)
                .withActions(monitor.snapshot.value, selection.value, ui.value, projects.projects.value)
                .copy(preview = preview.shown.value),
        )

    init {
        // Another project starts with the whole list and no selected session (plan, decision 1).
        scope.launch {
            selected.collect { focus -> selection.update { it.onFocus(focus) } }
        }
        // A failure is shown until the next action or until another session is selected (plan, decision 4).
        scope.launch {
            selection.map { Triple(it.project, it.view, it.session) }.distinctUntilChanged().collect {
                ui.update { shown -> shown.copy(failure = null) }
            }
        }
        // Ages tick only while a project or a global view is shown (plan, decision 5).
        scope.launch {
            selection.map { it.project != null || it.view != null }.distinctUntilChanged().collectLatest { shown ->
                if (shown) now.value = clock.now()
                while (shown) {
                    delay(tick)
                    now.value = clock.now()
                }
            }
        }
    }

    /**
     * The names the window crumbs need (AM-816): the shown project (none in a global view) and the sessions by id.
     * Never contains a path or a summary.
     */
    public val names: StateFlow<ShownNames> =
        combine(monitor.snapshot, state.map { it.modeBar.project }.distinctUntilChanged(), ::namesOf)
            .stateIn(scope, SharingStarted.Eagerly, namesOf(monitor.snapshot.value, state.value.modeBar.project))

    /** The shown global view, null on the project page. `app` hides filter commands and clears the sidebar by it. */
    public val view: StateFlow<GlobalView?> =
        selection.map { it.view }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, selection.value.view)

    /**
     * Actions of the selected visible session, for the palette (AM-410).
     * [SessionActionsState.canStop], `canRespawn` and `canRemove` are false while an action runs or a dialog is open;
     * `canPreview` stays true while an action runs and is false only with a dialog open.
     * `canAttach` behaves like `canPreview` and also needs the project of the session in the project list;
     * `canReply` is `canAttach` for a session that waits for the user.
     * The target of a run is read again from the snapshot, so this may lag one step behind.
     */
    public val actions: StateFlow<SessionActionsState> =
        combine(monitor.snapshot, selection, ui, preview.open, projects.projects, ::actionsOf).stateIn(
            scope,
            SharingStarted.Eagerly,
            actionsOf(monitor.snapshot.value, selection.value, ui.value, preview.open.value, projects.projects.value),
        )

    /**
     * The session `⏎` and `r` (when [AttachTarget.waiting]) attach to, read now from the snapshot, the selection
     * and the project list (not from [actions], which may lag one step); null without a selected visible session,
     * for a session outside the registered projects and while a dialog of Mission Control is open.
     * Pure query: no state change, no log.
     */
    public fun attachTarget(): AttachTarget? =
        SessionAttach.targetOf(
            SessionActions.target(monitor.snapshot.value.sessions, selection.value, projects.projects.value),
            projects.projects.value,
            ui.value.dialog != null,
        )

    /**
     * Shows [project] on the filter "Zakończone" in one step (FR-P9, the answer "Pokaż" after adding a folder). The
     * sidebar selects the same project a moment later; a focus on the shown project keeps the filter, whereas
     * selecting the project first and the filter second would lose the filter to the reset of a new project.
     */
    public fun showFinished(project: ProjectId) {
        selection.value = Selection(project = project, filter = SessionFilter.FINISHED)
    }

    /** Handles [intent] from the palette and window keys; ignored without a shown project, view or rows. */
    public fun onIntent(intent: MissionControlIntent) {
        when (intent) {
            is MissionControlIntent.SelectFilter -> onEvent(MissionControlEvent.SelectFilter(intent.filter))
            else -> if (ui.value.dialog == null) onSessionIntent(intent)
        }
    }

    private fun onSessionIntent(intent: MissionControlIntent) {
        when (intent) {
            MissionControlIntent.NextSession -> moveCursor(SessionCursor::next)
            MissionControlIntent.PreviousSession -> moveCursor(SessionCursor::previous)
            MissionControlIntent.StopSelected -> run(SessionAction.STOP)
            MissionControlIntent.RespawnSelected -> run(SessionAction.RESPAWN)
            MissionControlIntent.RemoveSelected -> askToRemove()
            is MissionControlIntent.SelectFilter -> Unit
            is MissionControlIntent.SelectSession -> selection.value = intent.toSelection()
            MissionControlIntent.TogglePreview -> togglePreview()
            is MissionControlIntent.ShowView -> selection.update { it.copy(view = intent.view, session = null) }
        }
    }

    /**
     * Ignored while a dialog is open; otherwise [PreviewState.toggle] reads the target from fresh data. The guard
     * protects the [onEvent] path (a click on "Podgląd" or "Zamknij"); [onIntent] already ignores intents then.
     */
    private fun togglePreview() {
        if (ui.value.dialog == null) preview.toggle()
    }

    /** Opens the confirmation for the selected inactive session; nothing is called until it is confirmed. */
    private fun askToRemove() {
        val target = SessionActions.target(monitor.snapshot.value.sessions, selection.value, projects.projects.value)
        if (ui.value.busy != null || ui.value.dialog != null || target == null) return
        if (SessionAction.REMOVE !in SessionActions.available(target.state, busy = false, target.origin)) return
        ui.value =
            ActionUi(dialog = SessionDialog.ConfirmRemove(target.id, target.name, target.origin == SessionOrigin.DISK))
    }

    /**
     * Removes the session of the open dialog, not the current selection. Activity is deliberately not checked
     * again: `remove` answers `SessionActive` for a resumed session and that becomes a [Failure] (plan, decision 4).
     */
    private fun confirmRemove() {
        val dialog = ui.value.dialog as? SessionDialog.ConfirmRemove ?: return
        launchAction(SessionAction.REMOVE, dialog.id)
    }

    /**
     * Runs [action] on the selected visible session read now from the snapshot, not from [state]. [ui] is set before
     * the launch, so a second call before the coroutine starts is ignored.
     */
    private fun run(action: SessionAction) {
        val target = SessionActions.target(monitor.snapshot.value.sessions, selection.value, projects.projects.value)
        if (ui.value.busy != null || ui.value.dialog != null || target == null) return
        if (action !in SessionActions.available(target.state, busy = false, target.origin)) return
        launchAction(action, target.id)
    }

    private fun launchAction(
        action: SessionAction,
        id: SessionId,
    ) {
        ui.value = ActionUi(busy = id to action)
        scope.launch {
            val reason = runner.run(action, id)
            ui.value = ActionUi(failure = reason?.let { Failure(id, action, it) })
        }
    }

    /** Counts from the current selection, not from [state], so two quick keys move two rows. */
    private fun moveCursor(move: (List<SessionId>, SessionId?) -> SessionId?) {
        val rows =
            SessionScope
                .visible(
                    monitor.snapshot.value.sessions,
                    selection.value,
                    projects.projects.value,
                ).map { it.id }
        selection.update { current -> current.copy(session = move(rows, current.session) ?: current.session) }
    }

    /** Handles a click in the filter or the session list. */
    internal fun onEvent(event: MissionControlEvent) {
        when (event) {
            is MissionControlEvent.SelectFilter -> selection.update { it.withFilter(event.filter) }

            is MissionControlEvent.SelectSession -> selection.update { it.copy(session = event.id) }

            is MissionControlEvent.Run -> if (event.action == SessionAction.REMOVE) askToRemove() else run(event.action)

            MissionControlEvent.TogglePreview -> togglePreview()

            MissionControlEvent.ConfirmRemove -> confirmRemove()

            MissionControlEvent.Dismiss -> ui.update { it.copy(dialog = null) }

            // `app`'s job, not the controller's: MissionControl routes these to its callbacks (Attach and Reply open
            // a terminal tab, AM-511; OpenSubfolder selects the subfolder in the sidebar, AM-307).
            MissionControlEvent.Attach,
            MissionControlEvent.Reply,
            MissionControlEvent.ChangeHistoryLimit,
            is MissionControlEvent.OpenSubfolder,
            -> Unit
        }
    }
}

private fun MissionControlIntent.SelectSession.toSelection() = Selection(project, session = id, subfolder = subfolder)

private fun namesOf(
    snapshot: SessionsSnapshot,
    project: String?,
): ShownNames = ShownNames(project, snapshot.sessions.associate { it.id to it.name })

private fun actionsOf(
    snapshot: SessionsSnapshot,
    picked: Selection,
    acting: ActionUi,
    previewOpen: Boolean,
    projects: List<Project>,
): SessionActionsState {
    val target = SessionActions.target(snapshot.sessions, picked, projects)
    val attach = SessionAttach.targetOf(target, projects, acting.dialog != null)
    val blocked = acting.busy != null || acting.dialog != null
    val available = target?.let { SessionActions.available(it.state, blocked, it.origin) }.orEmpty()
    return SessionActionsState(
        canStop = SessionAction.STOP in available,
        canRespawn = SessionAction.RESPAWN in available,
        canRemove = SessionAction.REMOVE in available,
        dialogOpen = acting.dialog != null,
        canPreview = target?.origin == SessionOrigin.SUPERVISOR && acting.dialog == null,
        previewOpen = previewOpen,
        canAttach = attach != null,
        canReply = attach?.waiting == true,
    )
}

/**
 * A global view has no filter (plan, decision 2). Guards against a late palette command (`view` is a step behind
 * through `stateIn`); it has no visible effect in the UI.
 */
private fun Selection.withFilter(filter: SessionFilter): Selection = if (view == null) copy(filter = filter) else this
