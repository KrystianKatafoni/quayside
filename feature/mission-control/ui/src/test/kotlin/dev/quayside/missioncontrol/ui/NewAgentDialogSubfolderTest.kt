package dev.quayside.missioncontrol.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmDropdownTags
import dev.quayside.core.domain.ProjectId
import dev.quayside.missioncontrol.ui.NewAgentWorld.ANNA
import dev.quayside.missioncontrol.ui.NewAgentWorld.BOB
import dev.quayside.missioncontrol.ui.NewAgentWorld.CARL
import dev.quayside.missioncontrol.ui.NewAgentWorld.GROUP
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SubfolderProblem
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The "Podfolder" list of the "Nowy agent" dialog on a group (FR-S3, FR-P7, NFR-6, AM-308 plan, rule 1). The group
 * has the subfolders `Anna`, `bob` and `Carl` (the last is gone from disk); the plain projects have none.
 */
@OptIn(ExperimentalTestApi::class)
internal class NewAgentDialogSubfolderTest {
    private val fake: FakeSessions = NewAgentWorld.sessions()
    private val rig = NewAgentRig(RecordingCommands(fake))
    private val subfolder = MissionControlTags.NEW_AGENT_SUBFOLDER

    private fun ComposeUiTest.shown(
        project: ProjectId? = GROUP.id,
        subfolder: SubfolderName? = null,
        rig: NewAgentRig = this@NewAgentDialogSubfolderTest.rig,
    ) {
        showNewAgent(rig, projects = NewAgentWorld.projectsWithGroup())
        openNewAgent(rig, project, subfolder)
    }

    private fun ComposeUiTest.tagShows(
        tag: String,
        text: String,
    ) = onNode(hasTestTag(tag) and hasText(text, substring = true)).assertExists()

    private fun ComposeUiTest.typePromptAndStart() {
        onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).performTextInput("fix the login")
        onNodeWithTag(MissionControlTags.NEW_AGENT_START).performClick()
        waitForIdle()
    }

    @Test
    fun `subfolder field shows only for a group`() =
        runComposeUiTest {
            shown(QUAYSIDE.id)
            onNodeWithTag(subfolder).assertDoesNotExist()
            onNode(hasText("Podfolder")).assertDoesNotExist()

            pickOption(MissionControlTags.NEW_AGENT_PROJECT, GROUP_INDEX)
            tagShows(subfolder, "Katalog projektu")
            onNode(hasText("Podfolder")).assertExists()

            pickOption(MissionControlTags.NEW_AGENT_PROJECT, 0)
            onNodeWithTag(subfolder).assertDoesNotExist()
        }

    @Test
    fun `subfolder list starts with the project directory then subfolders in group order`() =
        runComposeUiTest {
            shown()

            openList(subfolder)

            // Anna, bob, Carl: ignoring case, not the ASCII order (Anna, Carl, bob)
            listOf("Katalog projektu", ANNA.value, BOB.value, CARL.value).forEachIndexed { index, label ->
                onNode(hasTestTag(AmDropdownTags.option(index)) and hasText(label, substring = true)).assertExists()
            }
            onNodeWithTag(AmDropdownTags.option(4)).assertDoesNotExist()
            onNodeWithTag(AmDropdownTags.option(0)).assertIsSelected()
        }

    @Test
    fun `the preselected subfolder is shown and marked`() =
        runComposeUiTest {
            shown(subfolder = BOB)

            tagShows(subfolder, BOB.value)
            openList(subfolder)
            onNodeWithTag(AmDropdownTags.option(2)).assertIsSelected()
        }

    @Test
    fun `tab goes from project to subfolder to prompt`() =
        runComposeUiTest {
            shown()
            awaitFocus(MissionControlTags.NEW_AGENT_PROMPT)

            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).pressWithShift(Key.Tab)
            awaitFocus(subfolder)
            onNodeWithTag(subfolder).pressWithShift(Key.Tab)
            awaitFocus(MissionControlTags.NEW_AGENT_PROJECT)
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROJECT).press(Key.Tab)
            awaitFocus(subfolder)
            onNodeWithTag(subfolder).press(Key.Tab)
            awaitFocus(MissionControlTags.NEW_AGENT_PROMPT)
        }

    @Test
    fun `focus starts on the prompt with a subfolder preselected`() =
        runComposeUiTest {
            shown(subfolder = ANNA)

            awaitFocus(MissionControlTags.NEW_AGENT_PROMPT)
            onNodeWithTag(subfolder).assertIsNotFocused()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROJECT).assertIsNotFocused()
        }

    @Test
    fun `picking a subfolder and starting sends its name`() =
        runComposeUiTest {
            shown()

            pickOption(subfolder, 2)
            typePromptAndStart()

            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)
            rig.commands.specs.map { it.first to it.second.subfolder } shouldBe listOf(GROUP.id to "bob")
            rig.focuses shouldBe listOf(ProjectFocus(GROUP.id, BOB))
            fake.snapshot.value.sessions
                .single()
                .cwd shouldBe GROUP.dir.resolve("bob")
        }

    @Test
    fun `picking the project directory again sends no subfolder`() =
        runComposeUiTest {
            shown(subfolder = ANNA)

            pickOption(subfolder, 0)
            typePromptAndStart()

            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)
            rig.commands.specs.map { it.second.subfolder } shouldBe listOf(null)
            rig.focuses shouldBe listOf(ProjectFocus(GROUP.id))
            fake.snapshot.value.sessions
                .single()
                .cwd shouldBe GROUP.dir
        }

    @Test
    fun `changing the project clears the subfolder`() =
        runComposeUiTest {
            shown(subfolder = ANNA)

            pickOption(MissionControlTags.NEW_AGENT_PROJECT, 0)
            rig.form().subfolder shouldBe null
            pickOption(MissionControlTags.NEW_AGENT_PROJECT, GROUP_INDEX)

            rig.form().subfolder shouldBe null
            tagShows(subfolder, "Katalog projektu")
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(SubfolderProblem::class)
    fun `subfolder problems are shown in the live region`(problem: SubfolderProblem) =
        runComposeUiTest {
            shown(subfolder = ANNA)
            fake.failNext(SessionError.SubfolderUnavailable(GROUP.id, problem))
            val key =
                when (problem) {
                    SubfolderProblem.NOT_IN_PROJECT -> "newAgent.failure.subfolder.notInProject"
                    SubfolderProblem.MISSING -> "newAgent.failure.subfolder.missing"
                }

            typePromptAndStart()

            await(MissionControlTags.NEW_AGENT_PROBLEM).assertTextEquals(text(key))
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROBLEM)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
            rig.started.shouldBeEmpty()
        }

    @Test
    fun `a subfolder gone from disk is refused with its own text`() =
        runComposeUiTest {
            shown(subfolder = CARL)

            typePromptAndStart()

            await(MissionControlTags.NEW_AGENT_PROBLEM).assertTextEquals(text("newAgent.failure.subfolder.missing"))
            rig.started.shouldBeEmpty()
            tagShows(subfolder, CARL.value)
        }

    @Test
    fun `while the start runs the subfolder list is off`() =
        runComposeUiTest {
            val gate = CompletableDeferred<Unit>()
            val gated = NewAgentRig(RecordingCommands(fake, gate))
            shown(subfolder = ANNA, rig = gated)

            typePromptAndStart()

            waitUntil { gated.commands.specs.size == 1 }
            onNodeWithTag(subfolder).assertIsNotEnabled()
            gate.complete(Unit)
        }

    private companion object {
        /** The group is the third project of the list: Quayside, Other, Group. */
        const val GROUP_INDEX = 2
    }
}
