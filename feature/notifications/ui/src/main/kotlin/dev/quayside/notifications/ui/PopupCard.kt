package dev.quayside.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.PopupAction
import dev.quayside.notifications.api.PopupId
import dev.quayside.notifications.api.PopupModel
import dev.quayside.notifications.api.PopupStyle

/** One popup: a colour bar by kind, title, "project · session", the detail and the buttons. */
@Composable
internal fun PopupCard(
    popup: PopupModel,
    style: PopupStyle,
    onAction: (PopupId, PopupAction) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .width(PopupMetrics.CARD_WIDTH.dp)
                .height(PopupMetrics.cardHeight(popup, style).dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PopupColors.card)
                .testTag(PopupTags.card(popup.id)),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(PopupColors.barOf(popup.kind)))
        if (popup.collapsed) {
            CollapsedBody(popup, onAction)
        } else {
            FullBody(popup, style, onAction)
        }
    }
}

@Composable
private fun CollapsedBody(
    popup: PopupModel,
    onAction: (PopupId, PopupAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PopupText(popup.summary(), PopupColors.text, 12, Modifier.weight(1f))
        PopupButton(PopupStrings["popup.close"], PopupTags.dismiss(popup.id)) {
            onAction(popup.id, PopupAction.DISMISS)
        }
    }
}

@Composable
private fun FullBody(
    popup: PopupModel,
    style: PopupStyle,
    onAction: (PopupId, PopupAction) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PopupText(PopupStrings.title(popup.kind), PopupColors.text, 14, bold = true)
        PopupText(popup.summary(), PopupColors.muted, 12)
        if (style == PopupStyle.DETAILED) {
            PopupText(popup.detail.orEmpty(), PopupColors.text, 12, Modifier.weight(1f), maxLines = 2)
        }
        PopupButtons(popup, onAction)
    }
}

@Composable
private fun PopupButtons(
    popup: PopupModel,
    onAction: (PopupId, PopupAction) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (popup.session != null) {
            PopupButton(PopupStrings["popup.attach"], PopupTags.attach(popup.id)) {
                onAction(popup.id, PopupAction.ATTACH)
            }
            PopupButton(PopupStrings["popup.mute"], PopupTags.mute(popup.id)) {
                onAction(popup.id, PopupAction.MUTE)
            }
        }
        PopupButton(PopupStrings["popup.dismiss"], PopupTags.dismiss(popup.id)) {
            onAction(popup.id, PopupAction.DISMISS)
        }
    }
}

@Composable
private fun PopupButton(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    PopupText(label, PopupColors.accent, 12, Modifier.clickable(onClick = onClick).testTag(tag), bold = true)
}

@Composable
private fun PopupText(
    text: String,
    color: Color,
    size: Int,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.SemiBold else null),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "project · session" for the line under the title; just the title when both are unknown. */
internal fun PopupModel.summary(): String =
    listOfNotNull(project, sessionName).joinToString(" · ").ifEmpty { PopupStrings.title(kind) }

/** Colours of the cards (dark, like the rest of the app). */
internal object PopupColors {
    val card = Color(0xFF262A31)
    val text = Color(0xFFE6E8EB)
    val muted = Color(0xFF9AA0A8)
    val accent = Color(0xFF6CB2FF)

    /** The bar by kind: amber asks for input, orange for permission, red failed, green done, blue PR. */
    fun barOf(kind: NotificationKind): Color =
        when (kind) {
            NotificationKind.NEEDS_INPUT -> Color(0xFFF2B01E)
            NotificationKind.PERMISSION -> Color(0xFFF2711E)
            NotificationKind.FAILED -> Color(0xFFE5484D)
            NotificationKind.TURN_ENDED -> Color(0xFF3FB950)
            NotificationKind.PULL_REQUEST -> Color(0xFF4C8DFF)
            NotificationKind.RUNNING_LONG -> Color(0xFFA371F7)
            NotificationKind.TERMINAL_BELL -> Color(0xFF8B949E)
        }
}
