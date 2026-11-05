package dev.quayside.projects.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmTestTheme

/** Composes [content] over the fakes of [world] in the plex theme and returns the controller. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.showProjects(
    world: ControllerWorld,
    content: @Composable (ProjectsController) -> Unit,
): ProjectsController {
    var shown: ProjectsController? = null
    setContent {
        AmTestTheme {
            val scope = rememberCoroutineScope()
            val controller = remember { world.controller(scope) }
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

/** Every distinct ARGB value that occurs in the pixels of this node. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.argbColors(): Set<Int> {
    val map = captureToImage().toPixelMap()
    return (0 until map.width).flatMapTo(HashSet()) { x -> (0 until map.height).map { y -> map[x, y].toArgb() } }
}
