package dev.quayside.appearance.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.appearance.api.AppearanceError
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.FakeAppearanceApi
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.UiScale
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.terminal.api.TerminalSettings
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Scenarios of the Appearance dialog (FR-A3, FR-A4, NFR-6, plan AM-803) on fakes. The preview slot is the sample of
 * [SampleSlot]. The dialog is a focusable popup, so keys reach its controls and not the window behind it.
 */
@OptIn(ExperimentalTestApi::class)
class AppearanceDialogScreenTest {
    private val showcase = FakeAppearanceApi.showcaseCatalog()

    private fun inGroup(group: String): SemanticsMatcher = hasAnyAncestor(hasTestTag(group))

    @Test
    fun `theme rows are one selectable group`() =
        runComposeUiTest {
            showDialog(DialogWorld())

            // The list is a selectable group; its rows are selectable, one of them selected
            onNodeWithTag(AppearanceTags.THEMES)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            onAllNodes(isSelectable() and inGroup(AppearanceTags.THEMES)).fetchSemanticsNodes() shouldHaveSize 3
            onAllNodes(isSelected() and inGroup(AppearanceTags.THEMES)).fetchSemanticsNodes() shouldHaveSize 1
            onNodeWithTag(AppearanceTags.theme(PAPER)).assert(isSelected())
        }

    @Test
    fun `typography rows are one selectable group`() =
        runComposeUiTest {
            showDialog(DialogWorld())

            onNodeWithTag(AppearanceTags.TYPOGRAPHIES)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            onAllNodes(isSelectable() and inGroup(AppearanceTags.TYPOGRAPHIES)).fetchSemanticsNodes() shouldHaveSize 3
            onNodeWithTag(AppearanceTags.typography(GEIST)).assert(isSelected())
        }

    @Test
    fun `selected theme row is focused at start`() =
        runComposeUiTest {
            showDialog(DialogWorld())

            awaitFocus(AppearanceTags.theme(PAPER))
        }

    @Test
    fun `apply button is not focused at start`() =
        runComposeUiTest {
            showDialog(DialogWorld())
            awaitFocus(AppearanceTags.theme(PAPER))

            onNodeWithTag(AppearanceTags.APPLY).assertIsNotFocused()
            onNodeWithTag(AppearanceTags.CANCEL).assertIsNotFocused()
        }

    @Test
    fun `clicking a theme recolors only the preview`() =
        runComposeUiTest {
            // Given the dialog over saved settings with the theme paper, drawn in the plex theme
            val world = DialogWorld(appearanceStart = AppearanceSettings())
            val recorder = SlotRecorder()
            showDialog(world, recorder)
            val plex = showcase.theme(PLEX).shouldNotBeNull().colors
            val sunset = showcase.theme(SUNSET).shouldNotBeNull().colors
            awaitSlot(recorder) { it == plex }

            // When the user clicks the theme sunset
            await(AppearanceTags.theme(SUNSET)).performClick()
            awaitSlot(recorder) { it == sunset }

            // Then the preview is drawn in sunset, the dialog itself stays plex, and nothing is applied
            val accents = onNodeWithTag(AppearanceTags.APPLY).argbColors()
            accents shouldNotBe emptySet<Int>()
            (plex.content.accent.argb() in accents) shouldBe true
            (sunset.content.accent.argb() in accents) shouldBe false
            world.fake.settings.value shouldBe AppearanceSettings()
            world.fake.updates shouldBe 0
        }

    @Test
    fun `scale L enlarges the preview text and S shrinks it`() =
        runComposeUiTest {
            showDialog(DialogWorld(appearanceStart = AppearanceSettings()))
            val medium = await(SampleTags.TEXT).fetchSemanticsNode().size

            await(AppearanceTags.scale(UiScale.L)).performClick()
            waitForIdle()
            val large = onNodeWithTag(SampleTags.TEXT).fetchSemanticsNode().size
            await(AppearanceTags.scale(UiScale.S)).performClick()
            waitForIdle()
            val small = onNodeWithTag(SampleTags.TEXT).fetchSemanticsNode().size

            large.width shouldBeGreaterThan medium.width
            small.width shouldBeLessThan medium.width
        }

    @Test
    fun `compact density hides summaries in the preview`() =
        runComposeUiTest {
            // Given the comfortable list in the preview
            val recorder = SlotRecorder()
            showDialog(DialogWorld(appearanceStart = AppearanceSettings()), recorder)
            onNodeWithTag(SampleTags.LINES).assertTextEquals("two lines")

            // When the user picks the compact density
            await(AppearanceTags.density(ListDensity.COMPACT)).performClick()
            waitForIdle()

            // Then the preview reads the compact density
            onNodeWithTag(SampleTags.LINES).assertTextEquals("one line")
        }

    @Test
    fun `preview takes no click`() =
        runComposeUiTest {
            val recorder = SlotRecorder()
            showDialog(DialogWorld(), recorder)

            await(SampleTags.CLICKABLE).performClick()
            waitForIdle()

            recorder.clicks shouldBe 0
        }

    @Test
    fun `preview takes no Tab focus`() =
        runComposeUiTest {
            // Given the focus on the selected theme row
            showDialog(DialogWorld())
            awaitFocus(AppearanceTags.theme(PAPER))

            // When the user presses Tab more often than there are controls in the dialog
            repeat(times = TABS) { step ->
                onNodeWithTag(AppearanceTags.DIALOG).performKeyInput { pressKey(Key.Tab) }
                waitForIdle()

                // Then the focus never lands in the preview, while Tab does move it on from the theme row
                onNodeWithTag(SampleTags.FOCUSABLE).assertIsNotFocused()
                if (step == 0) onNodeWithTag(AppearanceTags.theme(PAPER)).assert(!isFocused())
            }
        }

    @Test
    fun `terminal sample uses termBg and the draft font size`() =
        runComposeUiTest {
            // Given the dialog over the plex theme, terminal font size 13
            val world = DialogWorld(appearanceStart = AppearanceSettings(), terminalStart = TerminalSettings())
            showDialog(world)
            val plex =
                showcase
                    .theme(PLEX)
                    .shouldNotBeNull()
                    .colors.terminal
            val paper =
                showcase
                    .theme(PAPER)
                    .shouldNotBeNull()
                    .colors.terminal
            (plex.termBg.argb() in await(AppearanceTags.TERMINAL_SAMPLE).argbColors()) shouldBe true
            val before = onNodeWithTag(AppearanceTags.TERMINAL_SAMPLE).fetchSemanticsNode().size

            // When the user picks paper and raises the size by five
            await(AppearanceTags.theme(PAPER)).performClick()
            repeat(times = 5) { await(AppearanceTags.TERMINAL_SIZE_PLUS).performClick() }
            waitForIdle()

            // Then the sample stands on the termBg of paper, not of plex, and its text is bigger
            val pixels = onNodeWithTag(AppearanceTags.TERMINAL_SAMPLE).argbColors()
            (paper.termBg.argb() in pixels) shouldBe true
            (plex.termBg.argb() in pixels) shouldBe false
            onNodeWithTag(AppearanceTags.TERMINAL_SAMPLE).fetchSemanticsNode().size.height shouldBeGreaterThan
                before.height
        }

    @Test
    fun `terminal sample draws the text in termText`() =
        runComposeUiTest {
            showDialog(DialogWorld(appearanceStart = AppearanceSettings()))

            val pixels = await(AppearanceTags.TERMINAL_SAMPLE).argbColors()

            // Anti-aliasing blends thin strokes, so look for a pixel nearer to the text colour than to the background
            val terminal = SampleTokens.colors().terminal
            pixels.any { it.distanceTo(terminal.termText.argb()) < it.distanceTo(terminal.termBg.argb()) } shouldBe true
        }

    @Test
    fun `suggestion row appears for a theme with another default typography`() =
        runComposeUiTest {
            // Given plex theme and plex typography: nothing to suggest
            showDialog(DialogWorld(appearanceStart = AppearanceSettings()))
            onNodeWithTag(AppearanceTags.SUGGESTION).assertDoesNotExist()

            // When the user clicks paper, which suggests geist
            await(AppearanceTags.theme(PAPER)).performClick()

            // Then the row is there; "Użyj" selects geist and the row is gone
            await(AppearanceTags.SUGGESTION)
            onNodeWithTag(AppearanceTags.typography(GEIST)).assert(!isSelected())
            await(AppearanceTags.SUGGESTION_USE).performClick()
            waitForIdle()
            onNodeWithTag(AppearanceTags.typography(GEIST)).assert(isSelected())
            onNodeWithTag(AppearanceTags.SUGGESTION).assertDoesNotExist()
        }

    @Test
    fun `suggestion goes away with a theme that suggests nothing`() =
        runComposeUiTest {
            showDialog(DialogWorld(appearanceStart = AppearanceSettings()))
            await(AppearanceTags.theme(PAPER)).performClick()
            await(AppearanceTags.SUGGESTION)

            await(AppearanceTags.theme(PLEX)).performClick()

            awaitGone(AppearanceTags.SUGGESTION)
        }

    @Test
    fun `suggestion does not change anything by itself`() =
        runComposeUiTest {
            val world = DialogWorld(appearanceStart = AppearanceSettings())
            showDialog(world)

            await(AppearanceTags.theme(PAPER)).performClick()
            await(AppearanceTags.SUGGESTION)

            onNodeWithTag(AppearanceTags.typography(PLEX_TYPOGRAPHY)).assert(isSelected())
        }

    @Test
    fun `size minus is disabled at the minimum`() =
        runComposeUiTest {
            showDialog(DialogWorld(terminalStart = TerminalSettings(fontSize = 8)))

            onNodeWithTag(AppearanceTags.TERMINAL_SIZE_MINUS).assertIsNotEnabled()
            onNodeWithTag(AppearanceTags.TERMINAL_SIZE_PLUS).assertIsEnabled()
        }

    @Test
    fun `size buttons and value have names for screen readers`() =
        runComposeUiTest {
            showDialog(DialogWorld(terminalStart = TerminalSettings(fontSize = 13)))

            onNodeWithTag(AppearanceTags.TERMINAL_SIZE_MINUS)
                .assertContentDescriptionEquals("Zmniejsz rozmiar terminala")
            onNodeWithTag(AppearanceTags.TERMINAL_SIZE_PLUS)
                .assertContentDescriptionEquals("Zwiększ rozmiar terminala")
            onNodeWithTag(AppearanceTags.TERMINAL_SIZE_VALUE)
                .assertContentDescriptionEquals("Rozmiar terminala: 13")
        }

    @Test
    fun `Enter on a focused minus steps down to the minimum`() =
        runComposeUiTest {
            showDialog(DialogWorld(terminalStart = TerminalSettings(fontSize = 9)))
            await(AppearanceTags.TERMINAL_SIZE_MINUS).requestFocus()
            awaitFocus(AppearanceTags.TERMINAL_SIZE_MINUS)

            onNodeWithTag(AppearanceTags.DIALOG).performKeyInput { pressKey(Key.Enter) }
            waitForIdle()

            onNodeWithTag(AppearanceTags.TERMINAL_SIZE_VALUE).assertContentDescriptionEquals("Rozmiar terminala: 8")
            onNodeWithTag(AppearanceTags.TERMINAL_SIZE_MINUS).assertIsNotEnabled()
        }

    @Test
    fun `size plus is disabled at the maximum`() =
        runComposeUiTest {
            showDialog(DialogWorld(terminalStart = TerminalSettings(fontSize = 32)))

            onNodeWithTag(AppearanceTags.TERMINAL_SIZE_PLUS).assertIsNotEnabled()
            onNodeWithTag(AppearanceTags.TERMINAL_SIZE_MINUS).assertIsEnabled()
        }

    @Test
    fun `escape closes without saving`() =
        runComposeUiTest {
            // Given a changed draft
            val world = DialogWorld()
            showDialog(world)
            await(AppearanceTags.theme(SUNSET)).performClick()

            // When the user presses Escape
            onNodeWithTag(AppearanceTags.DIALOG).performKeyInput { pressKey(Key.Escape) }

            // Then the dialog is gone and nothing was saved
            awaitGone(AppearanceTags.DIALOG)
            world.fake.updates shouldBe 0
            world.terminal.saves shouldBe 0
            world.fake.settings.value shouldBe SAVED
        }

    @Test
    fun `cancel closes without saving`() =
        runComposeUiTest {
            val world = DialogWorld()
            showDialog(world)
            await(AppearanceTags.theme(SUNSET)).performClick()

            await(AppearanceTags.CANCEL).performClick()

            awaitGone(AppearanceTags.DIALOG)
            world.fake.updates shouldBe 0
            world.terminal.saves shouldBe 0
        }

    @Test
    fun `cancel closes after the terminal font and size were touched`() =
        runComposeUiTest {
            val world = DialogWorld()
            showDialog(world)
            await(AppearanceTags.terminalFont("Geist Mono")).performClick()
            await(AppearanceTags.TERMINAL_SIZE_PLUS).performClick()

            await(AppearanceTags.CANCEL).performClick()

            awaitGone(AppearanceTags.DIALOG)
            world.terminal.saves shouldBe 0
        }

    @Test
    fun `apply saves the draft and closes the dialog`() =
        runComposeUiTest {
            val world = DialogWorld(appearanceStart = AppearanceSettings())
            showDialog(world)
            await(AppearanceTags.theme(SUNSET)).performClick()
            await(AppearanceTags.scale(UiScale.S)).performClick()

            await(AppearanceTags.APPLY).performClick()

            awaitGone(AppearanceTags.DIALOG)
            world.fake.settings.value.scheme shouldBe SUNSET
            world.fake.settings.value.uiScale shouldBe UiScale.S
            world.terminal.saves shouldBe 1
        }

    @Test
    fun `terminal font choices are rows that save the font of a typography or none`() =
        runComposeUiTest {
            // Given the saved font Menlo (not one of the catalog), and the dialog showing every choice as a row
            val world = DialogWorld()
            val controller = showDialog(world)

            // When the user picks the terminal font of the geist typography and applies
            await(AppearanceTags.terminalFont("Geist Mono")).performClick()
            await(AppearanceTags.APPLY).performClick()
            awaitGone(AppearanceTags.DIALOG)

            // Then the repo holds that font; the choice "from typography" saves none
            world.terminal.settings.value.fontFamily shouldBe "Geist Mono"
            runOnIdle { controller.open() }
            await(AppearanceTags.DIALOG)
            await(AppearanceTags.terminalFont(null)).performClick()
            await(AppearanceTags.APPLY).performClick()
            awaitGone(AppearanceTags.DIALOG)
            world.terminal.settings.value.fontFamily
                .shouldBeNull()
        }

    @Test
    fun `error text shows on failed apply`() =
        runComposeUiTest {
            // Given a store that refuses the change
            val world = DialogWorld()
            world.fake.failure = AppearanceError.UnknownScheme(PAPER)
            showDialog(world)
            onNodeWithTag(AppearanceTags.ERROR).assertDoesNotExist()

            // When the user applies
            await(AppearanceTags.APPLY).performClick()

            // Then the dialog stays open and says so
            await(AppearanceTags.ERROR)
            onNodeWithTag(AppearanceTags.DIALOG).assertExists()
        }

    @Test
    fun `error text shows on failed terminal write`() =
        runComposeUiTest {
            val world = DialogWorld()
            world.terminal.failure = true
            showDialog(world)

            await(AppearanceTags.APPLY).performClick()

            await(AppearanceTags.ERROR)
            onNodeWithTag(AppearanceTags.DIALOG).assertExists()
        }

    private fun Int.distanceTo(other: Int): Int =
        listOf(16, 8, 0).sumOf { shift -> abs(((this shr shift) and 0xFF) - ((other shr shift) and 0xFF)) }

    private fun ComposeUiTest.awaitSlot(
        recorder: SlotRecorder,
        matches: (ColorTokens) -> Boolean,
    ) {
        waitUntil(timeoutMillis = WAIT_MS) { recorder.colors?.let(matches) == true }
    }

    private companion object {
        /** More than the controls of the dialog: themes, typographies, scales, densities, fonts, size, buttons. */
        const val TABS = 40
    }
}
