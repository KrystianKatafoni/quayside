package dev.quayside.appearance.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.FakeAppearanceApi
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeFileError
import dev.quayside.core.designtokens.ColorTokens
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Scenarios of day/night and of the theme files in the Appearance dialog (FR-A5, FR-A6, plan AM-804) on fakes. The
 * preview slot is the sample of [SampleSlot]; it records the colours of the theme it is drawn in.
 */
@OptIn(ExperimentalTestApi::class)
class AppearanceDayNightScreenTest {
    private val showcase = FakeAppearanceApi.showcaseCatalog()

    private fun colorsOf(id: SchemeId): ColorTokens = showcase.theme(id).shouldNotBeNull().colors

    private fun ComposeUiTest.awaitSlot(
        recorder: SlotRecorder,
        expected: ColorTokens,
    ) {
        waitUntil(timeoutMillis = WAIT_MS) { recorder.colors == expected }
    }

    private fun ComposeUiTest.press(tag: String) {
        await(tag).performScrollTo().performClick()
    }

    @Test
    fun `day night switch is announced as a switch`() =
        runComposeUiTest {
            showDialog(DialogWorld())

            await(
                AppearanceTags.DAY_NIGHT_SWITCH,
            ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        }

    @Test
    fun `segment is hidden while day night is off`() =
        runComposeUiTest {
            showDialog(DialogWorld())

            await(AppearanceTags.DAY_NIGHT_SWITCH).assertIsOff()
            onNodeWithTag(AppearanceTags.SLOT_SEGMENT).assertDoesNotExist()
        }

    @Test
    fun `day night switch shows the day night segment`() =
        runComposeUiTest {
            // Given the dialog with day/night off
            showDialog(DialogWorld())

            // When the user turns the switch on
            press(AppearanceTags.DAY_NIGHT_SWITCH)

            // Then the segment with Day and Night is there, Day edited first
            await(AppearanceTags.SLOT_SEGMENT)
            onNodeWithTag(AppearanceTags.DAY_NIGHT_SWITCH).assertIsOn()
            await(AppearanceTags.slot(ThemeSlot.DAY)).assert(isSelected())
            await(AppearanceTags.slot(ThemeSlot.NIGHT)).assert(!isSelected())

            // And turning it off hides the segment again
            press(AppearanceTags.DAY_NIGHT_SWITCH)
            awaitGone(AppearanceTags.SLOT_SEGMENT)
        }

    @Test
    fun `clicking a theme in night segment recolors the preview`() =
        runComposeUiTest {
            // Given the dialog over the theme sunset, day/night off
            val world = DialogWorld(appearanceStart = AppearanceSettings(scheme = SUNSET))
            val recorder = SlotRecorder()
            showDialog(world, recorder)
            awaitSlot(recorder, colorsOf(SUNSET))

            // When day/night is turned on, the preview shows the day theme (paper)
            press(AppearanceTags.DAY_NIGHT_SWITCH)
            awaitSlot(recorder, colorsOf(PAPER))
            await(AppearanceTags.theme(PAPER)).assert(isSelected())

            // And the Night segment shows the night theme (plex)
            press(AppearanceTags.slot(ThemeSlot.NIGHT))
            awaitSlot(recorder, colorsOf(PLEX))
            await(AppearanceTags.theme(PLEX)).assert(isSelected())

            // And a click on sunset sets the night theme only
            await(AppearanceTags.theme(SUNSET)).performClick()
            awaitSlot(recorder, colorsOf(SUNSET))
            await(AppearanceTags.theme(SUNSET)).assert(isSelected())
            press(AppearanceTags.slot(ThemeSlot.DAY))
            awaitSlot(recorder, colorsOf(PAPER))

            // Then nothing is applied yet
            world.fake.updates shouldBe 0
            world.fake.settings.value shouldBe AppearanceSettings(scheme = SUNSET)
        }

    @Test
    fun `import and export buttons are not focused at start`() =
        runComposeUiTest {
            showDialog(DialogWorld())
            awaitFocus(AppearanceTags.theme(PAPER))

            await(AppearanceTags.IMPORT).assertIsNotFocused()
            await(AppearanceTags.IMPORT_SCHEMES).assertIsNotFocused()
            await(AppearanceTags.EXPORT).assertIsNotFocused()
        }

    @Test
    fun `import error text shows`() =
        runComposeUiTest {
            val world = DialogWorld()
            world.files.readFailure = ThemeFileError.NotThemeFile
            showDialog(world)

            press(AppearanceTags.IMPORT)

            await(AppearanceTags.ERROR).assertTextEquals(AppearanceStrings.of(ApplyError.IMPORT_NOT_THEME))
        }

    @Test
    fun `imported notice shows and the preview follows the imported theme`() =
        runComposeUiTest {
            // Given the dialog over saved settings with the theme paper
            val recorder = SlotRecorder()
            showDialog(DialogWorld(), recorder)
            awaitSlot(recorder, colorsOf(PAPER))

            // When the user imports a file with day/night on, plex by day
            press(AppearanceTags.IMPORT)

            // Then the notice shows, the segment is there and the preview shows the day theme of the file
            await(AppearanceTags.NOTICE).assertTextEquals(AppearanceStrings.of(FileNotice.IMPORTED))
            await(AppearanceTags.SLOT_SEGMENT)
            awaitSlot(recorder, colorsOf(IMPORTED.dayNight.day))
        }

    @Test
    fun `exported notice shows`() =
        runComposeUiTest {
            val world = DialogWorld()
            showDialog(world)

            press(AppearanceTags.EXPORT)

            await(AppearanceTags.NOTICE).assertTextEquals(AppearanceStrings.of(FileNotice.EXPORTED))
            world.files.written shouldBe mapOf(EXPORT_PATH to SAVED)
        }

    @Test
    fun `apply after import saves the imported settings`() =
        runComposeUiTest {
            // Given an imported file in the draft
            val world = DialogWorld()
            showDialog(world)
            press(AppearanceTags.IMPORT)
            await(AppearanceTags.NOTICE)
            world.fake.settings.value shouldBe SAVED

            // When the user applies
            press(AppearanceTags.APPLY)
            awaitGone(AppearanceTags.DIALOG)

            // Then the imported settings are saved
            world.fake.settings.value shouldBe IMPORTED
        }

    @Test
    fun `cancel after import saves nothing`() =
        runComposeUiTest {
            val world = DialogWorld()
            showDialog(world)
            press(AppearanceTags.IMPORT)
            await(AppearanceTags.NOTICE)

            press(AppearanceTags.CANCEL)
            awaitGone(AppearanceTags.DIALOG)

            world.fake.settings.value shouldBe SAVED
            world.fake.updates shouldBe 0
        }
}
