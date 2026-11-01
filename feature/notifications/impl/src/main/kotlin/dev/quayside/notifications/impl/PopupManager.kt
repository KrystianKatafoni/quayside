package dev.quayside.notifications.impl

import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.SessionId
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.PopupAction
import dev.quayside.notifications.api.PopupId
import dev.quayside.notifications.api.PopupModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * The popups on screen (FR-N3): adds them, removes timed ones after [TIMEOUT], handles the user's [PopupAction]s and
 * remembers which sessions are muted. Every change runs under [lock], so a timer, an action and a new popup never lose
 * each other's change. [settings] is read when a popup is added; the stack is re-arranged on every change.
 */
internal class PopupManager(
    private val settings: StateFlow<NotificationSettings>,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val onAttach: (SessionId) -> Unit,
) {
    private val lock = Any()
    private val state = MutableStateFlow<List<PopupModel>>(emptyList())
    private val timers = mutableMapOf<PopupId, Job>()
    private val muted = mutableMapOf<SessionId, Instant>()
    private var counter = 0

    val popups: StateFlow<List<PopupModel>> = state.asStateFlow()

    /** True while [session] is muted (FR-N3: "Wycisz sesję na 15 min"); an expired mute is forgotten here. */
    fun isMuted(session: SessionId?): Boolean =
        session != null &&
            synchronized(lock) {
                val until = muted[session] ?: return@synchronized false
                (clock.now() < until).also { if (!it) muted.remove(session) }
            }

    /** Adds the popup of [delivery] for [project] (its display name, null when unknown). */
    fun show(
        delivery: Delivery,
        project: String?,
    ) {
        val current = settings.value.popup
        synchronized(lock) {
            val session = delivery.session
            val model =
                PopupModel(
                    id = PopupId("p-${++counter}"),
                    kind = delivery.kind,
                    session = session?.id,
                    project = project,
                    sessionName = session?.name,
                    detail = PopupRules.detailOf(session?.state),
                    sticky = PopupRules.sticky(delivery.kind, current.duration),
                    collapsed = false,
                )
            val before = state.value
            val after = PopupRules.stack(model, before, current.maxVisible)
            release(before, after)
            state.value = after
            if (!model.sticky) timers[model.id] = scope.launch { expire(model.id) }
        }
    }

    /** Applies [action] to popup [id]; an unknown id is ignored. */
    fun act(
        id: PopupId,
        action: PopupAction,
    ) {
        val target = synchronized(lock) { state.value.firstOrNull { it.id == id } } ?: return
        when (action) {
            PopupAction.DISMISS -> {
                remove { it.id == id }
            }

            PopupAction.ATTACH -> {
                target.session?.let(onAttach)
                remove { it.id == id }
            }

            PopupAction.MUTE -> {
                mute(target.session, id)
            }
        }
    }

    private fun mute(
        session: SessionId?,
        id: PopupId,
    ) {
        if (session == null) {
            remove { it.id == id }
            return
        }
        synchronized(lock) { muted[session] = clock.now().plus(MUTE.toJavaDuration()) }
        remove { it.session == session }
    }

    private suspend fun expire(id: PopupId) {
        delay(TIMEOUT)
        remove { it.id == id }
    }

    private fun remove(matches: (PopupModel) -> Boolean) {
        synchronized(lock) {
            val before = state.value
            val after = PopupRules.arrange(before.filterNot(matches), settings.value.popup.maxVisible)
            release(before, after)
            state.value = after
        }
    }

    /** Cancels the timers of popups that are in [before] and not in [after]. */
    private fun release(
        before: List<PopupModel>,
        after: List<PopupModel>,
    ) {
        val kept = after.map { it.id }.toSet()
        before.filter { it.id !in kept }.forEach { timers.remove(it.id)?.cancel() }
    }

    internal companion object {
        /** How long a timed popup stays. */
        val TIMEOUT = 8.seconds

        /** How long "Wycisz" silences a session. */
        val MUTE = 15.minutes
    }
}
