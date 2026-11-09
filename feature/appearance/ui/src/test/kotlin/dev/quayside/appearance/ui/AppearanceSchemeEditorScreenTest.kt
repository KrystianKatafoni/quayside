package dev.quayside.appearance.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.appearance.api.SampleSchemes
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/** The scheme editor, removing and exporting custom schemes in the Appearance dialog (FR-A6, plan AM-820). */
@OptIn(ExperimentalTestApi::class)
class AppearanceSchemeEditorScreenTest {
    private val mine = SampleSchemes.mine()

    private fun worldWithMine(): DialogWorld =
        DialogWorld().also { world -> runBlocking { world.fake.addScheme(mine) } }

    @Test
    fun `editor guides from an empty form to an added scheme`() =
        runComposeUiTest {
            showDialog(DialogWorld())

            await(AppearanceTags.SCHEME_NEW).performScrollTo().performClick()

            await(AppearanceTags.EDITOR_REPORT).assertTextEquals(AppearanceStrings["editor.incomplete"])
            await(AppearanceTags.EDITOR_SAVE).assertIsNotEnabled()
            awaitGone(AppearanceTags.EDITOR_PREVIEW)

            await(AppearanceTags.editorField(EditorField.KEY)).performTextInput("mine")
            await(AppearanceTags.editorField(EditorField.NAME)).performTextInput("Mine")

            await(AppearanceTags.EDITOR_REPORT).assertTextEquals(AppearanceStrings["editor.ready"])
            await(AppearanceTags.EDITOR_PREVIEW)
            await(AppearanceTags.EDITOR_SAVE).assertIsEnabled().performClick()

            awaitGone(AppearanceTags.EDITOR)
            await(AppearanceTags.NOTICE).assertTextEquals(AppearanceStrings.of(FileNotice.SCHEME_ADDED))
            await(AppearanceTags.theme(mine.id))
        }

    @Test
    fun `low contrast shows the report, hides the sample and keeps Add off`() =
        runComposeUiTest {
            showDialog(DialogWorld())
            await(AppearanceTags.SCHEME_NEW).performScrollTo().performClick()
            await(AppearanceTags.editorField(EditorField.KEY)).performTextInput("mine")
            await(AppearanceTags.editorField(EditorField.NAME)).performTextInput("Mine")

            await(AppearanceTags.editorField(EditorField.TEXT)).performTextReplacement("#222222")

            await(AppearanceTags.EDITOR_REPORT)
                .assertTextEquals(AppearanceStrings.format("editor.contrast", "text/bg"))
            awaitGone(AppearanceTags.EDITOR_PREVIEW)
            await(AppearanceTags.EDITOR_SAVE).assertIsNotEnabled()
        }

    @Test
    fun `a taken key shows its report`() =
        runComposeUiTest {
            showDialog(DialogWorld())
            await(AppearanceTags.SCHEME_NEW).performScrollTo().performClick()

            await(AppearanceTags.editorField(EditorField.KEY)).performTextInput("plex")
            await(AppearanceTags.editorField(EditorField.NAME)).performTextInput("Plex")

            await(AppearanceTags.EDITOR_REPORT).assertTextEquals(AppearanceStrings["editor.taken"])
        }

    @Test
    fun `closing the editor removes it`() =
        runComposeUiTest {
            showDialog(DialogWorld())
            await(AppearanceTags.SCHEME_NEW).performScrollTo().performClick()
            await(AppearanceTags.EDITOR)

            await(AppearanceTags.EDITOR_CANCEL).performScrollTo().performClick()

            awaitGone(AppearanceTags.EDITOR)
        }

    @Test
    fun `remove and export are off for a built-in theme and on for a custom one`() =
        runComposeUiTest {
            showDialog(worldWithMine())
            await(AppearanceTags.SCHEME_REMOVE).performScrollTo().assertIsNotEnabled()
            await(AppearanceTags.SCHEME_EXPORT).assertIsNotEnabled()

            await(AppearanceTags.theme(mine.id)).performScrollTo().performClick()

            await(AppearanceTags.SCHEME_REMOVE).performScrollTo().assertIsEnabled()
            await(AppearanceTags.SCHEME_EXPORT).assertIsEnabled()
        }

    @Test
    fun `removing the chosen custom scheme takes its row away`() =
        runComposeUiTest {
            showDialog(worldWithMine())
            await(AppearanceTags.theme(mine.id)).performScrollTo().performClick()

            await(AppearanceTags.SCHEME_REMOVE).performScrollTo().performClick()

            awaitGone(AppearanceTags.theme(mine.id))
            await(AppearanceTags.NOTICE).assertTextEquals(AppearanceStrings.of(FileNotice.SCHEME_REMOVED))
        }

    @Test
    fun `refusing to remove the saved scheme shows the reason`() =
        runComposeUiTest {
            val world = DialogWorld(appearanceStart = SAVED.copy(scheme = mine.id))
            runBlocking { world.fake.addScheme(mine) }
            showDialog(world)

            await(AppearanceTags.SCHEME_REMOVE).performScrollTo().performClick()

            await(AppearanceTags.ERROR).assertTextEquals(AppearanceStrings.of(ApplyError.SCHEME_IN_USE))
            await(AppearanceTags.theme(mine.id))
        }
}
