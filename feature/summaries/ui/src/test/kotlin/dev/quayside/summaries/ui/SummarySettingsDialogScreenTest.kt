package dev.quayside.summaries.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.summaries.api.FakeSummarySettingsRepo
import dev.quayside.summaries.api.SummarySettingsError
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Cold JVM start-up can exceed the default 1 s of `waitUntil` in the first test. */
private const val WAIT_MS = 5_000L

/** Scenarios of the summaries settings dialog (FR-S7, NFR-6) on a fake repo. */
@OptIn(ExperimentalTestApi::class)
class SummarySettingsDialogScreenTest {
    private val repo = FakeSummarySettingsRepo()

    /** Shows the dialog over [repo] and opens it, the way the palette command does. */
    private fun ComposeUiTest.showDialog() {
        val built = mutableListOf<SummarySettingsController>()
        setContent {
            AmTestTheme {
                val scope = rememberCoroutineScope()
                val controller = remember { SummarySettingsController(repo, scope).also(built::add) }
                SummarySettingsDialog(controller)
            }
        }
        waitUntil(timeoutMillis = WAIT_MS) { built.isNotEmpty() }
        runOnIdle { built.single().open() }
        await(SummaryTags.DIALOG)
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag), WAIT_MS)
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.awaitFocus(tag: String) {
        waitUntilExactlyOneExists(hasTestTag(tag) and isFocused(), WAIT_MS)
    }

    @Test
    fun `focus is on the switch when the dialog opens`() =
        runComposeUiTest {
            showDialog()

            awaitFocus(SummaryTags.USE_MODEL)
        }

    @Test
    fun `clicking the switch shows the restart note and Save writes it`() =
        runComposeUiTest {
            showDialog()
            onNodeWithTag(SummaryTags.RESTART_NOTE).assertDoesNotExist()

            await(SummaryTags.USE_MODEL).performClick()
            await(SummaryTags.RESTART_NOTE)
            await(SummaryTags.SAVE).performClick()

            waitUntilDoesNotExist(hasTestTag(SummaryTags.DIALOG), WAIT_MS)
            repo.useModel.value shouldBe true
        }

    @Test
    fun `Space on the focused switch toggles the draft`() =
        runComposeUiTest {
            showDialog()
            awaitFocus(SummaryTags.USE_MODEL)

            onNodeWithTag(SummaryTags.USE_MODEL).performKeyInput { pressKey(Key.Spacebar) }

            await(SummaryTags.RESTART_NOTE)
        }

    @Test
    fun `Cancel closes without writing`() =
        runComposeUiTest {
            showDialog()
            await(SummaryTags.USE_MODEL).performClick()

            await(SummaryTags.CANCEL).performClick()

            waitUntilDoesNotExist(hasTestTag(SummaryTags.DIALOG), WAIT_MS)
            repo.useModel.value shouldBe false
        }

    @Test
    fun `Escape closes without writing`() =
        runComposeUiTest {
            showDialog()
            await(SummaryTags.USE_MODEL).performClick()
            awaitFocus(SummaryTags.USE_MODEL)

            onNodeWithTag(SummaryTags.USE_MODEL).performKeyInput { pressKey(Key.Escape) }

            waitUntilDoesNotExist(hasTestTag(SummaryTags.DIALOG), WAIT_MS)
            repo.useModel.value shouldBe false
        }

    @Test
    fun `a refused write shows the error and keeps the dialog`() =
        runComposeUiTest {
            repo.failure = SummarySettingsError.WRITE_FAILED
            showDialog()
            await(SummaryTags.USE_MODEL).performClick()

            await(SummaryTags.SAVE).performClick()

            await(SummaryTags.SAVE_ERROR)
            await(SummaryTags.DIALOG)
        }
}
