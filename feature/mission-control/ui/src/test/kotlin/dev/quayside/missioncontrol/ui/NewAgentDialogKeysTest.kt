package dev.quayside.missioncontrol.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Focus and keys of the "Nowy agent" dialog (FR-S3, NFR-6, AM-608 rule 7): the focus starts on the prompt and never
 * on "Uruchom"; Enter in the prompt is a new line; ⌘⏎ starts; Escape cancels; Tab moves the focus.
 */
@OptIn(ExperimentalTestApi::class)
internal class NewAgentDialogKeysTest {
    private val rig = NewAgentRig(RecordingCommands(NewAgentWorld.sessions()))

    private val order =
        listOf(
            MissionControlTags.NEW_AGENT_PROJECT,
            MissionControlTags.NEW_AGENT_PROMPT,
            MissionControlTags.NEW_AGENT_NAME,
            MissionControlTags.NEW_AGENT_AGENT,
            MissionControlTags.NEW_AGENT_MODEL,
            MissionControlTags.NEW_AGENT_PERMISSION,
            MissionControlTags.NEW_AGENT_CANCEL,
            MissionControlTags.NEW_AGENT_START,
        )

    private fun ComposeUiTest.shown() {
        showNewAgent(rig)
        openNewAgent(rig, QUAYSIDE.id)
        awaitFocus(MissionControlTags.NEW_AGENT_PROMPT)
    }

    /** A valid form, so that a start would work if a key reached "Uruchom". */
    private fun ComposeUiTest.fill() =
        runOnIdle {
            checkNotNull(rig.controller).onEvent(NewAgentEvent.Edit(rig.form().copy(prompt = "fix the login")))
        }

    @Test
    fun `NFR-6 the focus starts on the prompt and not on a button`() =
        runComposeUiTest {
            shown()

            onNodeWithTag(MissionControlTags.NEW_AGENT_START).assertIsNotFocused()
            onNodeWithTag(MissionControlTags.NEW_AGENT_CANCEL).assertIsNotFocused()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROJECT).assertIsNotFocused()
        }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["Enter", "Space"])
    fun `NFR-6 a key right after opening does not start the agent`(name: String) =
        runComposeUiTest {
            shown()
            fill()
            val key = if (name == "Enter") Key.Enter else Key.Spacebar

            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).press(key)
            waitForIdle()

            rig.commands.specs.shouldBeEmpty()
            rig.started.shouldBeEmpty()
            onNodeWithTag(MissionControlTags.NEW_AGENT_DIALOG).assertExists()
        }

    @Test
    fun `Enter in the prompt adds a line and does not start`() =
        runComposeUiTest {
            shown()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).performTextInput("first")

            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).press(Key.Enter)
            waitForIdle()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).performTextInput("second")
            waitForIdle()

            rig.form().prompt shouldBe "first\nsecond"
            rig.commands.specs.shouldBeEmpty()
            onNodeWithTag(MissionControlTags.NEW_AGENT_DIALOG).assertExists()
        }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            MissionControlTags.NEW_AGENT_PROMPT,
            MissionControlTags.NEW_AGENT_NAME,
            MissionControlTags.NEW_AGENT_AGENT,
        ],
    )
    fun `cmd Enter starts from any text field`(tag: String) =
        runComposeUiTest {
            shown()
            fill()
            onNodeWithTag(tag).performClick()
            waitForIdle()

            onNodeWithTag(tag).pressWithMeta(Key.Enter)

            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)
            rig.commands.specs.size shouldBe 1
            rig.started.size shouldBe 1
        }

    /** A click on a dropdown field opens its list, so the focus comes by Tab or by request; the list stays closed. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            MissionControlTags.NEW_AGENT_PROJECT,
            MissionControlTags.NEW_AGENT_MODEL,
            MissionControlTags.NEW_AGENT_PERMISSION,
        ],
    )
    fun `cmd Enter starts from a closed dropdown field`(tag: String) =
        runComposeUiTest {
            shown()
            fill()
            onNodeWithTag(tag).focus()
            awaitFocus(tag)

            onNodeWithTag(tag).pressWithMeta(Key.Enter)

            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)
            rig.commands.specs.size shouldBe 1
            rig.started.size shouldBe 1
        }

    @Test
    fun `Escape in an open list keeps the dialog and the focus goes back to the field`() =
        runComposeUiTest {
            shown()
            fill()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PERMISSION).focus()
            awaitFocus(MissionControlTags.NEW_AGENT_PERMISSION)
            onNodeWithTag(MissionControlTags.NEW_AGENT_PERMISSION).press(Key.Enter)
            awaitListOpen()

            pressKeyOn(MissionControlTags.NEW_AGENT_PERMISSION, Key.Escape)
            awaitListGone()

            onNodeWithTag(MissionControlTags.NEW_AGENT_DIALOG).assertExists()
            awaitFocus(MissionControlTags.NEW_AGENT_PERMISSION)
            rig.commands.specs.shouldBeEmpty()
        }

    @Test
    fun `cmd Enter with an empty prompt shows the problem`() =
        runComposeUiTest {
            shown()

            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).pressWithMeta(Key.Enter)

            await(MissionControlTags.NEW_AGENT_PROBLEM)
            rig.commands.specs.shouldBeEmpty()
        }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            MissionControlTags.NEW_AGENT_PROMPT,
            MissionControlTags.NEW_AGENT_NAME,
            MissionControlTags.NEW_AGENT_PERMISSION,
        ],
    )
    fun `Escape cancels from any field with a closed list`(tag: String) =
        runComposeUiTest {
            shown()
            fill()
            onNodeWithTag(tag).focus()
            awaitFocus(tag)
            rig.commands.specs.shouldBeEmpty()

            onNodeWithTag(MissionControlTags.NEW_AGENT_DIALOG).press(Key.Escape)

            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)
            rig.commands.specs.shouldBeEmpty()
            rig.started.shouldBeEmpty()
        }

    @Test
    fun `NFR-6 Tab goes through the fields in the order of the form`() =
        runComposeUiTest {
            shown()

            order.drop(1).zipWithNext().forEach { (from, to) ->
                onNodeWithTag(from).press(Key.Tab)
                awaitFocus(to)
            }
        }

    @Test
    fun `NFR-6 Shift Tab goes back through the fields`() =
        runComposeUiTest {
            shown()

            order.take(2).reversed().zipWithNext().forEach { (from, to) ->
                onNodeWithTag(from).pressWithShift(Key.Tab)
                awaitFocus(to)
            }
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROJECT).press(Key.Tab)
            awaitFocus(MissionControlTags.NEW_AGENT_PROMPT)
        }

    @Test
    fun `NFR-6 Tab in the prompt moves the focus and adds no tab character`() =
        runComposeUiTest {
            shown()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).performTextInput("abc")
            waitForIdle()

            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).press(Key.Tab)
            awaitFocus(MissionControlTags.NEW_AGENT_NAME)

            rig.form().prompt shouldBe "abc"
        }

    @Test
    fun `NFR-6 Shift Tab in the prompt adds no tab character either`() =
        runComposeUiTest {
            shown()
            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).performTextInput("abc")
            waitForIdle()

            onNodeWithTag(MissionControlTags.NEW_AGENT_PROMPT).pressWithShift(Key.Tab)
            awaitFocus(MissionControlTags.NEW_AGENT_PROJECT)

            rig.form().prompt shouldBe "abc"
        }
}
