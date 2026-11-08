package dev.quayside.projects.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

/** [ProjectInputRules.subfolderName] and the small types of AM-306: a name, a focus, the error that hides it. */
class SubfolderRulesTest {
    private fun rejected(
        text: String,
        reason: SubfolderProblem,
    ) = Outcome.Err(ProjectError.InvalidSubfolder(SubfolderName(text), reason))

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = ["soft", "my project", "a.b", "Zażółć", "x", "-dash", "name.with.dots", "UPPER"])
    fun `valid name is accepted as given`(text: String) {
        ProjectInputRules.subfolderName(text) shouldBe Outcome.Ok(SubfolderName(text))
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = ["", "   ", ".", "..", "a/b", "/", "soft/", "a\nb", "a\u0000b", "tab\there"])
    fun `not a name is rejected`(text: String) {
        ProjectInputRules.subfolderName(text) shouldBe rejected(text, SubfolderProblem.NOT_A_NAME)
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = [".git", ".hidden", ".a", ".DS_Store"])
    fun `leading dot is hidden`(text: String) {
        ProjectInputRules.subfolderName(text) shouldBe rejected(text, SubfolderProblem.HIDDEN)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lengths")
    fun `length is counted in code points`(
        @Suppress("UNUSED_PARAMETER") case: String,
        text: String,
        accepted: Boolean,
    ) {
        val expected = if (accepted) Outcome.Ok(SubfolderName(text)) else rejected(text, SubfolderProblem.NOT_A_NAME)

        ProjectInputRules.subfolderName(text) shouldBe expected
    }

    @Test
    fun `limit of subfolders is 64`() {
        ProjectInputRules.MAX_SUBFOLDERS shouldBe 64
    }

    @Test
    fun `SubfolderName hides its value in toString`() {
        SubfolderName("jane-secret").toString() shouldNotContain "jane-secret"
    }

    @Test
    fun `SubfolderName compares by value`() {
        SubfolderName("soft") shouldBe SubfolderName("soft")
        (SubfolderName("soft") == SubfolderName("Soft")) shouldBe false
    }

    @Test
    fun `InvalidSubfolder hides the name in toString`() {
        val text = ProjectError.InvalidSubfolder(SubfolderName("jane-secret"), SubfolderProblem.MISSING).toString()

        text shouldNotContain "jane-secret"
    }

    @Test
    fun `InvalidSubfolder still compares the name and the reason`() {
        ProjectError.InvalidSubfolder(SubfolderName("a"), SubfolderProblem.MISSING) shouldBe
            ProjectError.InvalidSubfolder(SubfolderName("a"), SubfolderProblem.MISSING)
        (
            ProjectError.InvalidSubfolder(SubfolderName("a"), SubfolderProblem.MISSING) ==
                ProjectError.InvalidSubfolder(SubfolderName("b"), SubfolderProblem.MISSING)
        ) shouldBe false
    }

    @Test
    fun `ProjectFocus defaults to the whole project`() {
        ProjectFocus(ProjectId("p")).subfolder shouldBe null
        ProjectFocus(ProjectId("p")) shouldBe ProjectFocus(ProjectId("p"), subfolder = null)
        (ProjectFocus(ProjectId("p")) == ProjectFocus(ProjectId("p"), SubfolderName("soft"))) shouldBe false
    }

    @Test
    fun `a project has no subfolders by default`() {
        aProject().subfolders shouldBe emptyList()
    }

    companion object {
        private const val MAX = ProjectInputRules.MAX_DISPLAY_NAME_LENGTH
        private const val EMOJI = "😀"

        @JvmStatic
        fun lengths(): List<Arguments> =
            listOf(
                arguments("longest ascii name", "x".repeat(MAX), true),
                arguments("one code point too long", "x".repeat(MAX + 1), false),
                // 45 emoji are 90 UTF-16 units but 45 code points: a length in units would reject them
                arguments("45 emoji are 45 code points", EMOJI.repeat(45), true),
                arguments("longest emoji name", EMOJI.repeat(MAX), true),
                arguments("one emoji too long", EMOJI.repeat(MAX + 1), false),
            )
    }
}
