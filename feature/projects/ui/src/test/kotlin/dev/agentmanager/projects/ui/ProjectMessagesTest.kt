package dev.agentmanager.projects.ui

import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.projects.api.NameProblem
import dev.agentmanager.projects.api.ProjectError
import dev.agentmanager.projects.api.ProjectInputRules
import dev.agentmanager.projects.api.StorageIssue
import dev.agentmanager.projects.api.StorageProblem
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/** Every error and storage issue has one fixed text without paths (AM-304 plan, decision 3; ENG-C8). */
internal class ProjectMessagesTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("errors")
    fun `every project error has a fixed key`(
        case: String,
        error: ProjectError,
        expected: UiText,
    ) {
        withClue(case) { ProjectMessages.of(error) shouldBe expected }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("issues")
    fun `every storage issue has a fixed key`(
        case: String,
        issue: StorageIssue,
        expected: UiText,
    ) {
        withClue(case) { ProjectMessages.of(issue) shouldBe expected }
    }

    @Test
    fun `different errors and issues never share a key`() {
        val keys =
            allErrors().map { ProjectMessages.of(it).key } + StorageIssue.entries.map { ProjectMessages.of(it).key }

        keys.toSet() shouldHaveSize 11 + 3
    }

    @Test
    fun `no message holds the path of the error`() {
        allErrors().forEach { error ->
            val text = ProjectMessages.of(error)
            withClue(error) {
                text.key shouldNotContain SECRET
                text.args.forEach { it shouldNotContain SECRET }
                ProjectsStrings.format(text) shouldNotContain SECRET
            }
        }
    }

    companion object {
        private const val SECRET = "secret-user"
        private val DIR: Path = Path.of("/Users/$SECRET/x")
        private val ID = ProjectId("project0001")

        /** One value of every variant and enum entry: 5 plain variants + 3 name problems + 3 storage problems. */
        fun allErrors(): List<ProjectError> =
            listOf(
                ProjectError.NotAbsolute(Path.of("$SECRET/x")),
                ProjectError.NotFound(DIR),
                ProjectError.NotADirectory(DIR),
                ProjectError.AlreadyAdded(ID),
                ProjectError.UnknownProject(ID),
            ) + NameProblem.entries.map(ProjectError::InvalidDisplayName) +
                StorageProblem.entries.map(ProjectError::StorageFailed)

        @JvmStatic
        fun errors(): List<Arguments> = pathAndListErrors() + nameErrors() + storageErrors()

        private fun pathAndListErrors(): List<Arguments> =
            listOf(
                arguments("relative path", ProjectError.NotAbsolute(Path.of("x")), UiText("error.notAbsolute")),
                arguments("missing folder", ProjectError.NotFound(DIR), UiText("error.notFound")),
                arguments("a file, not a folder", ProjectError.NotADirectory(DIR), UiText("error.notADirectory")),
                arguments("already on the list", ProjectError.AlreadyAdded(ID), UiText("error.alreadyAdded")),
                arguments("unknown project", ProjectError.UnknownProject(ID), UiText("error.unknownProject")),
            )

        private fun nameErrors(): List<Arguments> =
            listOf(
                arguments("blank name", ProjectError.InvalidDisplayName(NameProblem.BLANK), UiText("error.name.blank")),
                arguments(
                    "name too long names the limit",
                    ProjectError.InvalidDisplayName(NameProblem.TOO_LONG),
                    UiText("error.name.tooLong", listOf(ProjectInputRules.MAX_DISPLAY_NAME_LENGTH.toString())),
                ),
                arguments(
                    "control characters in the name",
                    ProjectError.InvalidDisplayName(NameProblem.CONTROL_CHARACTERS),
                    UiText("error.name.controlCharacters"),
                ),
            )

        private fun storageErrors(): List<Arguments> =
            listOf(
                arguments(
                    "list not readable",
                    ProjectError.StorageFailed(StorageProblem.READ_FAILED),
                    UiText("error.storage.readFailed"),
                ),
                arguments(
                    "list not writable",
                    ProjectError.StorageFailed(StorageProblem.WRITE_FAILED),
                    UiText("error.storage.writeFailed"),
                ),
                arguments(
                    "list from a newer version",
                    ProjectError.StorageFailed(StorageProblem.NEWER_SCHEMA),
                    UiText("error.storage.newerSchema"),
                ),
            )

        @JvmStatic
        fun issues(): List<Arguments> =
            listOf(
                arguments(
                    "broken file moved aside",
                    StorageIssue.RECOVERED_FROM_BROKEN_FILE,
                    UiText("storageIssue.recovered"),
                ),
                arguments("file not readable", StorageIssue.READ_FAILED, UiText("storageIssue.readFailed")),
                arguments("file from a newer version", StorageIssue.NEWER_SCHEMA, UiText("storageIssue.newerSchema")),
            )
    }
}
