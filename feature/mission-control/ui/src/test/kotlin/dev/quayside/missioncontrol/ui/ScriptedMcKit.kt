package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration.Companion.hours

/** Second project of the AM-816 screen tests (the first is [MC_PROJECT]). */
internal val MC_OTHER = aProject(id = "project0002", dir = Path.of("/Users/test/projects/other"), displayName = "Other")

/** A fresh snapshot of [sessions] in start order. */
internal fun freshSnapshot(vararg sessions: Session): SessionsSnapshot =
    SessionsSnapshot(sessions.sortedBy { it.startedAt }, SnapshotStatus.Fresh(T0))

/** Lays out [content] at 4 pixels per dp, so pixel tests can read thin strokes and arcs. */
@Composable
internal fun Scaled4(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(SCALE.toFloat()), content = content)
}

/** Pixels per dp in [Scaled4]. */
internal const val SCALE = 4

/**
 * Shows [content] over the scripted [snapshot] and the projects [MC_PROJECT] (selected) and [MC_OTHER] in the plex
 * theme; the controller lives in the composition scope with a one-hour tick. [scaled] draws at 4 px per dp.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.showScripted(
    snapshot: SessionsSnapshot,
    now: Instant = T0.plusSeconds(600),
    scaled: Boolean = false,
    content: @Composable (MissionControlController) -> Unit = { MissionControl(it) },
): MissionControlController {
    var shown: MissionControlController? = null
    setContent {
        AmTestTheme {
            val scope = rememberCoroutineScope()
            val controller =
                remember {
                    MissionControlController(
                        SessionsApi(ScriptedSessionMonitor(snapshot), FakeSessions()),
                        FakeProjectsApi(initial = listOf(MC_PROJECT, MC_OTHER)),
                        MutableStateFlow<ProjectFocus?>(ProjectFocus(MC_PROJECT.id)),
                        TestClock(now),
                        scope,
                        NoOpLogger,
                        tick = 1.hours,
                    )
                }
            shown = controller
            if (scaled) Scaled4 { content(controller) } else content(controller)
        }
    }
    return checkNotNull(shown) { "content was not composed" }
}

/** The text style a text node was laid out with (role, family, size, weight, letter spacing). */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.textStyle(): TextStyle {
    val results = mutableListOf<TextLayoutResult>()
    val action = fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
    checkNotNull(action) { "node has no text layout" }.invoke(results)
    return results.first().layoutInput.style
}

/** Opaque ARGB of a token as [PixelMap] reports it. */
internal fun Rgba.argb(): Int = toColor().toArgb()

/** Pixels of the node. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.pixelMap(): PixelMap = captureToImage().toPixelMap()

/** Index of the first pixel on the diagonal from the top left corner whose colour is not [container]; null if none. */
internal fun PixelMap.diagonalOnset(container: Int): Int? =
    (0 until minOf(width, height)).firstOrNull { this[it, it].toArgb() != container }

/** Colour of the first pixel going down the middle column from the top that is not [container]; null if none. */
internal fun PixelMap.topEdgeColor(container: Int): Int? =
    (0 until height).map { this[width / 2, it].toArgb() }.firstOrNull { it != container }

/** Matches the nodes of the subtree tagged [tag], the tagged node included. */
internal fun inTag(tag: String) = hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))

/** ARGB of the pixel at ([x], [y]). */
internal fun PixelMap.argbAt(
    x: Int,
    y: Int,
): Int = this[x, y].toArgb()
