package dev.quayside.projects.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectGroups
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.HistoryAge
import dev.quayside.sessions.api.HistoryLimit
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionHistorySettings
import dev.quayside.sessions.api.SessionHistorySettingsRepo
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.SessionsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * What `app` supplies for the disk sessions of a new project (FR-P9, AM-312): the global settings behind the two
 * choices of the add dialog, and [showFinished], which selects the project in Mission Control with the filter
 * "Zakończone" (the answer "Pokaż").
 */
public class PastSessionsPorts(
    public val settings: SessionHistorySettingsRepo,
    public val showFinished: (ProjectId) -> Unit,
)

/** The two global choices shown in the add dialog; saved at once (no draft). */
internal data class HistoryChoice(
    val maxAge: HistoryAge,
    val maxPerProject: HistoryLimit,
)

/**
 * Disk sessions found for a new project: [total], and for a project with subfolders how many started in a checked
 * subfolder ([inSubfolders]) and how many elsewhere in the project ([inRoot]); both null for a project without
 * subfolders.
 */
internal data class PastCounts(
    val total: Int,
    val inSubfolders: Int?,
    val inRoot: Int?,
)

/** Pure rules of the question "Znaleziono N starych sesji" (FR-P9). */
internal object PastSessionCounts {
    /**
     * Counts the disk sessions of [project] in [sessions]; supervisor sessions and other projects are ignored. A
     * project with subfolders also gets the split: those that started in a checked subfolder or below it, and the
     * rest (the project directory and unchecked folders).
     */
    fun of(
        project: Project,
        sessions: List<Session>,
    ): PastCounts {
        val mine = sessions.filter { it.origin == SessionOrigin.DISK && it.project == project.id }
        if (project.subfolders.isEmpty()) return PastCounts(mine.size, inSubfolders = null, inRoot = null)
        val inSubfolders = mine.count { ProjectGroups.subfolderOf(project, it.cwd) != null }
        return PastCounts(mine.size, inSubfolders, mine.size - inSubfolders)
    }
}

/** How long the sidebar waits for the first scan of a new project before it gives up asking. */
private val SCAN_WAIT = 30.seconds

/**
 * The disk sessions of a new project in [ProjectsController] (FR-P9, AM-312): the global choices of the add dialog
 * and the question after adding. [expect] waits for the first scan of the project and asks once when it found
 * sessions; opening any dialog, removing the project, another [expect] or [SCAN_WAIT] ends the wait without a
 * question. Nothing is stored per project; a failed save of a choice leaves the choice as it was and shows the
 * fixed text `history.error` in the add dialog.
 */
internal class PastSessionsFlow(
    private val ui: MutableStateFlow<ProjectsUiState>,
    private val projects: ProjectsApi,
    private val snapshot: StateFlow<SessionsSnapshot>,
    private val ports: PastSessionsPorts,
    private val scope: CoroutineScope,
) {
    private var waiting: Job? = null

    /** The saved choices, for a new add dialog. */
    fun choices(): HistoryChoice =
        ports.settings.settings.value
            .toChoice()

    /** Waits for the scan that has a [SessionsSnapshot.history] key of [project] and asks when it found sessions. */
    fun expect(project: Project) {
        waiting?.cancel()
        waiting =
            scope.launch {
                val found = withTimeoutOrNull(SCAN_WAIT) { scanned(project.id) }
                val now = found?.let { (list, sessions) -> list.find { it.id == project.id }?.let { it to sessions } }
                if (now != null) ask(now.first, now.second)
            }
    }

    /** The projects and sessions of the first state that has a scan of [id]; null when the wait is abandoned. */
    private suspend fun scanned(id: ProjectId): Pair<List<Project>, List<Session>>? =
        combine(projects.projects, snapshot, ui.map { it.dialog != null }) { list, snap, dialogOpen ->
            when {
                dialogOpen || list.none { it.id == id } -> Verdict.Abandoned
                id in snap.history -> Verdict.Ready(list, snap.sessions)
                else -> Verdict.Waiting
            }
        }.first { it != Verdict.Waiting }
            .let { (it as? Verdict.Ready)?.let { ready -> ready.list to ready.sessions } }

    private fun ask(
        project: Project,
        sessions: List<Session>,
    ) {
        val counts = PastSessionCounts.of(project, sessions)
        if (counts.total == 0) return
        // Guard against a late state: a dialog opened after the wait ended must not be replaced by the question.
        ui.update { if (it.dialog == null) it.copy(dialog = Dialog.PastSessions(project, counts)) else it }
    }

    /** Runs [event] if it belongs to this flow and returns true; a click that does not fit is ignored. */
    fun handle(
        event: SidebarEvent,
        dialog: Dialog?,
    ): Boolean {
        when (event) {
            SidebarEvent.ShowPastSessions -> (dialog as? Dialog.PastSessions)?.let { show(it.project.id) }
            is SidebarEvent.ChooseHistoryAge -> choose(dialog) { it.copy(maxAge = event.age) }
            is SidebarEvent.ChooseHistoryLimit -> choose(dialog) { it.copy(maxPerProject = event.limit) }
            else -> return false
        }
        return true
    }

    /** "Pokaż": closes the question, selects the project and lets `app` select the filter "Zakończone". */
    private fun show(id: ProjectId) {
        ui.update { it.copy(dialog = null).reduce(SidebarEvent.Select(id)) }
        ports.showFinished(id)
    }

    /** Saves a choice of the add dialog at once as the global setting; shown only after it was saved. */
    private fun choose(
        dialog: Dialog?,
        change: (SessionHistorySettings) -> SessionHistorySettings,
    ) {
        if (dialog !is Dialog.ConfirmAdd || dialog.history == null) return
        scope.launch {
            val saved = ports.settings.update(change)
            ui.update {
                when (saved) {
                    is Outcome.Ok -> it.withChoice(dialog, saved.value.toChoice(), error = null)
                    is Outcome.Err -> it.withChoice(dialog, it.choiceOf(dialog), UiText("history.error"))
                }
            }
        }
    }

    private sealed interface Verdict {
        data object Waiting : Verdict

        data object Abandoned : Verdict

        class Ready(
            val list: List<Project>,
            val sessions: List<Session>,
        ) : Verdict
    }
}

private fun SessionHistorySettings.toChoice(): HistoryChoice = HistoryChoice(maxAge, maxPerProject)

/**
 * [choice] and [error] in the add dialog that is still open for the same folder as [shown] (a guard against a late
 * result after the user cancelled and picked another folder); any other dialog stays as it is.
 */
private fun ProjectsUiState.withChoice(
    shown: Dialog.ConfirmAdd,
    choice: HistoryChoice?,
    error: UiText?,
): ProjectsUiState {
    val open = dialog as? Dialog.ConfirmAdd
    return if (open != null &&
        open.report == shown.report
    ) {
        copy(dialog = open.copy(history = choice, error = error))
    } else {
        this
    }
}

/** The choice shown now in the add dialog of the same folder as [shown]. */
private fun ProjectsUiState.choiceOf(shown: Dialog.ConfirmAdd): HistoryChoice? =
    (dialog as? Dialog.ConfirmAdd)?.takeIf { it.report == shown.report }?.history
