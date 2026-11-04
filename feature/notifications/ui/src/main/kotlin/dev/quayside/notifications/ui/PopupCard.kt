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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmPanel
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.toColor
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
    AmPanel(
        modifier =
            Modifier
                .width(PopupMetrics.CARD_WIDTH.dp)
                .height(PopupMetrics.cardHeight(popup, style).dp)
                .testTag(PopupTags.card(popup.id)),
    ) {
        Row {
            Box(Modifier.width(4.dp).fillMaxHeight().background(popup.kind.barColor(AmTheme.colors).toColor()))
            if (popup.collapsed) CollapsedBody(popup, onAction) else FullBody(popup, style, onAction)
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
        AmText(popup.summary(), Modifier.weight(1f), TextRole.CAPTION)
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
        AmText(PopupStrings.title(popup.kind), role = TextRole.HEADING)
        AmText(
            popup.summary(),
            role = TextRole.CAPTION,
            color = mutedColor(),
        )
        if (style == PopupStyle.DETAILED) {
            AmText(popup.detail.orEmpty(), Modifier.weight(1f), TextRole.CAPTION, maxLines = 2)
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

/** A clickable semibold caption in the text colour; the popup window is not focusable, so it has no focus frame. */
@Composable
private fun PopupButton(
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    val style = AmTheme.typography.style(TextRole.CAPTION)
    BasicText(
        text = label,
        modifier = Modifier.clickable(onClick = onClick).testTag(tag),
        style =
            style.copy(
                color = textColor(),
                fontWeight = FontWeight.SemiBold,
            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "project · session" for the line under the title; just the title when both are unknown. */
internal fun PopupModel.summary(): String =
    listOfNotNull(project, sessionName).joinToString(" · ").ifEmpty { PopupStrings.title(kind) }
