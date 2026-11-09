package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.ProjectNotifications
import dev.quayside.notifications.api.ProjectProfile
import dev.quayside.notifications.api.VoiceId
import dev.quayside.projects.api.aProject
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** The project exceptions in the draft (plan AM-706, table 2): pure, registered projects only, sorted by name. */
class ProjectDraftTest {
    private val a = ProjectId("project-a")
    private val b = ProjectId("project-b")

    @Test
    fun `rows are sorted by name ignoring case`() {
        // ASCII order would give Anna, Carl, bob
        val projects =
            listOf(
                aProject(id = "p-bob", displayName = "bob"),
                aProject(id = "p-anna", displayName = "Anna"),
                aProject(id = "p-carl", displayName = "Carl"),
            )

        draftOf(NotificationSettings(), projects).projects.map { it.name } shouldBe listOf("Anna", "bob", "Carl")
    }

    @Test
    fun `row shows the saved exception or the default`() {
        val projects =
            listOf(aProject(id = "p-anna", displayName = "Anna"), aProject(id = "p-bob", displayName = "bob"))
        val settings =
            NotificationSettings(
                projects =
                    mapOf(
                        ProjectId("p-bob") to ProjectNotifications(ProjectProfile.ERRORS_ONLY, VoiceId("Ewa")),
                    ),
            )

        draftOf(settings, projects).projects shouldBe
            listOf(
                ProjectDraft(ProjectId("p-anna"), "Anna", ProjectProfile.DEFAULT, null),
                ProjectDraft(ProjectId("p-bob"), "bob", ProjectProfile.ERRORS_ONLY, VoiceId("Ewa")),
            )
    }

    @Test
    fun `a project outside the list has no row`() {
        val settings = NotificationSettings(projects = mapOf(b to ProjectNotifications(ProjectProfile.MUTED)))

        draftOf(settings, listOf(aProject(id = "project-a"))).projects.map { it.id } shouldBe listOf(a)
    }

    @ParameterizedTest
    @MethodSource("projectsTable")
    fun `projectsOf keeps unlisted exceptions and drops default rows`(
        rows: List<ProjectDraft>,
        current: Map<ProjectId, ProjectNotifications>,
        expected: Map<ProjectId, ProjectNotifications>,
    ) {
        projectsOf(rows, current) shouldBe expected
    }

    @Test
    fun `a changed profile and voice are stored for the project`() {
        val settings = NotificationSettings()
        val draft = draftOf(settings, listOf(aProject(id = "project-a", displayName = "A")))
        val edited =
            draft.copy(
                projects =
                    draft.projects.map {
                        it.copy(profile = ProjectProfile.MUTED, voice = VoiceId("Anna"))
                    },
            )

        val result = (settingsOf(edited, settings) as Outcome.Ok).value

        result.projects shouldBe mapOf(a to ProjectNotifications(ProjectProfile.MUTED, VoiceId("Anna")))
    }

    @Test
    fun `a project set back to default is removed from the exceptions`() {
        val settings = NotificationSettings(projects = mapOf(a to ProjectNotifications(ProjectProfile.MUTED)))
        val draft = draftOf(settings, listOf(aProject(id = "project-a", displayName = "A")))
        val edited =
            draft.copy(
                projects = draft.projects.map { it.copy(profile = ProjectProfile.DEFAULT, voice = null) },
            )

        val result = (settingsOf(edited, settings) as Outcome.Ok).value

        result.projects shouldBe emptyMap()
    }

    @Test
    fun `exceptions of projects outside the list are kept`() {
        val stored = ProjectNotifications(ProjectProfile.ERRORS_ONLY, VoiceId("Anna"))
        val settings = NotificationSettings(projects = mapOf(b to stored))
        val draft = draftOf(settings, listOf(aProject(id = "project-a", displayName = "A")))
        val edited = draft.copy(projects = draft.projects.map { it.copy(profile = ProjectProfile.MUTED) })

        val result = (settingsOf(edited, settings) as Outcome.Ok).value

        result.projects shouldBe mapOf(a to ProjectNotifications(ProjectProfile.MUTED), b to stored)
    }

    companion object {
        private val a = ProjectId("project-a")
        private val b = ProjectId("project-b")

        private fun row(
            profile: ProjectProfile,
            voice: String? = null,
        ) = ProjectDraft(a, "A", profile, voice?.let(::VoiceId))

        @JvmStatic
        fun projectsTable(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    listOf(row(ProjectProfile.DEFAULT)),
                    emptyMap<ProjectId, ProjectNotifications>(),
                    emptyMap<ProjectId, ProjectNotifications>(),
                ),
                Arguments.of(
                    listOf(row(ProjectProfile.ERRORS_ONLY, "Anna")),
                    emptyMap<ProjectId, ProjectNotifications>(),
                    mapOf(a to ProjectNotifications(ProjectProfile.ERRORS_ONLY, VoiceId("Anna"))),
                ),
                Arguments.of(
                    listOf(row(ProjectProfile.DEFAULT)),
                    mapOf(a to ProjectNotifications(ProjectProfile.MUTED)),
                    emptyMap<ProjectId, ProjectNotifications>(),
                ),
                Arguments.of(
                    listOf(row(ProjectProfile.MUTED)),
                    mapOf(b to ProjectNotifications(ProjectProfile.ERRORS_ONLY)),
                    mapOf(
                        a to ProjectNotifications(ProjectProfile.MUTED),
                        b to ProjectNotifications(ProjectProfile.ERRORS_ONLY),
                    ),
                ),
            )
    }
}
