package dev.quayside.projects.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Where the vertical guide runs: under the folder icon of the group row. */
internal val GUIDE_X = 43.dp

/** Where the content of a subfolder row starts: right of the end of the horizontal guide. */
internal val SUBFOLDER_INDENT = 58.dp

private val GUIDE_WIDTH = 1.dp
private val GUIDE_END_X = 54.dp

/** The gap the list leaves between two rows; the vertical guide reaches over it to join the row above. */
private val ROW_GAP = 2.dp

/**
 * The branch of a folder tree: a vertical line from the group row down along the subfolder rows and a short
 * horizontal line into the row. The [last] subfolder ends the vertical line at its own middle (an "L"), the others
 * continue it down (a "T"). Decorative: no semantics, no size.
 */
internal fun Modifier.treeGuide(
    last: Boolean,
    color: Color,
): Modifier =
    drawBehind {
        val x = GUIDE_X.toPx()
        val middle = size.height / 2
        val width = GUIDE_WIDTH.toPx()
        drawLine(color, Offset(x, -ROW_GAP.toPx()), Offset(x, if (last) middle else size.height), width)
        drawLine(color, Offset(x, middle), Offset(GUIDE_END_X.toPx(), middle), width)
    }
