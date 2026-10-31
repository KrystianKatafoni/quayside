package dev.agentmanager.missioncontrol.ui

import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.sessions.api.SessionMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** How often the open preview reads the output again; `lastActivityAt` changes only with the state (plan, 3). */
private val PREVIEW_REFRESH = 3.seconds

/**
 * The output preview of the selected session (AM-411): whether it is open and what it shows. Collects in [scope];
 * one collector follows the key of [SessionPreview], so a changed selection cancels the read of the old session
 * and a lost target closes the preview. Logs nothing: the output is user data (ENG-C8).
 */
internal class PreviewState(
    private val monitor: SessionMonitor,
    private val selection: StateFlow<Selection>,
    scope: CoroutineScope,
) {
    private val openFlow = MutableStateFlow(false)
    private val shownFlow = MutableStateFlow<PreviewUi?>(null)

    /** The preview is open. */
    val open: StateFlow<Boolean> = openFlow.asStateFlow()

    /** The panel to show, null when closed. */
    val shown: StateFlow<PreviewUi?> = shownFlow.asStateFlow()

    init {
        scope.launch {
            combine(openFlow, selection, monitor.snapshot) { isOpen, picked, snapshot ->
                SessionPreview.key(isOpen, snapshot.sessions, picked)
            }.distinctUntilChanged().collectLatest { id ->
                if (id == null) close() else follow(id)
            }
        }
    }

    /** Closes an open preview; opens the one of the selected visible session, read now, if there is one. */
    fun toggle() {
        if (openFlow.value) {
            openFlow.value = false
        } else if (SessionActions.target(monitor.snapshot.value.sessions, selection.value) != null) {
            openFlow.value = true
        }
    }

    /**
     * Closes after the target is lost (the key became null) and clears [shown]. [toggle] only flips [open], so a quick
     * close and open in one step keeps the key and the panel.
     */
    private fun close() {
        openFlow.value = false
        shownFlow.value = null
    }

    /** Loading, then a read every [PREVIEW_REFRESH]; the old text stays while a read runs, a failure replaces it. */
    private suspend fun follow(id: SessionId) {
        val name =
            monitor.snapshot.value.sessions
                .firstOrNull { it.id == id }
                ?.name ?: return close()
        shownFlow.value = PreviewUi(id, name, PreviewContent.Loading)
        while (true) {
            val content = SessionPreview.content(monitor.recentOutput(id))
            shownFlow.value = PreviewUi(id, name, content)
            delay(PREVIEW_REFRESH)
        }
    }
}
