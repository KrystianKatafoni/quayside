package dev.quayside.missioncontrol.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Where the vertical guide of the overview runs, from the left edge of the card. */
internal val OVERVIEW_GUIDE_X = 18.dp

/** Where the content of an overview row starts: right of the end of the horizontal guide. */
internal val OVERVIEW_INDENT = 38.dp

private val GUIDE_WIDTH = 1.dp
private val GUIDE_END_X = 32.dp

/**
 * The branch of a folder tree in the card of the subfolders, like the one in the sidebar: a vertical line down along
 * the rows and a short horizontal line into each row. The [last] row ends the vertical line at its middle (an "L"),
 * the others continue it (a "T"). The rows touch each other, so the line is continuous. Decorative: no semantics.
 */
internal fun Modifier.branchGuide(
    last: Boolean,
    color: Color,
): Modifier =
    drawBehind {
        val x = OVERVIEW_GUIDE_X.toPx()
        val middle = size.height / 2
        val width = GUIDE_WIDTH.toPx()
        drawLine(color, Offset(x, 0f), Offset(x, if (last) middle else size.height), width)
        drawLine(color, Offset(x, middle), Offset(GUIDE_END_X.toPx(), middle), width)
    }
