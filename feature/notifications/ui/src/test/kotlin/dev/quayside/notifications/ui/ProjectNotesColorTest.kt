package dev.quayside.notifications.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.FakeVoiceCatalog
import dev.quayside.notifications.api.FakeVoicePreview
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.aProject
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test

private const val WAIT_MS = 5_000L

/** The colours of the captions of the project exceptions section (NFR-6, AM-706): muted, never the error colour. */
@OptIn(ExperimentalTestApi::class)
class ProjectNotesColorTest {
    private val muted =
        SampleTokens
            .colors()
            .content.muted
            .toColor()
            .toArgb()
    private val err =
        SampleTokens
            .colors()
            .status.err
            .toColor()
            .toArgb()

    private fun ComposeUiTest.pixels(tag: String): Set<Int> {
        waitUntilExactlyOneExists(hasTestTag(tag), WAIT_MS)
        val map = onNodeWithTag(tag).captureToImage().toPixelMap()
        return (0 until map.width)
            .flatMap { x -> (0 until map.height).map { y -> map[x, y].toArgb() } }
            .toSet()
    }

    private fun ComposeUiTest.showSection(projects: List<Project>) {
        val state =
            NotificationDialogState(
                draft = draftOf(NotificationSettings(), projects),
                invalid = null,
                saveError = null,
            )
        setContent {
            AmTestTheme {
                val scope = rememberCoroutineScope()
                val controller =
                    remember {
                        NotificationSettingsController(
                            FakeNotificationSettingsRepo(),
                            FakeVoiceCatalog(emptyList()),
                            FakeVoicePreview(),
                            MutableStateFlow(emptyList()),
                            scope,
                        )
                    }
                Column { ProjectsSection(state, controller) }
            }
        }
    }

    @Test
    fun `the hint and the empty note use the muted colour`() =
        runComposeUiTest {
            // Given a section without projects
            showSection(emptyList())

            // Then both captions are muted and not err
            (muted in pixels(SettingsTags.PROJECTS_HINT)) shouldBe true
            (muted in pixels(SettingsTags.PROJECTS_EMPTY)) shouldBe true
            (err in pixels(SettingsTags.PROJECTS_HINT)) shouldBe false
            (err in pixels(SettingsTags.PROJECTS_EMPTY)) shouldBe false
        }

    @Test
    fun `the hint uses the muted colour with projects registered`() =
        runComposeUiTest {
            showSection(listOf(aProject(id = "project-anna", displayName = "Anna")))

            (muted in pixels(SettingsTags.PROJECTS_HINT)) shouldBe true
            (err in pixels(SettingsTags.PROJECTS_HINT)) shouldBe false
        }
}
