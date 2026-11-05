package dev.quayside.appearance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmPanel
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.ButtonKind
import dev.quayside.core.designsystem.ButtonOptions
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.amScrim
import dev.quayside.core.designsystem.toColor

private val PANEL_MAX_WIDTH = 980.dp
private val LEFT_WIDTH = 360.dp

/**
 * The dialog; [preview] is the real screen composed by `app` (ADR 0022), drawn in the draft look. A focusable popup
 * over the whole window, so keys reach its controls and not the window behind it; Escape closes without saving. The
 * dialog itself keeps the applied look: only the preview shows the draft.
 */
@Composable
public fun AppearanceDialog(
    controller: AppearanceController,
    preview: @Composable () -> Unit,
) {
    val state by controller.state.collectAsState()
    val shown = state ?: return
    Popup(
        popupPositionProvider = WholeWindow,
        onDismissRequest = controller::close,
        properties = PopupProperties(focusable = true),
    ) {
        Box(Modifier.fillMaxSize().amScrim(), contentAlignment = Alignment.Center) {
            DialogPanel(shown, controller, preview)
        }
    }
}

@Composable
private fun DialogPanel(
    state: AppearanceDialogState,
    controller: AppearanceController,
    preview: @Composable () -> Unit,
) {
    val bypass = remember { PreviewBypass() }
    AmPanel(
        modifier =
            Modifier
                .fillMaxWidth(fraction = 0.95f)
                .widthIn(max = PANEL_MAX_WIDTH)
                .testTag(AppearanceTags.DIALOG)
                .onPreviewKeyEvent { event ->
                    val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                    if (escape) controller.close()
                    escape
                },
    ) {
        // Scrolls when the window is lower than the dialog; the lists inside have their own limit.
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(all = 16.dp),
            Arrangement.spacedBy(space = 10.dp),
        ) {
            AmText(AppearanceStrings["title"], role = TextRole.HEADING)
            Columns(state.draft, controller, bypass, preview)
            Footer(state, controller)
        }
    }
}

/** The settings on the left, the preview and the terminal settings on the right. */
@Composable
private fun Columns(
    draft: AppearanceDraft,
    controller: AppearanceController,
    bypass: PreviewBypass,
    preview: @Composable () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 16.dp)) {
        Column(Modifier.width(LEFT_WIDTH), Arrangement.spacedBy(space = 8.dp)) {
            LeftColumn(draft, controller)
        }
        Column(Modifier.weight(weight = 1f), Arrangement.spacedBy(space = 8.dp)) {
            RightColumn(draft, controller, bypass, preview)
        }
    }
}

/** Themes, typographies with the suggestion, scale and density. */
@Composable
private fun LeftColumn(
    draft: AppearanceDraft,
    controller: AppearanceController,
) {
    val onIntent = controller::onIntent
    Label("themes.label")
    ThemeList(draft, controller.catalog, onIntent)
    Label("typography.label")
    TypographyList(draft, controller.catalog, onIntent)
    Label("scale.label")
    ScaleChoice(draft, onIntent)
    Label("density.label")
    DensityChoice(draft, onIntent)
}

/** The preview, the terminal sample and the terminal font and size. */
@Composable
private fun RightColumn(
    draft: AppearanceDraft,
    controller: AppearanceController,
    bypass: PreviewBypass,
    preview: @Composable () -> Unit,
) {
    val onIntent = controller::onIntent
    Label("preview.label")
    PreviewPane(draft, controller.catalog, bypass, preview)
    TerminalSample(draft, controller.catalog)
    Label("terminalFont.label")
    TerminalFontList(draft, terminalFontChoices(controller.catalog), onIntent, bypass.after)
    Label("terminalSize.label")
    TerminalSizeStepper(draft.terminalSize, onIntent)
}

@Composable
private fun Label(key: String) {
    AmText(
        AppearanceStrings[key],
        role = TextRole.CAPTION,
        color =
            AmTheme.colors.content.muted
                .toColor(),
    )
}

/** The fixed message of a failed apply, and the Apply and Cancel buttons (Apply is off while a save runs). */
@Composable
private fun Footer(
    state: AppearanceDialogState,
    controller: AppearanceController,
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        state.error?.let {
            AmText(
                AppearanceStrings.of(it),
                Modifier.testTag(AppearanceTags.ERROR),
                color =
                    AmTheme.colors.status.err
                        .toColor(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            AmButton(
                AppearanceStrings["apply"],
                { controller.onIntent(AppearanceIntent.Apply) },
                Modifier.testTag(AppearanceTags.APPLY),
                ButtonOptions(kind = ButtonKind.PRIMARY, enabled = !state.busy),
            )
            AmButton(AppearanceStrings["cancel"], controller::close, Modifier.testTag(AppearanceTags.CANCEL))
        }
    }
}

/** Places the dialog layer over the whole window, whatever the anchor. */
private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}
