package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.agentruntime.api.FakeWorkspaceTrust
import dev.quayside.agentruntime.api.TrustError
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Trusting a directory from the "Nowy agent" dialog in the whole window on fakes (FR-S3, NFR-6, AM-309, ADR 0031,
 * docs/12 §4): `claude --bg` refuses the directory, the user answers the question, the start repeats. Nothing writes
 * `~/.claude.json`: the trust is a [FakeWorkspaceTrust].
 */
@OptIn(ExperimentalTestApi::class)
class NewAgentTrustWindowTest {
    private val world = AgentWorld()
    private val trust = FakeWorkspaceTrust().also { world.setup.trust = it }
    private val untrusted = SessionError.Runtime(RuntimeProblem.WORKSPACE_NOT_TRUSTED)

    @Test
    fun `FR-S3 untrusted start asks for trust and starts after consent`() =
        runComposeUiTest {
            // Given the open dialog with a prompt and a directory that Claude Code does not trust
            showWindowOf(world.setup)
            openWithProject(world.setup, "project0001")
            typePrompt("Fix the login")
            world.fake.failNext(untrusted)

            // When the user clicks "Uruchom" and agrees to trust the directory
            onNodeWithTag(WindowTags.NEW_AGENT_START).performClick()
            awaitText(WindowTags.NEW_AGENT_TRUST_PATH, world.quayside.dir.toString())
            await(WindowTags.NEW_AGENT_TRUST_ACCEPT).performClick()

            // Then exactly that directory was trusted, the same form started again and its row is selected
            awaitGone(WindowTags.NEW_AGENT_DIALOG)
            trust.trusted.map { it.toString() } shouldBe listOf(world.quayside.dir.toString())
            world.starts.specs shouldBe
                List(2) {
                    world.quayside.id to
                        world.starts.specs
                            .first()
                            .second
                }
            world.starts.specs
                .first()
                .second.prompt shouldBe "Fix the login"
            await(WindowTags.missionRow("00000001"))
            awaitDetails("00000001")
            awaitDetails("Pracuje")
        }

    @Test
    fun `FR-S3 declining trust keeps the prompt and shows the manual hint`() =
        runComposeUiTest {
            // Given the consent step after a refused start
            showWindowOf(world.setup)
            openWithProject(world.setup, "project0001")
            typePrompt("Fix the login")
            world.fake.failNext(untrusted)
            onNodeWithTag(WindowTags.NEW_AGENT_START).performClick()
            await(WindowTags.NEW_AGENT_TRUST)

            // When the user clicks "Anuluj"
            onNodeWithTag(WindowTags.NEW_AGENT_TRUST_CANCEL).performClick()

            // Then the dialog stays with the prompt and the manual instruction, and nothing was trusted or started
            awaitGone(WindowTags.NEW_AGENT_TRUST)
            awaitText(WindowTags.NEW_AGENT_PROBLEM, world.quayside.dir.toString())
            awaitText(WindowTags.NEW_AGENT_PROBLEM, "claude")
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).assertTextEquals("Fix the login")
            awaitFocus(WindowTags.NEW_AGENT_PROMPT)
            trust.trusted.shouldBeEmpty()
            world.starts.specs.size shouldBe 1
        }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["Space", "Enter"])
    fun `NFR-6 trust question starts with focus on Anuluj`(name: String) =
        runComposeUiTest {
            // Given the consent step that just opened
            showWindowOf(world.setup)
            openWithProject(world.setup, "project0001")
            typePrompt("Fix the login")
            world.fake.failNext(untrusted)
            onNodeWithTag(WindowTags.NEW_AGENT_START).performClick()
            await(WindowTags.NEW_AGENT_TRUST)
            awaitFocus(WindowTags.NEW_AGENT_TRUST_CANCEL)
            onNodeWithTag(WindowTags.NEW_AGENT_TRUST_ACCEPT).assertIsNotFocused()

            // When the user presses Space or Enter without moving the focus
            onNodeWithTag(WindowTags.NEW_AGENT_TRUST_CANCEL).press(if (name == "Space") Key.Spacebar else Key.Enter)
            waitForIdle()

            // Then nothing was trusted and no second start happened
            trust.trusted.shouldBeEmpty()
            trust.calls.shouldBeEmpty()
            world.starts.specs.size shouldBe 1
        }

    @Test
    fun `FR-S3 failed trust write shows the manual hint`() =
        runComposeUiTest {
            // Given the consent step and a config file that cannot be written
            showWindowOf(world.setup)
            openWithProject(world.setup, "project0001")
            typePrompt("Fix the login")
            world.fake.failNext(untrusted)
            onNodeWithTag(WindowTags.NEW_AGENT_START).performClick()
            await(WindowTags.NEW_AGENT_TRUST)
            trust.failNext(TrustError.CONFIG_MISSING)

            // When the user agrees to trust the directory
            onNodeWithTag(WindowTags.NEW_AGENT_TRUST_ACCEPT).performClick()

            // Then the dialog shows the manual instruction with the prompt kept, and nothing started again
            awaitGone(WindowTags.NEW_AGENT_TRUST)
            awaitText(WindowTags.NEW_AGENT_PROBLEM, world.quayside.dir.toString())
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).assertTextEquals("Fix the login")
            trust.trusted.shouldBeEmpty()
            world.starts.specs.size shouldBe 1
        }
}
