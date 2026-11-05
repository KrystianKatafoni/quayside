package dev.quayside.appearance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmDensity
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.listMetrics
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba

/** Cold JVM start-up can exceed the default 1 s of `waitUntil` in the first test. */
internal const val WAIT_MS = 5_000L

/** Tags of the sample the tests put into the preview slot (the slot is `app`'s in production). */
internal object SampleTags {
    const val TEXT = "sample.text"
    const val LINES = "sample.lines"
    const val CLICKABLE = "sample.clickable"
    const val FOCUSABLE = "sample.focusable"
}

/** What the preview slot of a test saw and received: the theme it was composed in, and the clicks that reached it. */
internal class SlotRecorder {
    var colors: ColorTokens? = null
    var density: AmDensity? = null
    var clicks: Int = 0
}

/**
 * The preview slot of the tests: a text, a line that says how many lines a list row would have, a clickable box and a
 * focusable box. It reads the theme the dialog composes it in, so the tests see the draft look.
 */
@Composable
internal fun SampleSlot(recorder: SlotRecorder) {
    val colors = AmTheme.colors
    val density = AmTheme.density
    SideEffect {
        recorder.colors = colors
        recorder.density = density
    }
    Column {
        AmText("Quayside agents waiting", Modifier.testTag(SampleTags.TEXT))
        AmText(if (density.listMetrics().twoLines) "two lines" else "one line", Modifier.testTag(SampleTags.LINES))
        Box(
            Modifier
                .testTag(SampleTags.CLICKABLE)
                .size(40.dp)
                .background(colors.surfaces.raised.toColor())
                .clickable { recorder.clicks++ },
        )
        Box(Modifier.testTag(SampleTags.FOCUSABLE).size(40.dp).focusable())
    }
}

/**
 * Shows the dialog over [world] in the plex theme, with [SampleSlot] as its preview, and opens it the way the palette
 * command does. Returns the controller.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.showDialog(
    world: DialogWorld,
    recorder: SlotRecorder = SlotRecorder(),
): AppearanceController {
    val built = mutableListOf<AppearanceController>()
    setContent {
        AmTestTheme {
            val scope = rememberCoroutineScope()
            val controller = remember { world.controller(scope).also(built::add) }
            AppearanceDialog(controller) { SampleSlot(recorder) }
        }
    }
    waitUntil(timeoutMillis = WAIT_MS) { built.isNotEmpty() }
    runOnIdle { built.single().open() }
    await(AppearanceTags.DIALOG)
    return built.single()
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
    waitUntilExactlyOneExists(hasTestTag(tag), WAIT_MS)
    return onNodeWithTag(tag)
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitGone(tag: String) = waitUntilDoesNotExist(hasTestTag(tag), WAIT_MS)

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitFocus(tag: String) {
    waitUntilExactlyOneExists(hasTestTag(tag) and isFocused(), WAIT_MS)
}

/** Every distinct ARGB value that occurs in the pixels of this node. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.argbColors(): Set<Int> {
    val map = captureToImage().toPixelMap()
    return (0 until map.width).flatMapTo(HashSet()) { x -> (0 until map.height).map { y -> map[x, y].toArgb() } }
}

/** ARGB of a token as Compose draws it. */
internal fun Rgba.argb(): Int = toColor().toArgb()
