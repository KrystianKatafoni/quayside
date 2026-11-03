package dev.quayside.missioncontrol.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Test

/**
 * The running start and its failure in the "Nowy agent" dialog (FR-S3, AM-608 rule 4): while it runs the fields and
 * buttons are off and nothing closes the dialog; after a failure the form stays and the start works again.
 */
@OptIn(ExperimentalTestApi::class)
internal class NewAgentDialogBusyTest {
    private val fake: FakeSessions = NewAgentWorld.sessions()
    private val gate = CompletableDeferred<Unit>()
    private val trusting = SessionError.Runtime(RuntimeProblem.COMMAND_FAILED)

    private fun ComposeUiTest.shown(rig: NewAgentRig) {
        showNewAgent(rig)
        openNewAgent(rig, QUAYSIDE.id)
        awaitFocus(MissionControlTags.NEW_AGENT_PROMPT)
        runOnIdle {
            val form = rig.form().copy(prompt = NewAgentWorld.SECRET_PROMPT, name = "keep-name")
            checkNotNull(rig.controller).onEvent(NewAgentEvent.Edit(form))
        }
    }

    private fun ComposeUiTest.clickStart() {
        onNodeWithTag(MissionControlTags.NEW_AGENT_START).performClick()
        waitForIdle()
    }

    @Test
    fun `while the start runs the fields and buttons are off`() =
        runComposeUiTest {
            val rig = NewAgentRig(RecordingCommands(fake, gate))
            shown(rig)

            clickStart()

            waitUntil { rig.commands.specs.size == 1 }
            listOf(
                MissionControlTags.NEW_AGENT_PROMPT,
                MissionControlTags.NEW_AGENT_NAME,
                MissionControlTags.NEW_AGENT_AGENT,
                MissionControlTags.NEW_AGENT_START,
                MissionControlTags.NEW_AGENT_CANCEL,
            ).forEach { onNodeWithTag(it).assertIsNotEnabled() }
            rig.state()?.busy shouldBe true
        }

    @Test
    fun `while the start runs the choices do not change`() =
        runComposeUiTest {
            val rig = NewAgentRig(RecordingCommands(fake, gate))
            shown(rig)
            clickStart()
            waitUntil { rig.commands.specs.size == 1 }
            val before = rig.form()

            onNodeWithTag(MissionControlTags.NEW_AGENT_MODEL).performClick()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PERMISSION).performClick()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROJECT).performClick()
            waitForIdle()

            rig.form() shouldBe before
        }

    @Test
    fun `while the start runs Escape and Anuluj do nothing and the start finishes`() =
        runComposeUiTest {
            val rig = NewAgentRig(RecordingCommands(fake, gate))
            shown(rig)
            clickStart()
            waitUntil { rig.commands.specs.size == 1 }

            onNodeWithTag(MissionControlTags.NEW_AGENT_DIALOG).press(Key.Escape)
            onNodeWithTag(MissionControlTags.NEW_AGENT_CANCEL).performClick()
            waitForIdle()

            onNodeWithTag(MissionControlTags.NEW_AGENT_DIALOG).assertExists()
            rig.state()?.busy shouldBe true
            runOnIdle { gate.complete(Unit) }
            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)
            rig.started.size shouldBe 1
        }

    @Test
    fun `while the start runs cmd Enter and a second click do not start again`() =
        runComposeUiTest {
            val rig = NewAgentRig(RecordingCommands(fake, gate))
            shown(rig)
            clickStart()
            waitUntil { rig.commands.specs.size == 1 }

            onNodeWithTag(MissionControlTags.NEW_AGENT_DIALOG).pressWithMeta(Key.Enter)
            onNodeWithTag(MissionControlTags.NEW_AGENT_START).performClick()
            runOnIdle { gate.complete(Unit) }
            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)

            rig.commands.specs.size shouldBe 1
            rig.started.size shouldBe 1
        }

    @Test
    fun `a failed start keeps the form, shows the trust hint and gives the focus back to the prompt`() =
        runComposeUiTest {
            val rig = NewAgentRig(RecordingCommands(fake))
            shown(rig)
            fake.failNext(trusting)

            clickStart()

            await(MissionControlTags.NEW_AGENT_PROBLEM).assertTextEquals(text("newAgent.failure.commandFailed"))
            rig.form().prompt shouldBe NewAgentWorld.SECRET_PROMPT
            rig.form().name shouldBe "keep-name"
            rig.state()?.busy shouldBe false
            awaitFocus(MissionControlTags.NEW_AGENT_PROMPT)
            listOf(
                MissionControlTags.NEW_AGENT_PROMPT,
                MissionControlTags.NEW_AGENT_NAME,
                MissionControlTags.NEW_AGENT_START,
                MissionControlTags.NEW_AGENT_CANCEL,
            ).forEach { onNodeWithTag(it).assertIsEnabled() }
            rig.started.shouldBeEmpty()
        }

    @Test
    fun `the failure text does not quote the prompt`() =
        runComposeUiTest {
            val rig = NewAgentRig(RecordingCommands(fake))
            shown(rig)
            fake.failNext(trusting)

            clickStart()

            text("newAgent.failure.commandFailed").shouldNotContain(NewAgentWorld.SECRET_PROMPT)
            await(MissionControlTags.NEW_AGENT_PROBLEM).assertTextEquals(text("newAgent.failure.commandFailed"))
        }

    @Test
    fun `after a failed start the next click starts again`() =
        runComposeUiTest {
            val rig = NewAgentRig(RecordingCommands(fake))
            shown(rig)
            fake.failNext(trusting)
            clickStart()
            await(MissionControlTags.NEW_AGENT_PROBLEM)

            clickStart()

            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)
            rig.commands.specs.size shouldBe 2
            rig.started.size shouldBe 1
        }

    @Test
    fun `after a failed start Escape still cancels`() =
        runComposeUiTest {
            val rig = NewAgentRig(RecordingCommands(fake))
            shown(rig)
            fake.failNext(trusting)
            clickStart()
            await(MissionControlTags.NEW_AGENT_PROBLEM)

            onNodeWithTag(MissionControlTags.NEW_AGENT_DIALOG).press(Key.Escape)

            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)
            rig.started.shouldBeEmpty()
        }

    @Test
    fun `a project that is gone clears the project field and says so`() =
        runComposeUiTest {
            val rig = NewAgentRig(RecordingCommands(fake))
            shown(rig)
            fake.failNext(SessionError.UnknownProject(QUAYSIDE.id))

            clickStart()

            await(MissionControlTags.NEW_AGENT_PROBLEM).assertTextEquals(text("newAgent.failure.unknownProject"))
            rig.form().project shouldBe null
        }

    @Test
    fun `the dialog reopened after a success is fresh and focused on the prompt`() =
        runComposeUiTest {
            val rig = NewAgentRig(RecordingCommands(fake))
            shown(rig)
            clickStart()
            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)

            openNewAgent(rig, QUAYSIDE.id)

            awaitFocus(MissionControlTags.NEW_AGENT_PROMPT)
            rig.form() shouldBe NewAgentForm(QUAYSIDE.id)
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROBLEM).assertDoesNotExist()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).assertIsEnabled()
        }
}
