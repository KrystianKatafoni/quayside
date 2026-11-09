package dev.quayside.notifications.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.FakeVoiceCatalog
import dev.quayside.notifications.api.FakeVoicePreview
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.ProjectNotifications
import dev.quayside.notifications.api.ProjectProfile
import dev.quayside.notifications.api.VoiceId
import dev.quayside.projects.api.aProject
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The project exceptions in the notification settings controller (FR-N7, plan AM-706). */
class NotificationSettingsProjectsTest {
    private val anna = aProject(id = "project-anna", displayName = "Anna")
    private val bob = aProject(id = "project-bob", displayName = "bob")
    private val list = MutableStateFlow(listOf(bob, anna))

    private fun TestScope.controllerOver(repo: ScriptedRepo) =
        NotificationSettingsController(
            repo,
            FakeVoiceCatalog(emptyList()),
            FakeVoicePreview(),
            list,
            backgroundScope,
        )

    private fun scripted(settings: NotificationSettings = NotificationSettings()) =
        ScriptedRepo(FakeNotificationSettingsRepo(settings))

    private fun NotificationSettingsController.rows(): List<ProjectDraft> =
        state.value
            .shouldNotBeNull()
            .draft.projects

    @Test
    fun `draft lists the registered projects at open`() =
        runTest {
            val controller = controllerOver(scripted())

            act { controller.open() }

            controller.rows() shouldBe
                listOf(
                    ProjectDraft(anna.id, "Anna", ProjectProfile.DEFAULT, null),
                    ProjectDraft(bob.id, "bob", ProjectProfile.DEFAULT, null),
                )
        }

    @Test
    fun `project list is read again at every open`() =
        runTest {
            val controller = controllerOver(scripted())
            act { controller.open() }
            act { controller.cancel() }
            val carl = aProject(id = "project-carl", displayName = "Carl")

            list.value = list.value + carl
            act { controller.open() }

            controller.rows().map { it.name } shouldBe listOf("Anna", "bob", "Carl")
        }

    @Test
    fun `projects added while the dialog is open do not appear in it`() =
        runTest {
            val controller = controllerOver(scripted())
            act { controller.open() }

            act { list.value = list.value + aProject(id = "project-carl", displayName = "Carl") }

            controller.rows().map { it.name } shouldBe listOf("Anna", "bob")
        }

    @Test
    fun `saved profile and voice reach the repo`() =
        runTest {
            val repo = scripted()
            val controller = controllerOver(repo)
            act { controller.open() }

            act {
                controller.edit { draft ->
                    draft.copy(
                        projects =
                            draft.projects.map {
                                if (it.id ==
                                    bob.id
                                ) {
                                    it.copy(profile = ProjectProfile.ERRORS_ONLY, voice = VoiceId("Ewa"))
                                } else {
                                    it
                                }
                            },
                    )
                }
            }
            act { controller.save() }

            repo.settings.value.projects shouldBe
                mapOf(bob.id to ProjectNotifications(ProjectProfile.ERRORS_ONLY, VoiceId("Ewa")))
        }

    @Test
    fun `saving keeps the exception of a project that was removed`() =
        runTest {
            val gone = ProjectId("project-gone")
            val stored = ProjectNotifications(ProjectProfile.MUTED)
            val repo = scripted(NotificationSettings(projects = mapOf(gone to stored)))
            val controller = controllerOver(repo)
            act { controller.open() }
            act {
                controller.edit { draft ->
                    draft.copy(projects = draft.projects.map { it.copy(profile = ProjectProfile.MUTED) })
                }
            }

            act { controller.save() }

            repo.settings.value.projects shouldBe
                mapOf(
                    gone to stored,
                    anna.id to ProjectNotifications(ProjectProfile.MUTED),
                    bob.id to ProjectNotifications(ProjectProfile.MUTED),
                )
        }
}
