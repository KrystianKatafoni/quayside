package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration.Companion.hours

/**
 * Mission Control over the scripted [sessions] of [project] (default [OVERVIEW_GROUP]) with [focus] selected (default:
 * the group itself), the way the sidebar would: the group page with its overview. Returns the controller.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.showGroup(
    sessions: List<Session>,
    handlers: MissionControlHandlers = MissionControlHandlers(),
    project: Project = OVERVIEW_GROUP,
    focus: ProjectFocus = ProjectFocus(project.id),
): MissionControlController {
    var shown: MissionControlController? = null
    setContent {
        AmTestTheme {
            val scope = rememberCoroutineScope()
            val controller =
                remember {
                    MissionControlController(
                        SessionsApi(ScriptedSessionMonitor(freshSnapshot(*sessions.toTypedArray())), FakeSessions()),
                        FakeProjectsApi(initial = listOf(project, OVERVIEW_NESTED)),
                        MutableStateFlow<ProjectFocus?>(focus),
                        TestClock(T0.plusSeconds(600)),
                        scope,
                        NoOpLogger,
                        tick = 1.hours,
                    )
                }
            shown = controller
            MissionControl(controller, handlers = handlers)
        }
    }
    return checkNotNull(shown) { "content was not composed" }
}

/** The node of the overview row of [name]. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.overviewRow(name: String): SemanticsNodeInteraction =
    awaitTag(MissionControlTags.overviewRow(SubfolderName(name)))

/**
 * The descriptions of the chips (`czeka: 2`, `pracuje: 1`, `gotowe: 3`, `błąd: 4`; AM-310) announced inside the node
 * tagged [tag], from left to right. A state at zero has no chip.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.counterTexts(tag: String): List<String> =
    onAllNodes(inTag(tag) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription), true)
        .fetchSemanticsNodes()
        .sortedBy { it.boundsInRoot.left }
        .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
        .filter { COUNTER_TEXT.matches(it) }

/** The counter node announced as [description] inside the node tagged [tag]. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.counterNode(
    tag: String,
    description: String,
): SemanticsNodeInteraction =
    onAllNodes(hasContentDescription(description) and inTag(tag), useUnmergedTree = true).onFirst()

/** "czeka: N", "pracuje: N", "gotowe: N" or "błąd: N". */
private val COUNTER_TEXT = Regex("(czeka|pracuje|gotowe|błąd): \\d+")

/** True when a node tagged [tag] exists. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.exists(tag: String): Boolean = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
