package dev.quayside.missioncontrol.ui

import androidx.compose.ui.input.key.Key
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

    private fun ComposeUiTest.clickTimes(
        tag: String,
        times: Int,
    ) = repeat(times) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
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
    fun `a start with no project preselected asks to pick one`() =
        runComposeUiTest {
            shown(project = null)

            tagShows(MissionControlTags.NEW_AGENT_PROJECT, text("newAgent.project.none"))
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROJECT).performClick()
            waitForIdle()
            rig.form().project shouldBe QUAYSIDE.id
        }

    @Test
    fun `the project is picked by click and by arrow keys`() =
        runComposeUiTest {
            shown()

            onNodeWithTag(MissionControlTags.NEW_AGENT_PROJECT).performClick()
            waitForIdle()
            rig.form().project shouldBe OTHER.id
            tagShows(MissionControlTags.NEW_AGENT_PROJECT, "Other")

            onNodeWithTag(MissionControlTags.NEW_AGENT_PROJECT).press(Key.DirectionLeft)
            waitForIdle()
            rig.form().project shouldBe QUAYSIDE.id

            onNodeWithTag(MissionControlTags.NEW_AGENT_PROJECT).press(Key.DirectionRight)
            waitForIdle()
            rig.form().project shouldBe OTHER.id
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
    fun `the model choice goes through the aliases and wraps`() =
        runComposeUiTest {
            shown()
            val expected = listOf(ModelChoice.SONNET, ModelChoice.OPUS, ModelChoice.HAIKU, ModelChoice.DEFAULT)

            expected.forEach { model ->
                clickTimes(MissionControlTags.NEW_AGENT_MODEL, 1)
                rig.form().model shouldBe model
            }
            clickTimes(MissionControlTags.NEW_AGENT_MODEL, 2)
            tagShows(MissionControlTags.NEW_AGENT_MODEL, "opus")
        }

    /**
     * Decision: a click goes round (the last mode leads back to the default), the arrow keys never wrap, so the
     * "Pomiń zgody" mode is reached only by steps forward and never by a left arrow from the default.
     */
    @Test
    fun `the permission choice goes through the four modes by click and wraps back to the default`() =
        runComposeUiTest {
            shown()
            val expected =
                listOf(
                    SessionPermissionMode.ACCEPT_EDITS to "Akceptuj edycje",
                    SessionPermissionMode.PLAN to "Tylko plan",
                    SessionPermissionMode.BYPASS_PERMISSIONS to "Pomiń zgody",
                    null to "Domyślny",
                )

            expected.forEach { (mode, label) ->
                clickTimes(MissionControlTags.NEW_AGENT_PERMISSION, 1)
                rig.form().permission shouldBe mode
                tagShows(MissionControlTags.NEW_AGENT_PERMISSION, label)
            }
        }

    @Test
    fun `left arrow from the default mode does not select bypass permissions`() =
        runComposeUiTest {
            shown()
            clickTimes(MissionControlTags.NEW_AGENT_PERMISSION, 1)
            onNodeWithTag(MissionControlTags.NEW_AGENT_PERMISSION).press(Key.DirectionLeft)
            waitForIdle()
            rig.form().permission shouldBe null

            onNodeWithTag(MissionControlTags.NEW_AGENT_PERMISSION).press(Key.DirectionLeft)
            waitForIdle()

            rig.form().permission shouldBe null
            onNodeWithTag(MissionControlTags.NEW_AGENT_WARNING).assertDoesNotExist()
        }

    @Test
    fun `arrows go through the modes one way and stop at the ends`() =
        runComposeUiTest {
            shown()
            clickTimes(MissionControlTags.NEW_AGENT_PERMISSION, 1)
            val field = onNodeWithTag(MissionControlTags.NEW_AGENT_PERMISSION)

            repeat(4) {
                field.press(Key.DirectionRight)
                waitForIdle()
            }

            rig.form().permission shouldBe SessionPermissionMode.BYPASS_PERMISSIONS
            field.press(Key.DirectionLeft)
            waitForIdle()
            rig.form().permission shouldBe SessionPermissionMode.PLAN
        }

    @Test
    fun `skipping permissions shows a warning that goes away with another mode`() =
        runComposeUiTest {
            shown()
            clickTimes(MissionControlTags.NEW_AGENT_PERMISSION, 2)
            onNodeWithTag(MissionControlTags.NEW_AGENT_WARNING).assertDoesNotExist()

            clickTimes(MissionControlTags.NEW_AGENT_PERMISSION, 1)

            onNodeWithTag(MissionControlTags.NEW_AGENT_WARNING).assertTextEquals(text("newAgent.bypassWarning"))
            clickTimes(MissionControlTags.NEW_AGENT_PERMISSION, 1)
            onNodeWithTag(MissionControlTags.NEW_AGENT_WARNING).assertDoesNotExist()
        }

    @Test
    fun `Uruchom starts with the values of the fields and closes`() =
        runComposeUiTest {
            shown()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).performTextInput(" Fix the login ")
            onNodeWithTag(MissionControlTags.NEW_AGENT_NAME).performTextInput("login-fix")
            onNodeWithTag(MissionControlTags.NEW_AGENT_AGENT).performTextInput("code-reviewer")
            clickTimes(MissionControlTags.NEW_AGENT_MODEL, 2)
            clickTimes(MissionControlTags.NEW_AGENT_PERMISSION, 2)

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
