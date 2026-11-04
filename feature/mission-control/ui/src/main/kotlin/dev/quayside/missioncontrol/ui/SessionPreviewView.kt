package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.TextRole

/** The output preview ("peek") under the session list: title, "Zamknij" and the tail of the output (AM-411). */
@Composable
internal fun SessionPreviewView(
    preview: PreviewUi,
    onEvent: (MissionControlEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(MissionControlTags.PREVIEW)
                .background(color = panelColor())
                .padding(all = 8.dp),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AmText(MissionControlStrings.format(UiText("preview.title", listOf(preview.name))))
            CloseButton { onEvent(MissionControlEvent.TogglePreview) }
        }
        PreviewBody(preview.content)
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .testTag(MissionControlTags.PREVIEW_CLOSE)
                .focusProperties { canFocus = false }
                .clip(AmTheme.shape.corner)
                .clickable(role = Role.Button, onClick = onClick)
                .background(color = raisedColor())
                .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        AmText(MissionControlStrings["preview.close"])
    }
}

@Composable
private fun PreviewBody(content: PreviewContent) {
    when (content) {
        PreviewContent.Loading -> {
            AmText(MissionControlStrings["preview.loading"], color = mutedColor())
        }

        PreviewContent.Empty -> {
            AmText(MissionControlStrings["preview.empty"], color = mutedColor())
        }

        is PreviewContent.Failed -> {
            AmText(MissionControlStrings["preview.failed"], color = Tone.ERR.tint())
            AmText(MissionControlStrings.format(content.reason), color = Tone.ERR.tint())
        }

        is PreviewContent.Text -> {
            OutputText(content.text)
        }
    }
}

/** Monospace, selectable; scrolled to the end and kept there when the text grows (`reverseScrolling`). */
@Composable
private fun OutputText(text: String) {
    SelectionContainer {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag(MissionControlTags.PREVIEW_TEXT)
                    .verticalScroll(rememberScrollState(), reverseScrolling = true),
        ) {
            AmText(text = text, role = TextRole.CODE, maxLines = Int.MAX_VALUE)
        }
    }
}
