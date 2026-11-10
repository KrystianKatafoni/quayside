package dev.quayside.missioncontrol.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.sessions.api.FakeSessionHistorySettings
import dev.quayside.sessions.api.HistoryAge
import dev.quayside.sessions.api.HistoryLimit
import dev.quayside.sessions.api.HistorySettingsError
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The window "Stare sesje z dysku" over its controller on a fake repo (FR-P9, AM-312). */
@OptIn(ExperimentalTestApi::class)
internal class HistorySettingsDialogScreenTest {
    private val repo = FakeSessionHistorySettings()
    private var controller: HistorySettingsController? = null

    private fun ComposeUiTest.shown(): HistorySettingsController {
        setContent {
            AmTestTheme {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val shown = androidx.compose.runtime.remember { HistorySettingsController(repo, scope) }
                controller = shown
                HistorySettingsDialog(shown)
            }
        }
        return checkNotNull(controller)
    }

    private fun ComposeUiTest.open() {
        val shown = shown()
        runOnIdle { shown.open() }
        waitUntilExactlyOneExists(hasTestTag(HistoryTags.DIALOG))
    }

    private fun ComposeUiTest.age(age: HistoryAge) = onNodeWithTag(HistoryTags.age(age.name))

    private fun ComposeUiTest.limit(limit: HistoryLimit) = onNodeWithTag(HistoryTags.limit(limit.name))

    @Test
    fun `the closed window draws nothing`() =
        runComposeUiTest {
            shown()

            onNodeWithTag(HistoryTags.DIALOG).assertDoesNotExist()
        }

    @Test
    fun `an open window shows every choice with the saved ones selected`() =
        runComposeUiTest {
            open()

            HistoryAge.entries.forEach { age(it).assertExists() }
            HistoryLimit.entries.forEach { limit(it).assertExists() }
            age(HistoryAge.DAYS_30).assertIsSelected()
            limit(HistoryLimit.SESSIONS_200).assertIsSelected()
            age(HistoryAge.DAYS_7).assertIsNotSelected()
            onNodeWithTag(HistoryTags.DIALOG).assertExists()
            onNode(hasText("Bez limitu")).assertExists()
        }

    @Test
    fun `a click saves at once and moves the selection`() =
        runComposeUiTest {
            open()

            age(HistoryAge.DAYS_7).performClick()
            limit(HistoryLimit.SESSIONS_50).performClick()

            waitUntil { repo.settings.value.maxPerProject == HistoryLimit.SESSIONS_50 }
            repo.settings.value.maxAge shouldBe HistoryAge.DAYS_7
            age(HistoryAge.DAYS_7).assertIsSelected()
            age(HistoryAge.DAYS_30).assertIsNotSelected()
            limit(HistoryLimit.SESSIONS_50).assertIsSelected()
        }

    @Test
    fun `picking another value and coming back to the saved one selects it again`() =
        runComposeUiTest {
            open()

            age(HistoryAge.DAYS_90).performClick()
            waitUntil { repo.settings.value.maxAge == HistoryAge.DAYS_90 }
            age(HistoryAge.DAYS_30).performClick()
            waitUntil { repo.settings.value.maxAge == HistoryAge.DAYS_30 }

            age(HistoryAge.DAYS_30).assertIsSelected()
            age(HistoryAge.DAYS_90).assertIsNotSelected()
        }

    @Test
    fun `a failed save shows the error and keeps the saved choice selected`() =
        runComposeUiTest {
            open()
            repo.failNext(HistorySettingsError.WRITE_FAILED)

            age(HistoryAge.DAYS_7).performClick()

            waitUntilExactlyOneExists(hasTestTag(HistoryTags.ERROR))
            age(HistoryAge.DAYS_30).assertIsSelected()
            age(HistoryAge.DAYS_7).assertIsNotSelected()
        }

    @Test
    fun `the close button and Escape close the window`() =
        runComposeUiTest {
            open()
            onNodeWithTag(HistoryTags.CLOSE).performClick()
            waitUntil { controller?.state?.value == null }

            runOnIdle { controller?.open() }
            waitUntilExactlyOneExists(hasTestTag(HistoryTags.DIALOG))
            onNodeWithTag(HistoryTags.CLOSE).performKeyInput { pressKey(Key.Escape) }
            waitUntil { controller?.state?.value == null }
        }
}
