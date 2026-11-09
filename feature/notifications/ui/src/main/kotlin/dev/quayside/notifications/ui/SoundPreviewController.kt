package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.SoundId
import dev.quayside.notifications.api.SoundLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The sound part of the dialog that is not a draft. [sounds]: the installed sounds; null while loading or after a
 * failure, then [listFailed] tells which. [previewing]: the kind whose sound plays now. [previewFailed]: the last
 * preview failed (the next preview clears it).
 */
public data class SoundPanelState(
    val sounds: List<SoundId>? = null,
    val listFailed: Boolean = false,
    val previewing: NotificationKind? = null,
    val previewFailed: Boolean = false,
)

/**
 * Lists the system sounds and previews one in the settings dialog (FR-N1). Main thread. The list is loaded once
 * and kept; a failure is tried again at the next [load]. One preview at a time; a result after [stop] or after a
 * newer preview is dropped (generation). Logs nothing.
 */
public class SoundPreviewController(
    private val library: SoundLibrary,
    private val scope: CoroutineScope,
) {
    private val panel = MutableStateFlow(SoundPanelState())

    /** What the sound section shows. */
    public val state: StateFlow<SoundPanelState> = panel

    private var loading = false
    private var generation = 0
    private var job: Job? = null

    /** Starts loading the sound list unless it is loaded or loading. */
    public fun load() {
        if (panel.value.sounds != null || loading) return
        loading = true
        scope.launch {
            val result =
                try {
                    library.sounds()
                } finally {
                    loading = false
                }
            when (result) {
                is Outcome.Ok -> panel.update { it.copy(sounds = result.value, listFailed = false) }
                is Outcome.Err -> panel.update { it.copy(listFailed = true) }
            }
        }
    }

    /**
     * Plays [sound] of [kind] at [volume] percent, or stops the preview of [kind] when it plays. A preview of
     * another kind is replaced.
     */
    public fun toggle(
        kind: NotificationKind,
        sound: SoundId,
        volume: Int,
    ) {
        val wasPlaying = panel.value.previewing == kind
        stop()
        if (wasPlaying) return
        val mine = generation
        panel.update { it.copy(previewing = kind, previewFailed = false) }
        // Lazy: `job` is set before the body can run, whatever the dispatcher.
        val started =
            scope.launch(start = CoroutineStart.LAZY) {
                val result = library.play(sound, volume)
                if (mine == generation) {
                    job = null
                    panel.update { it.copy(previewing = null, previewFailed = result is Outcome.Err) }
                }
            }
        job = started
        started.start()
    }

    /** Stops the preview that plays, if any. */
    public fun stop() {
        generation++
        job?.cancel()
        job = null
        panel.update { it.copy(previewing = null) }
    }
}
