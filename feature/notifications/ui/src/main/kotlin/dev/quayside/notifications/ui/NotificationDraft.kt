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
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceId

/**
 * The settings as the dialog edits them (AM-704); numbers stay text until save. [matrix] has every kind.
 * Part A: global switches, the matrix and the threshold of [NotificationKind.RUNNING_LONG];
 * part B1: the popup; part B2: speech.
 */
public data class NotificationDraft(
    val popups: Boolean,
    val voice: Boolean,
    val matrix: Map<NotificationKind, Set<NotificationChannel>>,
    val runningLongMinutes: String,
    val popup: PopupDraft,
    val speech: SpeechDraft,
)

/** The popup part of [NotificationDraft] (FR-N3); [maxVisible] stays text until save. */
public data class PopupDraft(
    val position: PopupPosition,
    val duration: PopupDuration,
    val maxVisible: String,
    val style: PopupStyle,
)

/** The speech part of [NotificationDraft] (FR-N4); [rate] and [volume] stay text, [templates] has every kind. */
public data class SpeechDraft(
    val voice: VoiceId?,
    val rate: String,
    val volume: String,
    val templates: Map<NotificationKind, String>,
    val readQuestion: Boolean,
    val onlyInBackground: Boolean,
    val muteWhenMicrophoneActive: Boolean,
)

/** The fields of the dialog that a save can refuse. Covers the threshold, the popup and the speech fields. */
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
        speech = speechDraftOf(settings.speech),
    )

private fun speechDraftOf(speech: SpeechSettings): SpeechDraft =
    SpeechDraft(
        voice = speech.voice,
        rate = speech.rate.toString(),
        volume = speech.volume.toString(),
        templates = NotificationKind.entries.associateWith(speech::templateFor),
        readQuestion = speech.readQuestion,
        onlyInBackground = speech.onlyInBackground,
        muteWhenMicrophoneActive = speech.muteWhenMicrophoneActive,
    )

/**
 * The settings of [draft] over [current] (fields outside the draft are kept from [current]). The matrix keeps
 * only kinds whose channels differ from the default. The first refused field wins, in the order of the dialog.
 */
internal fun settingsOf(
    draft: NotificationDraft,
    current: NotificationSettings,
): Outcome<NotificationSettings, InvalidField> {
    val deviations = draft.matrix.filter { (kind, channels) -> channels != NotificationSettings.defaultChannels(kind) }
    return number(draft.runningLongMinutes, MINUTES, NotificationField.RUNNING_LONG_MINUTES).flatMap { minutes ->
        number(draft.popup.maxVisible, VISIBLE, NotificationField.MAX_VISIBLE).flatMap { visible ->
            speechOf(draft.speech, current.speech).map { speech ->
                current.copy(
                    popups = draft.popups,
                    voice = draft.voice,
                    matrix = deviations,
                    runningLongMinutes = minutes,
                    popup = PopupSettings(draft.popup.position, draft.popup.duration, visible, draft.popup.style),
                    speech = speech,
                )
            }
        }
    }
}

/** The speech settings of [draft] over [current]: rate, volume, then templates in the order of the kinds. */
private fun speechOf(
    draft: SpeechDraft,
    current: SpeechSettings,
): Outcome<SpeechSettings, InvalidField> =
    number(draft.rate, RATE, NotificationField.RATE).flatMap { rate ->
        number(draft.volume, VOLUME, NotificationField.VOLUME).flatMap { volume ->
            templatesOf(draft.templates).map { templates ->
                current.copy(
                    voice = draft.voice,
                    rate = rate,
                    volume = volume,
                    templates = templates,
                    readQuestion = draft.readQuestion,
                    onlyInBackground = draft.onlyInBackground,
                    muteWhenMicrophoneActive = draft.muteWhenMicrophoneActive,
                )
            }
        }
    }

/** Trimmed templates that differ from the default; the first kind with a problem is refused. */
private fun templatesOf(draft: Map<NotificationKind, String>): Outcome<Map<NotificationKind, String>, InvalidField> {
    val stored = mutableMapOf<NotificationKind, String>()
    for (kind in NotificationKind.entries) {
        val text = draft[kind].orEmpty().trim()
        if (SpeechSettings.templateProblem(text) != null) {
            return Outcome.Err(InvalidField(NotificationField.TEMPLATE, kind))
        }
        if (text.isNotEmpty() && text != SpeechSettings.defaultTemplate(kind)) stored[kind] = text
    }
    return Outcome.Ok(stored)
}

/** `text.trim()` as an Int inside [range], else Err([field]). */
private fun number(
    text: String,
    range: IntRange,
    field: NotificationField,
): Outcome<Int, InvalidField> {
    val value = parseInRange(text, range)
    return if (value == null) Outcome.Err(InvalidField(field)) else Outcome.Ok(value)
}

/** `text.trim()` as an Int inside [range], else null. */
internal fun parseInRange(
    text: String,
    range: IntRange,
): Int? = text.trim().toIntOrNull()?.takeIf { it in range }

private val MINUTES = NotificationSettings.MIN_RUNNING_LONG_MINUTES..NotificationSettings.MAX_RUNNING_LONG_MINUTES
private val VISIBLE = PopupSettings.MIN_VISIBLE..PopupSettings.MAX_VISIBLE_LIMIT
private val RATE = SpeechSettings.MIN_RATE..SpeechSettings.MAX_RATE
private val VOLUME = SpeechSettings.MIN_VOLUME..SpeechSettings.MAX_VOLUME
