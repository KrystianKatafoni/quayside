package dev.quayside.terminal.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.LocalAmModalGate
import dev.quayside.core.designsystem.toColor
import javax.swing.JComponent
import kotlin.math.ceil
import kotlin.math.sqrt
import java.awt.Color as AwtColor

/** What the panes of one frame need: the tabs by id, the active pane and how a widget is embedded. */
private class PaneEnv(
    val tabs: Map<TabId, TerminalTab>,
    val active: PaneId?,
    val search: TerminalSearchController,
    val content: @Composable (JComponent) -> Unit,
)

/** Embeds [component] in the composition; the widget keeps its state while it is moved between panes. */
@Composable
internal fun EmbeddedWidget(component: JComponent) {
    val background = termBgColor().toArgb()
    val gate = LocalAmModalGate.current
    // The overload with `background` is deprecated: the factory sets it, so no white shows while the panel resizes.
    SwingPanel(
        factory = {
            component.background = AwtColor(background)
            ModalShield(component) { gate.active }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * The layout of [state] filled with the widgets drawn by [content]. Maximized: only the active pane is composed
 * (the widgets of the other panes stay alive in their sessions).
 */
@Composable
internal fun TerminalPanes(
    state: TerminalUiState,
    search: TerminalSearchController,
    content: @Composable (JComponent) -> Unit,
) {
    val layout = state.layout ?: return
    val shown = if (state.maximized) layout.leaves().firstOrNull { it.pane == state.active } ?: layout else layout
    PaneNode(shown, PaneEnv(state.tabs.associateBy { it.id }, state.active, search, content), Modifier.fillMaxSize())
}

@Composable
private fun PaneNode(
    node: PaneTree,
    env: PaneEnv,
    modifier: Modifier,
) {
    when (node) {
        is PaneTree.Leaf -> PaneFrame(node, env, modifier)
        is PaneTree.Split -> SplitPanes(node, env, modifier)
    }
}

@Composable
private fun SplitPanes(
    split: PaneTree.Split,
    env: PaneEnv,
    modifier: Modifier,
) {
    when (split.axis) {
        SplitAxis.SIDE_BY_SIDE -> {
            Row(modifier) {
                PaneNode(split.first, env, Modifier.weight(weight = 1f))
                PaneNode(split.second, env, Modifier.weight(weight = 1f))
            }
        }

        SplitAxis.STACKED -> {
            Column(modifier) {
                PaneNode(split.first, env, Modifier.weight(weight = 1f))
                PaneNode(split.second, env, Modifier.weight(weight = 1f))
            }
        }
    }
}

/**
 * A pane: the widget of its tab (and the search bar, if open for it) inside a frame with the card radius (r+4),
 * accent-colored when active. The widget is inset by [paneInset]: a Swing widget cannot be clipped to the rounding.
 */
@Composable
private fun PaneFrame(
    leaf: PaneTree.Leaf,
    env: PaneEnv,
    modifier: Modifier,
) {
    val active = leaf.pane == env.active
    val colors = AmTheme.colors
    val shape = AmTheme.shape
    val color = (if (active) colors.content.accent else colors.surfaces.border).toColor()
    Box(
        modifier
            .testTag(TerminalTags.pane(leaf.pane))
            .semantics { selected = active }
            .border(width = 2.dp, color = color, shape = shape.card)
            .padding(all = paneInset(shape.radii.card)),
    ) {
        env.tabs[leaf.tab]?.let { tab ->
            key(tab.id) { env.content(tab.session.component()) }
            SearchBarHost(tab, env.search)
        }
    }
}

/**
 * Space between the frame of a pane and its Swing widget so that the square corners of the widget fit inside a frame
 * rounded with [radius]: at least `radius * (1 - 1 / sqrt(2))` and at least 2 dp.
 */
internal fun paneInset(radius: Dp): Dp = maxOf(MIN_INSET, ceil(radius.value * (1 - 1 / sqrt(2f))).dp)

private val MIN_INSET = 2.dp
