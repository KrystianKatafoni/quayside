@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.domain.NoOpLogger
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/**
 * AM-819 on the lesson of AM-803 (ADR 0012): a `SwingPanel` eats the mouse over its area, so the handle of
 * [TerminalSplit] must lie outside of it. A real AWT window is the only place where this shows; skipped without
 * a display. Hit testing of AWT does not need the window focus.
 */
@Tag("awt-window")
class SplitOverSwingWindowTest {
    @TempDir
    lateinit var root: java.nio.file.Path

    private val area = JTextArea("terminal")

    // Position of the panel in the window in dp (= AWT points), written by onGloballyPositioned.
    @Volatile private var panelTop = 0f

    @Volatile private var missionBottom = 0f

    @Volatile private var panelLeft = 0f

    @Volatile private var panelWidth = 0f

    private fun onEdt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)

    private fun waitFor(condition: () -> Boolean) {
        repeat(times = 100) { if (!condition()) Thread.sleep(50) }
    }

    private fun isSkia(component: Component?): Boolean = component?.javaClass?.name?.contains("Skia") == true

    private fun trackPanel(
        coordinates: LayoutCoordinates,
        dp: Float,
    ) {
        val bounds = coordinates.boundsInWindow()
        panelTop = bounds.top / dp
        panelLeft = bounds.left / dp
        panelWidth = bounds.width / dp
    }

    @Composable
    private fun Content() {
        val dp = LocalDensity.current.density
        val pane =
            remember {
                val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
                TerminalPaneHeight(DEFAULT_TERMINAL_HEIGHT, layoutStoreOn(root), scope, NoOpLogger)
            }
        TerminalSplit(
            missionControl = { modifier ->
                Box(
                    modifier.fillMaxSize().onGloballyPositioned { missionBottom = it.boundsInWindow().bottom / dp },
                )
            },
            panel = { modifier ->
                SwingPanel(
                    factory = { area },
                    modifier =
                        modifier
                            .height(
                                DEFAULT_TERMINAL_HEIGHT,
                            ).fillMaxWidth()
                            .onGloballyPositioned { trackPanel(it, dp) },
                )
            },
            pane = pane,
            modifier = Modifier.fillMaxSize(),
        )
    }

    private fun showWindow(): ComposeWindow {
        lateinit var window: ComposeWindow
        onEdt {
            window = ComposeWindow()
            window.setSize(800, 600)
            window.setContent { AmTestTheme { Content() } }
            window.isVisible = true
        }
        waitFor { area.isShowing && panelTop > 0f }
        return window
    }

    private fun deepestAt(
        window: ComposeWindow,
        x: Int,
        y: Int,
    ): Component? {
        var hit: Component? = null
        onEdt { hit = SwingUtilities.getDeepestComponentAt(window.layeredPane, x, y) }
        return hit
    }

    /** The Skia canvas, found below the children first: the root of the Skia hierarchy has no mouse listeners. */
    private fun findCanvas(root: Component): Component? =
        (root as? Container)?.components?.firstNotNullOfOrNull { findCanvas(it) } ?: root.takeIf { isSkia(it) }

    private fun withWindow(body: (ComposeWindow) -> Unit) {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val window = showWindow()
        try {
            body(window)
        } finally {
            onEdt { window.dispose() }
        }
    }

    @Test
    fun `handle point hits the Compose canvas not the Swing widget`() =
        withWindow { window ->
            // Given the split with a Swing widget in the panel, the handle is the gap between Mission Control and it
            val x = (panelLeft + panelWidth / 2).toInt()
            val handleY = ((missionBottom + panelTop) / 2).toInt()

            // Then the middle of the handle is hit by the Compose canvas, not by the widget
            val hit = deepestAt(window, x, handleY)
            (hit === area) shouldBe false
            isSkia(hit) shouldBe true

            // And the probe works: a point inside the panel is hit by the widget
            (deepestAt(window, x, (panelTop + PROBE_INSIDE_DP).toInt()) === area) shouldBe true

            // And the canvas the drag events go to exists
            (findCanvas(window.contentPane) != null) shouldBe true
        }

    private companion object {
        const val PROBE_INSIDE_DP = 20f
    }
}
