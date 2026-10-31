package dev.agentmanager.missioncontrol.ui

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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                .background(color = Colors.panel)
                .padding(all = 8.dp),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Label(MissionControlStrings.format(UiText("preview.title", listOf(preview.name))))
            Box(
                modifier =
                    Modifier
                        .testTag(MissionControlTags.PREVIEW_CLOSE)
                        .focusProperties { canFocus = false }
                        .clickable(role = Role.Button) { onEvent(MissionControlEvent.TogglePreview) }
                        .background(color = Colors.button)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Label(MissionControlStrings["preview.close"])
            }
        }
        PreviewBody(preview.content)
    }
}

@Composable
private fun PreviewBody(content: PreviewContent) {
    when (content) {
        PreviewContent.Loading -> {
            Label(MissionControlStrings["preview.loading"], Colors.secondary)
        }

        PreviewContent.Empty -> {
            Label(MissionControlStrings["preview.empty"], Colors.secondary)
        }

        is PreviewContent.Failed -> {
            Label(MissionControlStrings["preview.failed"], Colors.of(Tone.ERR))
            Label(MissionControlStrings.format(content.reason), Colors.of(Tone.ERR))
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
            BasicText(
                text = text,
                style = TextStyle(color = Colors.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
            )
        }
    }
}
