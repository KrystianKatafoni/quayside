@file:OptIn(ExperimentalTestApi::class)

package dev.quayside.notifications.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.FakeSoundLibrary
import dev.quayside.notifications.api.FakeVoiceCatalog
import dev.quayside.notifications.api.FakeVoicePreview
import dev.quayside.notifications.api.VoiceCatalog
import dev.quayside.projects.api.Project
import kotlinx.coroutines.flow.MutableStateFlow

private const val KIT_WAIT_MS = 5_000L

/**
 * Shows the dialog over [repo] with [projects] registered and [voices] installed, and opens it. Shared by the screen
 * tests of the quiet hours and project sections (plan AM-706).
 */
internal fun ComposeUiTest.showDialogOver(
    repo: FakeNotificationSettingsRepo,
    projects: List<Project> = emptyList(),
    voices: VoiceCatalog = FakeVoiceCatalog(INSTALLED),
) {
    val built = mutableListOf<NotificationSettingsController>()
    setContent {
        AmTestTheme {
            val scope = rememberCoroutineScope()
            val controller =
                remember {
                    NotificationSettingsController(
                        repo,
                        voices,
                        FakeVoicePreview(),
                        MutableStateFlow(projects),
                        scope,
                    ).also(built::add)
                }
            NotificationSettingsDialog(controller, SoundPreviewController(FakeSoundLibrary(), scope))
        }
    }
    waitUntil(timeoutMillis = KIT_WAIT_MS) { built.isNotEmpty() }
    runOnIdle { built.single().open() }
    awaitTag(SettingsTags.DIALOG)
}

internal fun ComposeUiTest.awaitTag(tag: String): SemanticsNodeInteraction {
    waitUntilExactlyOneExists(hasTestTag(tag), KIT_WAIT_MS)
    return onNodeWithTag(tag)
}

internal fun ComposeUiTest.awaitTagText(
    tag: String,
    text: String,
) {
    waitUntilExactlyOneExists(hasTestTag(tag) and hasText(text, substring = true), KIT_WAIT_MS)
}

internal fun ComposeUiTest.awaitFocusOn(tag: String) {
    waitUntilExactlyOneExists(hasTestTag(tag) and isFocused(), KIT_WAIT_MS)
}

/** Clicks Save (scrolling to it first). */
internal fun ComposeUiTest.clickSave() {
    awaitTag(SettingsTags.SAVE).performScrollTo().performClick()
}

internal fun ComposeUiTest.awaitDialogClosed() {
    waitUntil(timeoutMillis = KIT_WAIT_MS) {
        onAllNodesWithTag(SettingsTags.DIALOG).fetchSemanticsNodes().isEmpty()
    }
}
