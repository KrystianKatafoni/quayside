package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.MessageCursor
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionMessage
import dev.quayside.sessions.api.SessionMessagePage
import dev.quayside.sessions.api.SessionMonitor
import dev.quayside.sessions.api.SessionOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The history of the selected disk session (AM-315, ADR 0034): the messages read so far, oldest first.
 *
 * @property messages the pages read so far; user data, never logged.
 * @property older cursor of the page before the first message; null = the start of the file (or nothing read yet).
 * @property loading a page is being read.
 * @property failure why the last read failed (a fixed text); the messages read before stay.
 */
internal data class ConversationUi(
    val id: SessionId,
    val messages: List<SessionMessage> = emptyList(),
    val older: MessageCursor? = null,
    val loading: Boolean = true,
    val failure: UiText? = null,
)

/**
 * Reads the newest page when a disk session is selected (once per selection, not refreshed: the file does not change
 * while the session is stopped) and older pages on [loadOlder]. Collects in [scope] (the window scope, not
 * thread-safe); a result that belongs to an earlier selection is dropped, also one of a read that ignores the cancel
 * (the first page checks that its job is still active, an older page compares a counter of selections). Logs nothing
 * (ENG-C8).
 */
internal class ConversationState(
    private val monitor: SessionMonitor,
    selection: StateFlow<Selection>,
    projects: StateFlow<List<Project>>,
    private val scope: CoroutineScope,
) {
    private val shownFlow = MutableStateFlow<ConversationUi?>(null)
    private var selections = 0

    /** The history of the selected disk session; null for any other selection. */
    val shown: StateFlow<ConversationUi?> = shownFlow.asStateFlow()

    init {
        scope.launch {
            combine(selection, monitor.snapshot, projects) { picked, snapshot, list ->
                SessionActions
                    .target(snapshot.sessions, picked, list)
                    ?.takeIf { it.origin == SessionOrigin.DISK }
                    ?.id
            }.distinctUntilChanged().collectLatest(::readNewest)
        }
    }

    /** Reads the page before the oldest shown message; ignored while a page is read and at the start of the file. */
    fun loadOlder() {
        val current = shownFlow.value ?: return
        val cursor = current.older
        if (current.loading || cursor == null) return
        shownFlow.value = current.copy(loading = true, failure = null)
        val mine = selections
        scope.launch {
            val outcome = monitor.conversation(current.id, cursor)
            if (mine == selections) shownFlow.update { it?.withOlder(outcome) }
        }
    }

    private suspend fun readNewest(id: SessionId?) {
        selections++
        shownFlow.value = id?.let { ConversationUi(it) }
        if (id != null) {
            val outcome = monitor.conversation(id, null)
            // collectLatest cancels this body and waits for it before the next selection starts, so `selections`
            // still equals the old value here; a read that ignores the cancel is told apart by the job itself.
            if (currentCoroutineContext().isActive) shownFlow.value = ConversationUi(id).withNewest(outcome)
        }
    }

    private fun ConversationUi.withNewest(outcome: Outcome<SessionMessagePage, SessionError>): ConversationUi =
        when (outcome) {
            is Outcome.Ok -> copy(messages = outcome.value.messages, older = outcome.value.older, loading = false)
            is Outcome.Err -> copy(loading = false, failure = SessionActionMessages.reason(outcome.error))
        }

    private fun ConversationUi.withOlder(outcome: Outcome<SessionMessagePage, SessionError>): ConversationUi =
        when (outcome) {
            is Outcome.Ok -> {
                copy(messages = outcome.value.messages + messages, older = outcome.value.older, loading = false)
            }

            is Outcome.Err -> {
                copy(loading = false, failure = SessionActionMessages.reason(outcome.error))
            }
        }
}
