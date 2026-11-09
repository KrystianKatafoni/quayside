package dev.quayside.projects.ui

import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.ProjectInputRules
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.SubfolderProblem
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

/** Fixed texts for the errors of `setSubfolders`: one key per [SubfolderProblem], never the name (ENG-C8). */
internal class SubfolderMessagesTest {
    private val secret = "jane-secret"

    private fun error(problem: SubfolderProblem) = ProjectError.InvalidSubfolder(SubfolderName(secret), problem)

    @ParameterizedTest(name = "{0}")
    @MethodSource("keys")
    fun `every problem has a fixed key`(
        problem: SubfolderProblem,
        expected: UiText,
    ) {
        ProjectMessages.of(error(problem)) shouldBe expected
    }

    @Test
    fun `different problems never share a key`() {
        SubfolderProblem.entries.map { ProjectMessages.of(error(it)).key }.toSet() shouldHaveSize
            SubfolderProblem.entries.size
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(SubfolderProblem::class)
    fun `every problem has a text in the bundle without the name`(problem: SubfolderProblem) {
        val text = ProjectMessages.of(error(problem))

        ProjectsStrings.format(text).shouldNotBeBlank()
        ProjectsStrings.format(text) shouldNotContain secret
        text.key shouldNotContain secret
        text.args.forEach { it shouldNotContain secret }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(SubfolderProblem::class)
    fun `log category is fixed and holds no name`(problem: SubfolderProblem) {
        error(problem).category() shouldBe "invalidSubfolder"
    }

    @Test
    fun `too many names say the limit as a plain number`() {
        // "64", not "64" formatted by MessageFormat with a grouping separator
        ProjectMessages.of(error(SubfolderProblem.TOO_MANY)).args shouldBe
            listOf(ProjectInputRules.MAX_SUBFOLDERS.toString())
    }

    companion object {
        @JvmStatic
        fun keys(): List<Arguments> =
            listOf(
                arguments(SubfolderProblem.NOT_A_NAME, UiText("error.subfolder.notAName")),
                arguments(SubfolderProblem.HIDDEN, UiText("error.subfolder.hidden")),
                arguments(SubfolderProblem.MISSING, UiText("error.subfolder.missing")),
                arguments(SubfolderProblem.NOT_A_DIRECTORY, UiText("error.subfolder.notADirectory")),
                arguments(SubfolderProblem.SYMLINK, UiText("error.subfolder.symlink")),
                arguments(
                    SubfolderProblem.TOO_MANY,
                    UiText("error.subfolder.tooMany", listOf(ProjectInputRules.MAX_SUBFOLDERS.toString())),
                ),
            )
    }
}
