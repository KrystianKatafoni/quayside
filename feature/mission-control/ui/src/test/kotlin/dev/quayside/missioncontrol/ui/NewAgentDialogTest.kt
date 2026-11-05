package dev.quayside.missioncontrol.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.domain.ProjectId
import dev.quayside.missioncontrol.ui.NewAgentWorld.OTHER
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.SessionPermissionMode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The fields and buttons of the "Nowy agent" dialog over the controller on fakes (FR-S3, AM-608 rules 3 and 7).
 * Keys and focus are in [NewAgentDialogKeysTest], the running start and its failure in [NewAgentDialogBusyTest].
 */
@OptIn(ExperimentalTestApi::class)
internal class NewAgentDialogTest {
    private val rig = NewAgentRig(RecordingCommands(NewAgentWorld.sessions()))

    private fun ComposeUiTest.shown(project: ProjectId? = QUAYSIDE.id) {
        showNewAgent(rig)
        openNewAgent(rig, project)
    }

    private fun ComposeUiTest.tagShows(
        tag: String,
        text: String,
    ) = onNode(hasTestTag(tag) and hasText(text, substring = true)).assertExists()

    private fun ComposeUiTest.edit(change: (NewAgentForm) -> NewAgentForm) =
        runOnIdle {
            checkNotNull(rig.controller).onEvent(NewAgentEvent.Edit(change(rig.form())))
        }

    @Test
    fun `the closed dialog draws nothing`() =
        runComposeUiTest {
            showNewAgent(rig)

            onNodeWithTag(MissionControlTags.NEW_AGENT_DIALOG).assertDoesNotExist()
        }

    @Test
    fun `opening draws the fields with their defaults`() =
        runComposeUiTest {
            shown()

            tagShows(MissionControlTags.NEW_AGENT_PROJECT, "Quayside")
            tagShows(MissionControlTags.NEW_AGENT_MODEL, text("newAgent.model.default"))
            tagShows(MissionControlTags.NEW_AGENT_PERMISSION, text("newAgent.permission.default"))
            tagShows(MissionControlTags.NEW_AGENT_START, "Uruchom")
            tagShows(MissionControlTags.NEW_AGENT_CANCEL, text("dialog.cancel"))
            listOf(
                MissionControlTags.NEW_AGENT_PROMPT,
                MissionControlTags.NEW_AGENT_NAME,
                MissionControlTags.NEW_AGENT_AGENT,
            ).forEach { onNodeWithTag(it).assertExists() }
            onNodeWithTag(MissionControlTags.NEW_AGENT_WARNING).assertDoesNotExist()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROBLEM).assertDoesNotExist()
        }

    @Test
    fun `typing edits prompt, name and subagent`() =
        runComposeUiTest {
            shown()

            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).performTextInput("Fix the login")
            onNodeWithTag(MissionControlTags.NEW_AGENT_NAME).performTextInput("login-fix")
            onNodeWithTag(MissionControlTags.NEW_AGENT_AGENT).performTextInput("code-reviewer")
            waitForIdle()

            rig.form() shouldBe NewAgentForm(QUAYSIDE.id, "Fix the login", "login-fix", "code-reviewer")
        }

    @Test
    fun `the subagent field says where the names come from`() =
        runComposeUiTest {
            shown()

            text("newAgent.agent.hint").let {
                it.contains(".claude/agents") shouldBe true
                it.contains("~/.claude/agents") shouldBe true
            }
        }

    @Test
    fun `Uruchom starts with the values of the fields and closes`() =
        runComposeUiTest {
            shown()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).performTextInput(" Fix the login ")
            onNodeWithTag(MissionControlTags.NEW_AGENT_NAME).performTextInput("login-fix")
            onNodeWithTag(MissionControlTags.NEW_AGENT_AGENT).performTextInput("code-reviewer")
            pickOption(MissionControlTags.NEW_AGENT_MODEL, ModelChoice.entries.indexOf(ModelChoice.OPUS))
            pickOption(MissionControlTags.NEW_AGENT_PERMISSION, 2)

            onNodeWithTag(MissionControlTags.NEW_AGENT_START).performClick()

            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)
            rig.commands.specs shouldBe
                listOf(
                    QUAYSIDE.id to
                        NewSessionSpec(
                            "Fix the login",
                            "login-fix",
                            "code-reviewer",
                            "opus",
                            SessionPermissionMode.PLAN,
                        ),
                )
            rig.started.single().first shouldBe QUAYSIDE.id
        }

    @Test
    fun `Anuluj closes without a start`() =
        runComposeUiTest {
            shown()
            edit { it.copy(prompt = "fix") }

            onNodeWithTag(MissionControlTags.NEW_AGENT_CANCEL).performClick()

            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)
            rig.commands.specs.shouldBeEmpty()
            rig.started.shouldBeEmpty()
        }

    @Test
    fun `a reopened dialog starts empty`() =
        runComposeUiTest {
            shown()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).performTextInput("old prompt")
            onNodeWithTag(MissionControlTags.NEW_AGENT_CANCEL).performClick()
            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)

            openNewAgent(rig, OTHER.id)

            rig.form() shouldBe NewAgentForm(OTHER.id)
            tagShows(MissionControlTags.NEW_AGENT_PROJECT, "Other")
        }

    @Test
    fun `an empty prompt shows the problem and starts nothing`() =
        runComposeUiTest {
            shown()

            onNodeWithTag(MissionControlTags.NEW_AGENT_START).performClick()

            await(MissionControlTags.NEW_AGENT_PROBLEM).assertTextEquals(text("newAgent.problem.emptyPrompt"))
            rig.commands.specs.shouldBeEmpty()
        }

    @Test
    fun `the problem is announced as a live region`() =
        runComposeUiTest {
            shown()

            onNodeWithTag(MissionControlTags.NEW_AGENT_START).performClick()

            await(MissionControlTags.NEW_AGENT_PROBLEM)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        }

    @Test
    fun `each validation problem has its text`() =
        runComposeUiTest {
            shown(project = null)
            val cases =
                listOf(
                    NewAgentForm(null, prompt = "fix") to "newAgent.problem.noProject",
                    NewAgentForm(QUAYSIDE.id, prompt = "fix", name = "-x") to "newAgent.problem.nameInvalid",
                    NewAgentForm(QUAYSIDE.id, prompt = "fix", agent = "bad agent") to "newAgent.problem.agentInvalid",
                )

            cases.forEach { (form, key) ->
                runOnIdle { checkNotNull(rig.controller).onEvent(NewAgentEvent.Edit(form)) }
                onNodeWithTag(MissionControlTags.NEW_AGENT_START).performClick()
                waitForIdle()
                onNodeWithTag(MissionControlTags.NEW_AGENT_PROBLEM).assertTextEquals(text(key))
            }
            rig.commands.specs.shouldBeEmpty()
        }
}
