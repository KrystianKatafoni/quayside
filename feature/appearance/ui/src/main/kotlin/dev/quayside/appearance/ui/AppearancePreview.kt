package dev.quayside.appearance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.core.designsystem.AmFonts
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.toColor

private val PREVIEW_HEIGHT = 230.dp

/**
 * Lets Tab walk past the preview: [after] sits on the control just after it (the first terminal font). Entering the
 * preview's focus group with Tab is redirected there, and with Shift+Tab the move is cancelled, which leaves the focus
 * on the control before the preview. So the preview takes no focus and the keyboard is not stuck in front of it.
 */
internal class PreviewBypass {
    val after = FocusRequester()
}

/**
 * The slot of `app` drawn in the look of the draft (ADR 0022): its theme, shape, typography and list density, and a
 * density that fits a window of 1280 dp into the pane with the scale of the draft. Takes no click and no focus.
 */
@Composable
internal fun PreviewPane(
    draft: AppearanceDraft,
    catalog: ThemeCatalog,
    bypass: PreviewBypass,
    preview: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT)
            .clipToBounds()
            .testTag(AppearanceTags.PREVIEW),
    ) {
        val theme = catalog.theme(previewScheme(draft))
        val typography = catalog.typography(draft.settings.typography)
        if (theme != null && typography != null) {
            val density = previewDensity(base, maxWidth, draft.settings.uiScale)
            AmTheme(theme.colors, theme.shape, typography, draft.settings.density.toAmDensity()) {
                CompositionLocalProvider(LocalDensity provides density) { Skipped(bypass, preview) }
            }
        }
        Box(Modifier.fillMaxSize().eatPointer())
    }
}

/** The slot on the bg of the draft theme, in a focus group that Tab walks past. */
@Composable
private fun Skipped(
    bypass: PreviewBypass,
    preview: @Composable () -> Unit,
) {
    val bg =
        AmTheme.colors.surfaces.bg
            .toColor()
    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .skippedByFocus(bypass)
            .focusGroup(),
        content = { preview() },
    )
}

/** Tab entering the focus group goes to the control after it, Shift+Tab to the one before it. */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.skippedByFocus(bypass: PreviewBypass): Modifier =
    focusProperties {
        onEnter = {
            // Measured with Compose MP 1.12: a forward move lands on the requested control only without cancelling;
            // a backward move stays before the preview only when cancelled (PreviewTabOrderTest).
            if (requestedFocusDirection == FocusDirection.Previous) {
                cancelFocusChange()
            } else {
                bypass.after.requestFocus()
            }
        }
    }

/** A layer on top that consumes every pointer event, so nothing under it is clicked, hovered or scrolled. */
private fun Modifier.eatPointer(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent().changes.forEach { it.consume() }
        }
    }

/** Two lines of a terminal in the `termBg`, `termText`, font and size of the draft. */
@Composable
internal fun TerminalSample(
    draft: AppearanceDraft,
    catalog: ThemeCatalog,
) {
    val colors = catalog.theme(previewScheme(draft))?.colors?.terminal ?: return
    val font = draft.terminalFont ?: catalog.typography(draft.settings.typography)?.terminalFont
    val style =
        TextStyle(
            color = colors.termText.toColor(),
            fontFamily = font?.let(AmFonts::family) ?: FontFamily.Monospace,
            fontSize = draft.terminalSize.sp,
        )
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(AppearanceTags.TERMINAL_SAMPLE)
            .background(colors.termBg.toColor())
            .padding(all = 8.dp),
    ) {
        BasicText(AppearanceStrings["sample.line1"], style = style, maxLines = 1)
        BasicText(AppearanceStrings["sample.line2"], style = style, maxLines = 1)
    }
}
