package dev.quayside.missioncontrol.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

/** What a screen reader gets from the "Nowy agent" dialog (NFR-6): names of the fields, values and the warning. */
@OptIn(ExperimentalTestApi::class)
internal class NewAgentDialogSemanticsTest {
    private val rig = NewAgentRig(RecordingCommands(NewAgentWorld.sessions()))

    private fun ComposeUiTest.shown() {
        showNewAgent(rig)
        openNewAgent(rig, QUAYSIDE.id)
    }

    /** Name and value of the field as the reader announces them: content description plus state description. */
    private fun ComposeUiTest.announced(tag: String): String {
        val config = onNodeWithTag(tag).fetchSemanticsNode().config
        val description = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        val state = config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
        return "$description $state".trim()
    }

    private fun ComposeUiTest.descriptions(tag: String): List<String> =
        onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            .orEmpty()

    @Test
    fun `every field has the name of its label`() =
        runComposeUiTest {
            shown()
            val labels =
                mapOf(
                    MissionControlTags.NEW_AGENT_PROJECT to "newAgent.project",
                    MissionControlTags.NEW_AGENT_PROMPT to "newAgent.prompt",
                    MissionControlTags.NEW_AGENT_NAME to "newAgent.name",
                    MissionControlTags.NEW_AGENT_AGENT to "newAgent.agent",
                    MissionControlTags.NEW_AGENT_MODEL to "newAgent.model",
                    MissionControlTags.NEW_AGENT_PERMISSION to "newAgent.permission",
                )

            labels.forEach { (tag, key) ->
                announced(tag) shouldContain text(key)
                // exactly one name: the label, not the label twice
                descriptions(tag) shouldBe listOf(text(key))
            }
        }

    @Test
    fun `model and mode in the default state are told apart and say their value`() =
        runComposeUiTest {
            shown()

            val model = announced(MissionControlTags.NEW_AGENT_MODEL)
            val mode = announced(MissionControlTags.NEW_AGENT_PERMISSION)

            (model == mode) shouldBe false
            model shouldContain text("newAgent.model.default")
            mode shouldContain text("newAgent.permission.default")
        }

    @Test
    fun `a choice announces its new value after a selection`() =
        runComposeUiTest {
            shown()

            pickOption(MissionControlTags.NEW_AGENT_PERMISSION, 1)

            announced(MissionControlTags.NEW_AGENT_PERMISSION) shouldContain text("newAgent.permission.acceptEdits")
            announced(MissionControlTags.NEW_AGENT_PERMISSION) shouldContain text("newAgent.permission")
        }

    @Test
    fun `the bypass warning is a polite live region`() =
        runComposeUiTest {
            shown()
            pickOption(MissionControlTags.NEW_AGENT_PERMISSION, 3)

            await(MissionControlTags.NEW_AGENT_WARNING)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        }
}
