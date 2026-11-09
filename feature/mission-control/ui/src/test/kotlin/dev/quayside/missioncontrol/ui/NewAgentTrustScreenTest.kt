package dev.quayside.missioncontrol.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * The consent step "Zaufać katalogowi?" in the "Nowy agent" dialog (NFR-6, FR-S3, AM-309 rule 5, ADR 0031): the focus
 * starts on "Anuluj", no key right after the step opens can grant trust, Escape declines, the path is shown.
 */
@OptIn(ExperimentalTestApi::class)
internal class NewAgentTrustScreenTest {
    private val fake = NewAgentWorld.sessions()
    private val rig = NewAgentRig(RecordingCommands(fake))
    private val trusting = SessionError.Runtime(RuntimeProblem.WORKSPACE_NOT_TRUSTED)

    /** The dialog with a valid form whose start is refused as untrusted: the consent step is on screen. */
    private fun ComposeUiTest.asked() {
        showNewAgent(rig)
        openNewAgent(rig, QUAYSIDE.id)
        awaitFocus(MissionControlTags.NEW_AGENT_PROMPT)
        runOnIdle {
            val controller = checkNotNull(rig.controller)
            controller.onEvent(NewAgentEvent.Edit(rig.form().copy(prompt = "fix the login")))
            fake.failNext(trusting)
            controller.onEvent(NewAgentEvent.Submit)
        }
        await(MissionControlTags.NEW_AGENT_TRUST)
    }

    @Test
    fun `trust step starts with focus on Anuluj`() =
        runComposeUiTest {
            asked()

            awaitFocus(MissionControlTags.NEW_AGENT_TRUST_CANCEL)

            onNodeWithTag(MissionControlTags.NEW_AGENT_TRUST_ACCEPT).assertIsNotFocused()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).assertDoesNotExist()
            rig.trust.calls.shouldBeEmpty()
        }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["Space", "Enter"])
    fun `space right after the trust step declines, never trusts`(name: String) =
        runComposeUiTest {
            asked()
            awaitFocus(MissionControlTags.NEW_AGENT_TRUST_CANCEL)

            onNodeWithTag(MissionControlTags.NEW_AGENT_TRUST_CANCEL).press(KEYS.getValue(name))
            waitForIdle()

            rig.trust.calls.shouldBeEmpty()
            rig.state()?.trust.shouldBeNull()
            rig.state()?.problem shouldBe NewAgentProblem.NotTrusted(QUAYSIDE.dir)
            rig.started.shouldBeEmpty()
        }

    @Test
    fun `cmd enter in the trust step does nothing`() =
        runComposeUiTest {
            asked()
            awaitFocus(MissionControlTags.NEW_AGENT_TRUST_CANCEL)

            onNodeWithTag(MissionControlTags.NEW_AGENT_TRUST_CANCEL).pressWithMeta(Key.Enter)
            waitForIdle()

            rig.trust.calls.shouldBeEmpty()
            rig.commands.specs.size shouldBe 1
            rig.state()?.trust shouldBe TrustStep(QUAYSIDE.dir)
            onNodeWithTag(MissionControlTags.NEW_AGENT_TRUST).assertExists()
        }

    @Test
    fun `tab reaches Zaufaj i uruchom`() =
        runComposeUiTest {
            asked()
            awaitFocus(MissionControlTags.NEW_AGENT_TRUST_CANCEL)

            onNodeWithTag(MissionControlTags.NEW_AGENT_TRUST_CANCEL).press(Key.Tab)

            awaitFocus(MissionControlTags.NEW_AGENT_TRUST_ACCEPT)
            onNodeWithTag(MissionControlTags.NEW_AGENT_TRUST_ACCEPT).assertIsEnabled()
            rig.trust.calls.shouldBeEmpty()
        }

    @Test
    fun `escape in the trust step returns to the prompt with focus`() =
        runComposeUiTest {
            asked()
            awaitFocus(MissionControlTags.NEW_AGENT_TRUST_CANCEL)

            onNodeWithTag(MissionControlTags.NEW_AGENT_DIALOG).press(Key.Escape)

            awaitGone(MissionControlTags.NEW_AGENT_TRUST)
            awaitFocus(MissionControlTags.NEW_AGENT_PROMPT)
            await(MissionControlTags.NEW_AGENT_PROBLEM)
            rig.state()?.problem shouldBe NewAgentProblem.NotTrusted(QUAYSIDE.dir)
            rig.form().prompt shouldBe "fix the login"
            rig.trust.calls.shouldBeEmpty()
        }

    @Test
    fun `trust question shows the path`() =
        runComposeUiTest {
            asked()

            await(MissionControlTags.NEW_AGENT_TRUST_PATH).assertTextContains(QUAYSIDE.dir.toString())
            await(MissionControlTags.NEW_AGENT_TRUST_WARNING).assertTextContains(text("newAgent.trust.warning"))
            onNodeWithTag(MissionControlTags.NEW_AGENT_TRUST_CANCEL).assertTextContains(text("newAgent.trust.cancel"))
            onNodeWithTag(MissionControlTags.NEW_AGENT_TRUST_ACCEPT).assertTextContains(text("newAgent.trust.accept"))
        }

    @Test
    fun `Zaufaj i uruchom trusts exactly that directory and starts the agent`() =
        runComposeUiTest {
            asked()

            await(MissionControlTags.NEW_AGENT_TRUST_ACCEPT).performClick()

            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)
            rig.trust.trusted.map { it.toString() } shouldBe listOf(QUAYSIDE.dir.toString())
            rig.commands.specs.size shouldBe 2
            rig.started.size shouldBe 1
        }

    @Test
    fun `the manual hint after a decline names the directory`() =
        runComposeUiTest {
            asked()

            await(MissionControlTags.NEW_AGENT_TRUST_CANCEL).performClick()

            awaitGone(MissionControlTags.NEW_AGENT_TRUST)
            await(MissionControlTags.NEW_AGENT_PROBLEM).assertTextContains(QUAYSIDE.dir.toString(), substring = true)
        }

    private companion object {
        val KEYS = mapOf("Space" to Key.Spacebar, "Enter" to Key.Enter)
    }
}
