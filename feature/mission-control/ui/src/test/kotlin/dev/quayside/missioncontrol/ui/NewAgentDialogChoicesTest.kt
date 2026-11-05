package dev.quayside.missioncontrol.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmDropdownTags
import dev.quayside.core.domain.ProjectId
import dev.quayside.missioncontrol.ui.NewAgentWorld.OTHER
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import dev.quayside.sessions.api.SessionPermissionMode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/** The dropdown choices of the "Nowy agent" dialog (FR-S3, AM-817): project, model and permission mode. */
@OptIn(ExperimentalTestApi::class)
internal class NewAgentDialogChoicesTest {
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
    fun `a start with no project preselected asks to pick one`() =
        runComposeUiTest {
            shown(project = null)

            tagShows(MissionControlTags.NEW_AGENT_PROJECT, text("newAgent.project.none"))
            openList(MissionControlTags.NEW_AGENT_PROJECT)
            rig.form().project shouldBe null
            onNodeWithTag(AmDropdownTags.option(0)).performClick()
            awaitListGone()

            rig.form().project shouldBe QUAYSIDE.id
        }

    @Test
    fun `the project is picked from the list`() =
        runComposeUiTest {
            shown()

            // When the user clicks the field the list shows both projects, the current one marked
            openList(MissionControlTags.NEW_AGENT_PROJECT)
            onNode(hasTestTag(AmDropdownTags.option(0)) and hasText("Quayside", substring = true)).assertExists()
            onNode(hasTestTag(AmDropdownTags.option(1)) and hasText("Other", substring = true)).assertExists()
            onNodeWithTag(AmDropdownTags.option(2)).assertDoesNotExist()
            onNodeWithTag(AmDropdownTags.option(0)).assertIsSelected()

            // And a click on the second one picks it
            onNodeWithTag(AmDropdownTags.option(1)).performClick()
            awaitListGone()
            rig.form().project shouldBe OTHER.id
            tagShows(MissionControlTags.NEW_AGENT_PROJECT, "Other")

            // And Enter, Up and Enter on the focused field pick the first one again
            val field = onNodeWithTag(MissionControlTags.NEW_AGENT_PROJECT)
            field.press(Key.Enter)
            awaitListOpen()
            pressKeyOn(MissionControlTags.NEW_AGENT_PROJECT, Key.DirectionUp)
            pressKeyOn(MissionControlTags.NEW_AGENT_PROJECT, Key.Enter)
            awaitListGone()
            rig.form().project shouldBe QUAYSIDE.id
        }

    @Test
    fun `the model choice offers all aliases`() =
        runComposeUiTest {
            shown()
            val expected = ModelChoice.entries.map { it.alias ?: text("newAgent.model.default") }

            openList(MissionControlTags.NEW_AGENT_MODEL)

            expected.forEachIndexed { index, label ->
                onNode(hasTestTag(AmDropdownTags.option(index)) and hasText(label, substring = true)).assertExists()
            }
            onNodeWithTag(AmDropdownTags.option(expected.size)).assertDoesNotExist()
            onNodeWithTag(AmDropdownTags.option(0)).assertIsSelected()

            onNodeWithTag(AmDropdownTags.option(ModelChoice.entries.indexOf(ModelChoice.HAIKU))).performClick()
            awaitListGone()
            rig.form().model shouldBe ModelChoice.HAIKU
            tagShows(MissionControlTags.NEW_AGENT_MODEL, "haiku")
        }

    /** Decision of 2026-11-05: the list shows the modes in this order and the dangerous one is the last. */
    @Test
    fun `the permission list offers the four modes in order`() =
        runComposeUiTest {
            shown()
            val expected =
                listOf(
                    null to "Domyślny",
                    SessionPermissionMode.ACCEPT_EDITS to "Akceptuj edycje",
                    SessionPermissionMode.PLAN to "Tylko plan",
                    SessionPermissionMode.BYPASS_PERMISSIONS to "Pomiń zgody",
                )

            openList(MissionControlTags.NEW_AGENT_PERMISSION)

            expected.forEachIndexed { index, (_, label) ->
                onNode(hasTestTag(AmDropdownTags.option(index)) and hasText(label, substring = true)).assertExists()
            }
            onNodeWithTag(AmDropdownTags.option(expected.size)).assertDoesNotExist()
            onNodeWithTag(AmDropdownTags.option(0)).assertIsSelected()
        }

    @Test
    fun `each permission mode is picked from the list and shown`() =
        runComposeUiTest {
            shown()
            val expected =
                listOf(
                    SessionPermissionMode.ACCEPT_EDITS to "Akceptuj edycje",
                    SessionPermissionMode.PLAN to "Tylko plan",
                    SessionPermissionMode.BYPASS_PERMISSIONS to "Pomiń zgody",
                    null to "Domyślny",
                )

            expected.forEachIndexed { step, (mode, label) ->
                // the list of the previous pick is gone; options 1, 2, 3 then 0 of the same four
                pickOption(MissionControlTags.NEW_AGENT_PERMISSION, (step + 1) % expected.size)
                rig.form().permission shouldBe mode
                tagShows(MissionControlTags.NEW_AGENT_PERMISSION, label)
            }
        }

    /**
     * Decision of 2026-11-05: on a closed field every arrow only opens the list (as in macOS), so
     * "Pomiń zgody" is never one key from the default mode.
     */
    @Test
    fun `from the default mode one key never selects bypass permissions`() =
        runComposeUiTest {
            shown()
            val field = onNodeWithTag(MissionControlTags.NEW_AGENT_PERMISSION)
            field.focus()
            waitForIdle()

            // Every arrow only opens the list; closing it leaves the mode as it was
            listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown).forEach { key ->
                field.press(key)
                awaitListOpen()
                pressKeyOn(MissionControlTags.NEW_AGENT_PERMISSION, Key.Escape)
                awaitListGone()
                rig.form().permission shouldBe null
            }

            // Enter opens the list and Enter on the focused (default) mode keeps it
            field.press(Key.Enter)
            awaitListOpen()
            pressKeyOn(MissionControlTags.NEW_AGENT_PERMISSION, Key.Enter)
            awaitListGone()
            rig.form().permission shouldBe null
            onNodeWithTag(MissionControlTags.NEW_AGENT_WARNING).assertDoesNotExist()
        }

    @Test
    fun `bypass needs three steps down and Enter`() =
        runComposeUiTest {
            shown()
            val field = onNodeWithTag(MissionControlTags.NEW_AGENT_PERMISSION)
            field.focus()
            waitForIdle()
            field.press(Key.Enter)
            awaitListOpen()

            // Two steps are not enough
            repeat(2) { pressKeyOn(MissionControlTags.NEW_AGENT_PERMISSION, Key.DirectionDown) }
            rig.form().permission shouldBe null
            pressKeyOn(MissionControlTags.NEW_AGENT_PERMISSION, Key.DirectionDown)
            pressKeyOn(MissionControlTags.NEW_AGENT_PERMISSION, Key.Enter)
            awaitListGone()

            // Then the mode is "Pomiń zgody" and the warning shows
            rig.form().permission shouldBe SessionPermissionMode.BYPASS_PERMISSIONS
            onNodeWithTag(MissionControlTags.NEW_AGENT_WARNING).assertTextEquals(text("newAgent.bypassWarning"))

            // And the list stops at its ends: Down on the last mode keeps it
            field.press(Key.Enter)
            awaitListOpen()
            pressKeyOn(MissionControlTags.NEW_AGENT_PERMISSION, Key.DirectionDown)
            pressKeyOn(MissionControlTags.NEW_AGENT_PERMISSION, Key.Enter)
            awaitListGone()
            rig.form().permission shouldBe SessionPermissionMode.BYPASS_PERMISSIONS
        }

    @Test
    fun `skipping permissions shows a warning that goes away with another mode`() =
        runComposeUiTest {
            shown()
            pickOption(MissionControlTags.NEW_AGENT_PERMISSION, 2)
            onNodeWithTag(MissionControlTags.NEW_AGENT_WARNING).assertDoesNotExist()

            pickOption(MissionControlTags.NEW_AGENT_PERMISSION, 3)

            onNodeWithTag(MissionControlTags.NEW_AGENT_WARNING).assertTextEquals(text("newAgent.bypassWarning"))
            pickOption(MissionControlTags.NEW_AGENT_PERMISSION, 0)
            onNodeWithTag(MissionControlTags.NEW_AGENT_WARNING).assertDoesNotExist()
        }

    @Test
    fun `Escape in an open list keeps the dialog`() =
        runComposeUiTest {
            // Given the dialog with a typed prompt and the open list of the model
            shown()
            edit { it.copy(prompt = "keep me") }
            val field = onNodeWithTag(MissionControlTags.NEW_AGENT_MODEL)
            field.focus()
            waitForIdle()
            field.press(Key.Enter)
            awaitListOpen()

            // When the user presses Escape
            pressKeyOn(MissionControlTags.NEW_AGENT_MODEL, Key.Escape)
            awaitListGone()

            // Then only the list is gone: the dialog and the form stay, the focus is on the field
            onNodeWithTag(MissionControlTags.NEW_AGENT_DIALOG).assertExists()
            rig.form().prompt shouldBe "keep me"
            rig.form().model shouldBe ModelChoice.DEFAULT
            rig.state() shouldNotBe null
            awaitFocus(MissionControlTags.NEW_AGENT_MODEL)

            // And the next Escape cancels the dialog
            pressKeyOn(MissionControlTags.NEW_AGENT_MODEL, Key.Escape)
            awaitGone(MissionControlTags.NEW_AGENT_DIALOG)
        }

    @Test
    fun `a form with the DEFAULT mode shows the default mode as selected`() =
        runComposeUiTest {
            shown()
            edit { it.copy(permission = SessionPermissionMode.DEFAULT) }
            waitForIdle()

            tagShows(MissionControlTags.NEW_AGENT_PERMISSION, text("newAgent.permission.default"))
            openList(MissionControlTags.NEW_AGENT_PERMISSION)
            onNodeWithTag(AmDropdownTags.option(0)).assertIsSelected()
        }
}
