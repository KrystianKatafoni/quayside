package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettingsError
import dev.quayside.notifications.api.NotificationSettingsRepo
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceCatalog
import dev.quayside.notifications.api.VoiceOption
import dev.quayside.notifications.api.VoicePreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The open dialog. [invalid]: the field the last save refused. [saveError]: why the last write changed nothing.
 * [voices]: the installed voices; null while loading or after a failure, then [voicesFailed] tells which.
 * [previewing]: a voice sample plays. [previewFailed]: the last sample failed (an edit clears it).
 */
public data class NotificationDialogState(
    val draft: NotificationDraft,
    val invalid: InvalidField?,
    val saveError: NotificationSettingsError?,
    val voices: List<VoiceOption>? = null,
    val voicesFailed: Boolean = false,
    val previewing: Boolean = false,
    val previewFailed: Boolean = false,
)

/**
 * Logic of the "Ustawienia powiadomień" dialog (FR-N1, FR-N2, FR-N4 voice sample). Main thread; [save] writes in
 * [scope], one write at a time (`saving` is set by [save], cleared when the write ends or the dialog is opened or
 * cancelled), a result arriving after cancel is dropped. An edit made while a write runs is lost when the write ends
 * Ok, because the dialog closes. Logs nothing (the values are the user's).
 */
public class NotificationSettingsController(
    private val repo: NotificationSettingsRepo,
    private val voices: VoiceCatalog,
    private val preview: VoicePreview,
    private val scope: CoroutineScope,
) {
    private val dialog = MutableStateFlow<NotificationDialogState?>(null)

    /** The open dialog; null when closed. */
    public val state: StateFlow<NotificationDialogState?> = dialog

    private var generation = 0
    private var saving = false
    private var voiceList: List<VoiceOption>? = null
    private var loadingVoices = false
    private var previewGeneration = 0
    private var previewJob: Job? = null

    /** Opens the dialog with a draft of the saved settings; the first open also starts loading the voices. */
    public fun open() {
        stopPreview()
        generation++
        saving = false
        dialog.value = NotificationDialogState(draftOf(repo.settings.value), null, null, voiceList)
        loadVoices()
    }

    /** Changes the draft; clears the invalid field, the save error and the failed sample. */
    public fun edit(change: (NotificationDraft) -> NotificationDraft) {
        dialog.update { it?.copy(draft = change(it.draft), invalid = null, saveError = null, previewFailed = false) }
    }

    /** Saves the draft over the current settings; closes on success, else shows the invalid field or the error. */
    public fun save() {
        val draft = dialog.value?.draft?.takeUnless { saving } ?: return
        // Refusal depends only on the draft, so this check is the binding one; the update below cannot refuse.
        val refused = settingsOf(draft, repo.settings.value)
        if (refused is Outcome.Err) {
            dialog.update { it?.copy(invalid = refused.error) }
            return
        }
        saving = true
        val opened = generation
        scope.launch {
            // `?: current` is unreachable: the draft was accepted above.
            val result = repo.update { current -> (settingsOf(draft, current) as? Outcome.Ok)?.value ?: current }
            if (opened == generation) finish(result)
        }
    }

    /** Sets the template of [kind] in the draft to the default text; save then leaves it out. */
    public fun restoreTemplate(kind: NotificationKind) {
        val default = SpeechSettings.defaultTemplate(kind)
        edit { it.copy(speech = it.speech.copy(templates = it.speech.templates + (kind to default))) }
    }

    /**
     * Starts the sample of the drafted voice, or stops the one that plays. One sample at a time; open, cancel and
     * a successful save stop it; a result after a stop is dropped (generation).
     */
    public fun togglePreview() {
        val current = dialog.value ?: return
        if (previewJob != null) {
            stopPreview()
            dialog.update { it?.copy(previewing = false) }
            return
        }
        val sample = previewSample(current.draft.speech, repo.settings.value.speech, current.voices)
        val mine = ++previewGeneration
        dialog.update { it?.copy(previewing = true, previewFailed = false) }
        // Lazy: `previewJob` is set before the body can run, whatever the dispatcher.
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                val result = preview.play(sample)
                if (mine == previewGeneration) {
                    previewJob = null
                    dialog.update { it?.copy(previewing = false, previewFailed = result is Outcome.Err) }
                }
            }
        previewJob = job
        job.start()
    }

    private fun stopPreview() {
        previewGeneration++
        previewJob?.cancel()
        previewJob = null
    }

    /** Closes the dialog and drops the draft. */
    public fun cancel() {
        stopPreview()
        generation++
        saving = false
        dialog.value = null
    }

    /** One load at a time; a list that arrived is kept for good, a failure is tried again at the next open. */
    private fun loadVoices() {
        if (voiceList != null || loadingVoices) return
        loadingVoices = true
        scope.launch {
            val result =
                try {
                    voices.voices()
                } finally {
                    // Defensive: the catalog returns Outcome, but the flag must not stay set if it ever throws.
                    loadingVoices = false
                }
            when (result) {
                is Outcome.Ok -> {
                    voiceList = result.value
                    dialog.update { it?.copy(voices = result.value) }
                }

                is Outcome.Err -> {
                    dialog.update { it?.copy(voicesFailed = true) }
                }
            }
        }
    }

    private fun finish(result: Outcome<Unit, NotificationSettingsError>) {
        saving = false
        when (result) {
            is Outcome.Ok -> {
                stopPreview()
                dialog.value = null
            }

            is Outcome.Err -> {
                dialog.update { it?.copy(saveError = result.error) }
            }
        }
    }
}
