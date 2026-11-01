package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.SessionState
import java.nio.file.Path
import java.time.Instant

/** Which sessions of the project the list shows (FR-S9, first half). */
public enum class SessionFilter {
    ALL,
    ACTIVE,
    FINISHED,
    ;

    /** [ALL] always; [ACTIVE] = [SessionState.isActive]; [FINISHED] = not active. */
    internal fun matches(state: SessionState): Boolean =
        when (this) {
            ALL -> true
            ACTIVE -> state.isActive
            FINISHED -> !state.isActive
        }
}

/** Clicks inside Mission Control. */
internal sealed interface MissionControlEvent {
    data class SelectFilter(
        val filter: SessionFilter,
    ) : MissionControlEvent

    data class SelectSession(
        val id: SessionId,
    ) : MissionControlEvent

    /** "Usuń z listy" in the confirmation dialog. */
    data object ConfirmRemove : MissionControlEvent

    /** "Anuluj", Escape or a click outside the confirmation dialog. */
    data object Dismiss : MissionControlEvent

    /** "Podgląd" in the details panel and "Zamknij" in the preview panel. */
    data object TogglePreview : MissionControlEvent

    /** A click on Stop, Respawn or Remove in the details panel; Remove opens the confirmation. */
    data class Run(
        val action: SessionAction,
    ) : MissionControlEvent

    /** "Attach" in the details panel or the row menu, or a double click on a row (AM-511); `app` handles it. */
    data object Attach : MissionControlEvent

    /** "Odpowiedz" in the details panel or the row menu (AM-511); `app` handles it. */
    data object Reply : MissionControlEvent
}

/** What the user picked: the project (from the sidebar), the filter and the session. */
internal data class Selection(
    val project: ProjectId? = null,
    val view: GlobalView? = null,
    val filter: SessionFilter = SessionFilter.ALL,
    val session: SessionId? = null,
)

internal data class MissionControlUiState(
    val screen: Screen,
    val modeBar: ModeBar,
    val dialog: SessionDialog? = null,
    val views: List<ViewEntry> = emptyList(),
    val preview: PreviewUi? = null,
)

/** An entry of the global views: its [count] of sessions of every project and whether it is shown. */
internal data class ViewEntry(
    val view: GlobalView,
    val count: Int,
    val selected: Boolean,
)

internal sealed interface Screen {
    /** No project on the list: "Brak projektów". */
    data object NoProjects : Screen

    /** Projects exist, none is selected: "Wybierz projekt". */
    data object NoSelection : Screen

    /**
     * @property loading the snapshot is [dev.quayside.sessions.api.SnapshotStatus.Loading]: "Wczytywanie sesji…".
     * @property details null without a (visible) selected session: "Wybierz sesję".
     * @property banner the stale text of [Freshness] when the snapshot is stale, else null.
     */
    data class Project(
        val header: ProjectHeader,
        val filter: SessionFilter,
        val rows: List<SessionRow>,
        val details: SessionDetails?,
        val banner: UiText?,
        val loading: Boolean,
    ) : Screen

    /** A global view: sessions of every project, [sessions] of them, without filter tabs. */
    data class Global(
        val view: GlobalView,
        val sessions: Int,
        val rows: List<SessionRow>,
        val details: SessionDetails?,
        val banner: UiText?,
        val loading: Boolean,
    ) : Screen
}

/** @property sessions every session of the project, whatever the filter. */
internal data class ProjectHeader(
    val name: String,
    val path: Path,
    val sessions: Int,
)

/** Visual tone of a state glyph (docs/05 table); colors are internal constants until AM-802. */
internal enum class Tone { WARN, WORK, OK, ERR, DIM }

/** @property key text key of the state, e.g. `state.working`. */
internal data class Glyph(
    val symbol: String,
    val tone: Tone,
    val key: String,
)

internal data class SessionRow(
    val id: SessionId,
    val glyph: Glyph,
    val name: String,
    val summary: String?,
    val lastEvent: UiText,
    val age: UiText,
    val selected: Boolean,
    val project: UiText? = null,
)

/**
 * FR-S8, only the fields `sessions:api` has. [worktree] is the session cwd.
 * @property canAttach `⏎` and the "Attach" controls apply ([SessionActionsState.canAttach]).
 * @property canReply [canAttach] and the session waits for the user ([SessionActionsState.canReply]).
 */
internal data class SessionDetails(
    val id: SessionId,
    val name: String,
    val glyph: Glyph,
    val summary: String?,
    val worktree: Path,
    val startedAt: Instant,
    val lastActivityAt: Instant,
    val actions: Set<SessionAction> = emptySet(),
    val busy: SessionAction? = null,
    val failure: Failure? = null,
    val canAttach: Boolean = false,
    val canReply: Boolean = false,
)

/** @property waiting @property working counted over the sessions of every project. */
internal data class ModeBar(
    val mode: String = "NORMAL",
    val project: String?,
    val waiting: Int,
    val working: Int,
    val freshness: UiText,
)
