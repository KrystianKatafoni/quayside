package dev.quayside.notifications.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.domain.ProjectId
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.ProjectNotifications
import dev.quayside.notifications.api.ProjectProfile
import dev.quayside.notifications.api.VoiceId
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.aProject
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private const val WAIT_MS = 5_000L

/** Tab order from the Focus switch to Save with the projects Anna and bob registered (plan AM-706). */
private val PROJECT_ORDER =
    listOf(
        SettingsTags.RESPECT_FOCUS,
        SettingsTags.projectProfile(ProjectId("project-anna")),
        SettingsTags.projectVoice(ProjectId("project-anna")),
        SettingsTags.projectProfile(ProjectId("project-bob")),
        SettingsTags.projectVoice(ProjectId("project-bob")),
        SettingsTags.SAVE,
    )

/** Scenarios of the "Wyjątki dla projektów" section of the notification settings dialog (FR-N7, NFR-6). */
@OptIn(ExperimentalTestApi::class)
class NotificationSettingsProjectsScreenTest {
    private val anna = aProject(id = "project-anna", displayName = "Anna")
    private val bob = aProject(id = "project-bob", displayName = "bob")
    private var repo = FakeNotificationSettingsRepo()

    private fun ComposeUiTest.showDialog(
        saved: NotificationSettings = NotificationSettings(),
        projects: List<Project> = listOf(bob, anna),
    ) {
        repo = FakeNotificationSettingsRepo(saved)
        showDialogOver(repo, projects)
    }

    @Test
    fun `every registered project has a profile and a voice choice`() =
        runComposeUiTest {
            // Given two registered projects without exceptions
            showDialog()

            // Then each has its name, a profile choice at "Domyślny" and a voice choice at the global voice
            listOf(anna, bob).forEach { project ->
                awaitTag(SettingsTags.projectProfile(project.id)).performScrollTo()
                awaitTagText(SettingsTags.projectProfile(project.id), "Domyślny")
                awaitTagText(SettingsTags.projectVoice(project.id), "Głos globalny")
                waitUntilExactlyOneExists(hasText(project.displayName), WAIT_MS)
            }
            // And the note of an empty list is not shown
            onNodeWithTag(SettingsTags.PROJECTS_EMPTY).assertDoesNotExist()
        }

    @Test
    fun `profile choice is saved for the project`() =
        runComposeUiTest {
            // Given the open dialog
            showDialog()

            // When the user picks "Tylko błędy" for Anna and saves
            pickOption(SettingsTags.projectProfile(anna.id), ProjectProfile.ERRORS_ONLY.ordinal)
            awaitTagText(SettingsTags.projectProfile(anna.id), "Tylko błędy")
            clickSave()
            awaitDialogClosed()

            // Then only Anna has an exception
            repo.settings.value.projects shouldBe
                mapOf(anna.id to ProjectNotifications(ProjectProfile.ERRORS_ONLY))
        }

    @Test
    fun `project voice offers the global voice first and is saved`() =
        runComposeUiTest {
            // Given the open dialog and the voices loaded
            showDialog()
            awaitTag(SettingsTags.projectVoice(bob.id)).performScrollTo()

            // When the user opens the voice list of bob: the global voice is first, then the installed ones
            openList(SettingsTags.projectVoice(bob.id))
            optionShows(0, "Głos globalny")
            optionShows(1, "Ewa")

            // And picks Ewa and saves
            clickOption(1)
            awaitTagText(SettingsTags.projectVoice(bob.id), "Ewa")
            clickSave()
            awaitDialogClosed()

            // Then bob has the voice and the default profile
            repo.settings.value.projects shouldBe mapOf(bob.id to ProjectNotifications(voice = VoiceId("Ewa")))
        }

    @Test
    fun `a saved project voice that is not installed stays in the list`() =
        runComposeUiTest {
            // Given bob with a voice that the Mac no longer has
            showDialog(NotificationSettings(projects = mapOf(bob.id to ProjectNotifications(voice = VoiceId("Gone")))))

            // Then the choice shows it as not installed
            awaitTag(SettingsTags.projectVoice(bob.id)).performScrollTo()
            awaitTagText(SettingsTags.projectVoice(bob.id), "Gone (niezainstalowany)")
        }

    @Test
    fun `a saved voice that is not installed stays in the list after another voice was picked`() =
        runComposeUiTest {
            // Given bob with the voice "Gone" that the Mac no longer has; the list is: global, Gone, Ewa, ...
            showDialog(NotificationSettings(projects = mapOf(bob.id to ProjectNotifications(voice = VoiceId("Gone")))))
            awaitTagText(SettingsTags.projectVoice(bob.id), "Gone (niezainstalowany)")

            // When the user picks Ewa and opens the list again
            pickOption(SettingsTags.projectVoice(bob.id), 2)
            awaitTagText(SettingsTags.projectVoice(bob.id), "Ewa")
            openList(SettingsTags.projectVoice(bob.id))

            // Then Gone is still the second entry; picking it and saving stores it
            optionShows(1, "Gone (niezainstalowany)")
            clickOption(1)
            clickSave()
            awaitDialogClosed()
            repo.settings.value.projects shouldBe mapOf(bob.id to ProjectNotifications(voice = VoiceId("Gone")))
        }

    @Test
    fun `tab moves through the project choices in order`() =
        runComposeUiTest {
            // Given the focus on the Focus switch before the projects section
            showDialog()
            awaitTag(SettingsTags.RESPECT_FOCUS).performScrollTo().focus()
            awaitFocusOn(SettingsTags.RESPECT_FOCUS)

            // When the user presses Tab, once per control
            PROJECT_ORDER.zipWithNext().forEach { (from, to) ->
                onNodeWithTag(from).performKeyInput { pressKey(Key.Tab) }

                // Then the focus moves on: profile and voice of Anna, of bob, then Save
                awaitFocusOn(to)
            }
        }

    @Test
    fun `no projects shows the empty note`() =
        runComposeUiTest {
            // Given no registered project
            showDialog(projects = emptyList())

            // Then the note tells where to add one
            awaitTag(SettingsTags.PROJECTS_EMPTY).performScrollTo()
            awaitTagText(
                SettingsTags.PROJECTS_EMPTY,
                "Brak projektów. Dodaj folder projektu w pasku bocznym, a pojawi się tu z wyjątkami.",
            )
        }

    @Test
    fun `choices are named with the project for the screen reader`() =
        runComposeUiTest {
            // Given the open dialog
            showDialog()

            // Then both choices of a project carry its name
            awaitTag(SettingsTags.projectProfile(anna.id))
                .performScrollTo()
                .assertContentDescriptionEquals("Profil: Anna")
            awaitTag(SettingsTags.projectVoice(anna.id)).assertContentDescriptionEquals("Głos: Anna")
            awaitTag(SettingsTags.projectProfile(bob.id)).assertContentDescriptionEquals("Profil: bob")
        }

    @Test
    fun `arrow key on a closed profile choice does not change it`() =
        runComposeUiTest {
            // Given the focus on the profile choice of Anna, at "Domyślny"
            showDialog()
            awaitTag(SettingsTags.projectProfile(anna.id)).performScrollTo().focus()
            awaitFocusOn(SettingsTags.projectProfile(anna.id))

            // When the user presses Up: the list opens; Up again must not wrap to "Wyciszony"; Enter confirms
            pressKeyOn(SettingsTags.projectProfile(anna.id), Key.DirectionUp)
            awaitListOpen()
            pressKeyOn(SettingsTags.projectProfile(anna.id), Key.DirectionUp)
            pressKeyOn(SettingsTags.projectProfile(anna.id), Key.Enter)
            awaitListGone()

            // Then the profile is still "Domyślny" and a save stores no exception
            awaitTagText(SettingsTags.projectProfile(anna.id), "Domyślny")
            clickSave()
            awaitDialogClosed()
            repo.settings.value.projects shouldBe emptyMap()
        }
}
