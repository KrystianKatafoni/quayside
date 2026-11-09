package dev.quayside.appearance.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.appearance.api.SampleSchemes
import dev.quayside.appearance.api.ThemeFileError
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Importing custom schemes in the Appearance dialog (FR-A6, plan AM-814) on fakes. */
@OptIn(ExperimentalTestApi::class)
class AppearanceSchemesScreenTest {
    private val mine = SampleSchemes.mine()
    private val path = Path.of("/themes/schemes.json")

    private fun worldWithFile(): DialogWorld =
        DialogWorld().also {
            it.files.putSchemes(path, listOf(mine))
            it.picker.openResult = path
        }

    @Test
    fun `imported scheme shows in the theme list with a notice`() =
        runComposeUiTest {
            // Given the dialog and a file with one custom scheme
            showDialog(worldWithFile())

            // When the user clicks "Importuj schemat…"
            await(AppearanceTags.IMPORT_SCHEMES).performScrollTo().performClick()

            // Then the notice shows and the list has a row for the new theme
            await(AppearanceTags.NOTICE).assertTextEquals(AppearanceStrings.of(FileNotice.SCHEMES_IMPORTED))
            await(AppearanceTags.theme(mine.id))
        }

    @Test
    fun `scheme import error text shows`() =
        runComposeUiTest {
            val world = worldWithFile()
            world.files.readFailure = ThemeFileError.NotThemeFile
            showDialog(world)

            await(AppearanceTags.IMPORT_SCHEMES).performScrollTo().performClick()

            await(AppearanceTags.ERROR).assertTextEquals(AppearanceStrings.of(ApplyError.IMPORT_NOT_THEME))
        }

    @Test
    fun `scheme import button is not focused at start`() =
        runComposeUiTest {
            showDialog(worldWithFile())
            awaitFocus(AppearanceTags.theme(PAPER))

            await(AppearanceTags.IMPORT_SCHEMES).assertIsNotFocused()
        }
}
