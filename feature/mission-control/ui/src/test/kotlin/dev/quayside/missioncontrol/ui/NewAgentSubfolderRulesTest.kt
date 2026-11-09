package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.missioncontrol.ui.NewAgentWorld.ANNA
import dev.quayside.missioncontrol.ui.NewAgentWorld.BOB
import dev.quayside.missioncontrol.ui.NewAgentWorld.GROUP
import dev.quayside.missioncontrol.ui.NewAgentWorld.OTHER
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SubfolderProblem
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Rules of the "Nowy agent" dialog for the subfolder of a group (AM-308 plan, rules 2 and 5). */
internal class NewAgentSubfolderRulesTest {
    private val listed = listOf(QUAYSIDE, GROUP, OTHER)

    @ParameterizedTest(name = "{0}")
    @MethodSource("focusCases")
    fun `default focus keeps the sidebar subfolder of a listed group`(
        @Suppress("UNUSED_PARAMETER") name: String,
        project: String?,
        subfolder: String?,
        expectedProject: String?,
        expectedSubfolder: String?,
    ) {
        val focus = project?.let { ProjectFocus(ProjectId(it), subfolder?.let(::SubfolderName)) }

        val (chosen, chosenSubfolder) = NewAgentRules.defaultFocus(focus, listed)

        // Compared as texts: a value class from a table would be unboxed by JUnit.
        listOf(chosen?.value, chosenSubfolder?.value) shouldBe listOf(expectedProject, expectedSubfolder)
    }

    @Test
    fun `default focus without projects gives none`() {
        NewAgentRules.defaultFocus(ProjectFocus(GROUP.id, ANNA), emptyList()) shouldBe (null to null)
    }

    @Test
    fun `default focus without a selection gives none even when there is one project`() {
        NewAgentRules.defaultFocus(null, listOf(QUAYSIDE)) shouldBe (null to null)
    }

    @Test
    fun `changing the project clears the subfolder`() {
        val form = NewAgentForm(GROUP.id, subfolder = ANNA)

        NewAgentRules.withProject(form, OTHER.id) shouldBe NewAgentForm(OTHER.id, subfolder = null)
    }

    @Test
    fun `choosing the same project keeps the subfolder`() {
        val form = NewAgentForm(GROUP.id, prompt = "fix", subfolder = BOB)

        NewAgentRules.withProject(form, GROUP.id) shouldBe form
    }

    @Test
    fun `choosing a project keeps the other fields`() {
        val form = NewAgentForm(GROUP.id, prompt = "fix", name = "n", agent = "a", model = ModelChoice.OPUS)

        NewAgentRules.withProject(form, OTHER.id) shouldBe form.copy(project = OTHER.id)
    }

    @Test
    fun `spec carries the subfolder name`() {
        val result = NewAgentRules.specOf(NewAgentForm(GROUP.id, prompt = "fix", subfolder = BOB))

        (result as Outcome.Ok).value.second.subfolder shouldBe "bob"
    }

    @Test
    fun `spec without subfolder has none`() {
        val result = NewAgentRules.specOf(NewAgentForm(GROUP.id, prompt = "fix"))

        (result as Outcome.Ok).value.second.subfolder shouldBe null
    }

    @Test
    fun `subfolder failures have their own texts`() {
        val notInProject =
            NewAgentRules.failureOf(
                SessionError.SubfolderUnavailable(GROUP.id, SubfolderProblem.NOT_IN_PROJECT),
            )
        val missing = NewAgentRules.failureOf(SessionError.SubfolderUnavailable(GROUP.id, SubfolderProblem.MISSING))

        listOf(notInProject, missing) shouldBe
            listOf(UiText("newAgent.failure.subfolder.notInProject"), UiText("newAgent.failure.subfolder.missing"))
        listOf(notInProject, missing).forEach {
            it.args shouldBe emptyList()
            MissionControlStrings.format(it).shouldNotBeBlank()
        }
        MissionControlStrings.format(notInProject) shouldNotBe MissionControlStrings.format(missing)
    }

    @Test
    fun `subfolder failure texts never carry the project id`() {
        SubfolderProblem.entries.forEach { problem ->
            val error = SessionError.SubfolderUnavailable(ProjectId("secret-project"), problem)

            MissionControlStrings.format(NewAgentRules.failureOf(error)) shouldNotContain "secret"
        }
    }

    @Test
    fun `form toString hides the subfolder`() {
        val form = NewAgentForm(GROUP.id, subfolder = SubfolderName("secret-folder"))

        form.toString() shouldNotContain "secret"
        form.toString() shouldContain GROUP.id.value
        NewAgentState(form, listed).toString() shouldNotContain "secret"
    }

    companion object {
        @JvmStatic
        @Suppress("LongMethod") // a table of cases
        fun focusCases(): List<Arguments> =
            listOf(
                arguments("group and its subfolder", "group", "bob", "group", "bob"),
                arguments("group and a removed subfolder", "group", "gone", "group", null),
                arguments("group and the letter case of another name", "group", "BOB", "group", null),
                arguments("unlisted project and a subfolder", "unknown", "bob", null, null),
                arguments("group row without a subfolder", "group", null, "group", null),
                arguments("plain project given a subfolder", "quayside", "bob", "quayside", null),
                arguments("plain project without a subfolder", "other", null, "other", null),
                arguments("no focus", null, null, null, null),
            )
    }
}
