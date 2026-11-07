package dev.quayside.terminal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Density
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path

private const val SCALE = 4
private const val WAIT_MS = 5_000L

/**
 * The frame of the active pane (AM-816): rounded with the card radius r + 4 in the accent colour, and the widget is
 * inset so that its square corners fit. Read from pixels at 4 px per dp; widgets are replaced by a marker.
 */
@OptIn(ExperimentalTestApi::class)
class PaneFrameLookTest {
    @TempDir
    lateinit var root: Path

    private val colors = SampleTokens.colors()
    private val plex = TypographySpec(TypographyId("plex"), "IBM Plex", "IBM Plex Sans", "IBM Plex Mono")
    private val api = ScriptedTerminalApi()

    private fun ComposeUiTest.showPane(radius: Int) {
        val dir = Files.createDirectory(root.resolve("Alpha"))
        val built = mutableListOf<TerminalController>()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(SCALE.toFloat())) {
                AmTheme(colors, SampleTokens.shape(radius), plex) {
                    val scope = rememberCoroutineScope()
                    val controller =
                        remember {
                            TerminalController(api, scope, RecordingLogger(), FixedShellStart()).also { built += it }
                        }
                    TerminalArea(controller, onAppKey = { false }, paneContent = {
                        Box(Modifier.fillMaxSize().testTag(CONTENT))
                    })
                }
            }
        }
        waitUntil(timeoutMillis = WAIT_MS) { built.isNotEmpty() }
        runOnIdle { built.single().openShell(project("alpha", dir, "Alpha")) }
        waitUntilExactlyOneExists(hasTestTag(CONTENT), WAIT_MS)
        waitForIdle()
    }

    private fun paneTag(): String = TerminalTags.pane(pane(1))

    @ParameterizedTest(name = "token radius {0} dp, card radius {1} dp")
    @CsvSource("8,12", "16,20")
    fun `active pane frame is rounded accent`(
        radius: Int,
        card: Int,
    ) = runComposeUiTest {
        showPane(radius)
        val container =
            colors.terminal.termBg
                .toColor()
                .toArgb()

        val pixels = onNodeWithTag(paneTag()).captureToImage().toPixelMap()

        // the very corner shows the area behind the pane, the frame in the middle of the top edge is the accent
        pixels[0, 0].toArgb() shouldBe container
        val top = (0 until pixels.height).map { pixels[pixels.width / 2, it].toArgb() }.first { it != container }
        top shouldBe
            colors.content.accent
                .toColor()
                .toArgb()
        // the corner begins 0.29 of the radius in along the diagonal
        val onset = (0 until minOf(pixels.width, pixels.height)).first { pixels[it, it].toArgb() != container }
        onset.shouldBeInRange((card * SCALE * 0.29).toInt() - 1..(card * SCALE * 0.29).toInt() + 1)
    }

    @Test
    fun `active pane frame of a square theme has square corners`() =
        runComposeUiTest {
            showPane(radius = 0)

            val pixels = onNodeWithTag(paneTag()).captureToImage().toPixelMap()

            pixels[0, 0].toArgb() shouldBe
                colors.content.accent
                    .toColor()
                    .toArgb()
        }

    @ParameterizedTest(name = "token radius {0} dp inset {1} dp")
    @CsvSource("0,2", "8,4", "16,6")
    fun `widget is inset from the frame by the pane inset`(
        radius: Int,
        inset: Int,
    ) = runComposeUiTest {
        showPane(radius)

        val pane = onNodeWithTag(paneTag()).fetchSemanticsNode().boundsInRoot
        val content = onNodeWithTag(CONTENT).fetchSemanticsNode().boundsInRoot

        (content.left - pane.left) shouldBeGreaterThanOrEqualTo (inset * SCALE).toFloat()
        (content.top - pane.top) shouldBeGreaterThanOrEqualTo (inset * SCALE).toFloat()
        (pane.right - content.right) shouldBeGreaterThanOrEqualTo (inset * SCALE).toFloat()
        (pane.bottom - content.bottom) shouldBeGreaterThanOrEqualTo (inset * SCALE).toFloat()
    }

    private companion object {
        const val CONTENT = "pane.content"
    }
}
