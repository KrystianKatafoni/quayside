package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import kotlin.time.Duration.Companion.hours

/** The one project of the AM-809 screen tests; its sessions are those of [worldSession]. */
internal val MC_PROJECT = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")

/** A session of [MC_PROJECT] started at T0 + [index] seconds. */
internal fun worldSession(
    id: String,
    state: SessionState = SessionState.Working,
    name: String = "name-$id",
    index: Long = 0,
): WorldSession =
    WorldSession(SessionId(id), A_PROJECT_DIR, state = state, name = name, startedAt = T0.plusSeconds(index))

/** [FakeSessions] with [sessions] in [MC_PROJECT]. */
internal fun fakeSessions(vararg sessions: WorldSession): FakeSessions =
    FakeSessions(
        SessionsWorld(projects = listOf(WorldProject(MC_PROJECT.id, MC_PROJECT.dir)), sessions = sessions.toList()),
        TestClock(T0),
    )

/**
 * Composes [content] (Mission Control by default) over [sessions] and [MC_PROJECT] in the plex theme, with the
 * controller in the composition scope and a one-hour tick. Returns the controller. The clock stands at [now].
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.showMc(
    sessions: FakeSessions,
    handlers: MissionControlHandlers = MissionControlHandlers(),
    now: Instant = T0.plusSeconds(240),
    content: @Composable (MissionControlController) -> Unit = { MissionControl(it, handlers = handlers) },
): MissionControlController {
    var shown: MissionControlController? = null
    setContent {
        AmTestTheme {
            val scope = rememberCoroutineScope()
            val controller =
                remember {
                    MissionControlController(
                        SessionsApi(monitor = sessions, commands = sessions),
                        FakeProjectsApi(initial = listOf(MC_PROJECT)),
                        MutableStateFlow(MC_PROJECT.id),
                        TestClock(now),
                        scope,
                        NoOpLogger,
                        tick = 1.hours,
                    )
                }
            shown = controller
            content(controller)
        }
    }
    return checkNotNull(shown) { "content was not composed" }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitTag(tag: String): SemanticsNodeInteraction {
    waitUntilExactlyOneExists(hasTestTag(tag))
    return onNodeWithTag(tag)
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.hasTag(tag: String): Boolean = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

/** A text inside the node tagged [tag], or on the node itself when it merges its children. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.textUnder(
    tag: String,
    text: String,
): SemanticsNodeInteraction =
    onAllNodes(hasText(text, substring = true) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)))).onFirst()

internal fun mcText(
    key: String,
    vararg args: String,
): String = MissionControlStrings.format(UiText(key, args.toList()))

/** Selects the session [id] of [MC_PROJECT] through the public intent, as `app` does after a start. */
internal fun MissionControlController.pickSession(id: String) =
    onIntent(MissionControlIntent.SelectSession(MC_PROJECT.id, SessionId(id)))

/** Every distinct ARGB value that occurs in the pixels of this node. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.argbColors(): Set<Int> {
    val map = captureToImage().toPixelMap()
    return (0 until map.width).flatMapTo(HashSet()) { x -> (0 until map.height).map { y -> map[x, y].toArgb() } }
}
