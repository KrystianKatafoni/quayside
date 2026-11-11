package dev.quayside.sessions.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import kotlinx.coroutines.flow.StateFlow

/** How old a disk session may be to be listed (FR-P9); by the modification time of its file. */
@Suppress("MagicNumber") // the choices of the dialog are the numbers
public enum class HistoryAge(
    public val days: Int?,
) {
    DAYS_7(7),
    DAYS_30(30),
    DAYS_90(90),

    /** Any age; [days] is null. */
    UNLIMITED(null),
}

/** How many disk sessions of one project are listed (FR-P9); the newest by modification time. */
@Suppress("MagicNumber")
public enum class HistoryLimit(
    public val count: Int,
) {
    SESSIONS_50(50),
    SESSIONS_200(200),
    SESSIONS_1000(1000),
}

/**
 * Global settings of the disk sessions (FR-P9, ADR 0032).
 *
 * @property maxAge oldest listed session; default 30 days.
 * @property maxPerProject most listed per project; default 200.
 * @property hidden conversation UUIDs removed from the list by "Usuń"; the session files are never deleted.
 * @property places places picked by the user, by conversation UUID (short supervisor id when the UUID is unknown).
 */
public data class SessionHistorySettings(
    val maxAge: HistoryAge = HistoryAge.DAYS_30,
    val maxPerProject: HistoryLimit = HistoryLimit.SESSIONS_200,
    val hidden: Set<SessionId> = emptySet(),
    val places: Map<SessionId, StoredPlace> = emptyMap(),
)

/** One saved place: [project] it was picked in, [place] inside it. [toString] hides the subfolder name (ENG-C8). */
public data class StoredPlace(
    val project: ProjectId,
    val place: PlaceAssignment,
) {
    override fun toString(): String = "StoredPlace(project=$project, place=$place)"
}

/** Stored settings of disk sessions (`session-history.json`). Any dispatcher; never throws. */
public interface SessionHistorySettingsRepo {
    /** Current settings; the defaults when nothing is stored or the file cannot be used. */
    public val settings: StateFlow<SessionHistorySettings>

    /** Saves `change(current)`, then publishes it; nothing is published when saving fails. */
    public suspend fun update(
        change: (SessionHistorySettings) -> SessionHistorySettings,
    ): Outcome<SessionHistorySettings, HistorySettingsError>
}

/** Why [SessionHistorySettingsRepo.update] saved nothing. */
public enum class HistorySettingsError {
    /** The file could not be written. */
    WRITE_FAILED,

    /** The file was written by a newer version of the app and is never overwritten. */
    NEWER_SCHEMA,
}
