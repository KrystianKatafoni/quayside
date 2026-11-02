package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.flatMap
import dev.quayside.core.domain.map
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.PopupDuration
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupSettings
import dev.quayside.notifications.api.PopupStyle

/**
 * The settings as the dialog edits them (AM-704); numbers stay text until save. [matrix] has every kind.
 * Part A: global switches, the matrix and the threshold of [NotificationKind.RUNNING_LONG]; part B1: the popup.
 */
public data class NotificationDraft(
    val popups: Boolean,
    val voice: Boolean,
    val matrix: Map<NotificationKind, Set<NotificationChannel>>,
    val runningLongMinutes: String,
    val popup: PopupDraft,
)

/** The popup part of [NotificationDraft] (FR-N3); [maxVisible] stays text until save. */
public data class PopupDraft(
    val position: PopupPosition,
    val duration: PopupDuration,
    val maxVisible: String,
    val style: PopupStyle,
)

/** The fields of the dialog that a save can refuse. Part B adds the popup and speech fields. */
public enum class NotificationField { RUNNING_LONG_MINUTES, MAX_VISIBLE, RATE, VOLUME, TEMPLATE }

/** A field the last save refused; [kind] only for [NotificationField.TEMPLATE]. */
public data class InvalidField(
    val field: NotificationField,
    val kind: NotificationKind? = null,
)

/** The draft of [settings]: every kind in the matrix (its channels or the default), numbers as text. */
internal fun draftOf(settings: NotificationSettings): NotificationDraft =
    NotificationDraft(
        popups = settings.popups,
        voice = settings.voice,
        matrix = NotificationKind.entries.associateWith(settings::channelsFor),
        runningLongMinutes = settings.runningLongMinutes.toString(),
        popup =
            PopupDraft(
                position = settings.popup.position,
                duration = settings.popup.duration,
                maxVisible = settings.popup.maxVisible.toString(),
                style = settings.popup.style,
            ),
    )

/**
 * The settings of [draft] over [current] (fields the dialog does not show are kept). The matrix keeps only kinds
 * whose channels differ from the default. The first refused field wins, in the order of the dialog.
 */
internal fun settingsOf(
    draft: NotificationDraft,
    current: NotificationSettings,
): Outcome<NotificationSettings, InvalidField> {
    val deviations = draft.matrix.filter { (kind, channels) -> channels != NotificationSettings.defaultChannels(kind) }
    return number(draft.runningLongMinutes, MINUTES, NotificationField.RUNNING_LONG_MINUTES).flatMap { minutes ->
        number(draft.popup.maxVisible, VISIBLE, NotificationField.MAX_VISIBLE).map { visible ->
            current.copy(
                popups = draft.popups,
                voice = draft.voice,
                matrix = deviations,
                runningLongMinutes = minutes,
                popup = PopupSettings(draft.popup.position, draft.popup.duration, visible, draft.popup.style),
            )
        }
    }
}

/** `text.trim()` as an Int inside [range], else Err([field]). */
private fun number(
    text: String,
    range: IntRange,
    field: NotificationField,
): Outcome<Int, InvalidField> {
    val value = text.trim().toIntOrNull()?.takeIf { it in range }
    return if (value == null) Outcome.Err(InvalidField(field)) else Outcome.Ok(value)
}

private val MINUTES = NotificationSettings.MIN_RUNNING_LONG_MINUTES..NotificationSettings.MAX_RUNNING_LONG_MINUTES
private val VISIBLE = 1..PopupSettings.MAX_VISIBLE_LIMIT
