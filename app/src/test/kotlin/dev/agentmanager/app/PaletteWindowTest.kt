package dev.agentmanager.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.agentmanager.projects.api.A_PROJECT_DIR
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.aProject
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** User scenarios of the palette in the whole window on fakes (FR-K1, FR-P6; AM-408). */
@OptIn(ExperimentalTestApi::class)
class PaletteWindowTest {
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Agent Manager")
    private val other = aProject(id = "project0002", dir = A_PROJECT_DIR.resolveSibling("other"), displayName = "Other")

    @Test
    fun `FR-K1 palette goes to another project`() =
        runComposeUiTest {
            // Given two projects and the open palette
            val projects = FakeProjectsApi(initial = listOf(agent, other))
            val palette = showWindow(projects)
            await(WindowTags.projectRow(other.id.value))
            runOnIdle { palette.open() }

            // When the user searches for "other" and picks "Go to project: Other"
            await(WindowTags.PALETTE_FIELD).performTextInput("other")
            await(WindowTags.paletteRow("projects.goto.project0002")).performClick()

            // Then the palette is gone, the project is selected and Mission Control shows it
            waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
            await(WindowTags.projectRow("project0002")).assertIsSelected()
            waitUntil {
                onAllNodes(hasText("Other") and hasAnyAncestor(hasTestTag(WindowTags.MODE_BAR)))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }

    @Test
    fun `FR-K1 FR-P6 rename from the palette saves the new name`() =
        runComposeUiTest {
            // Given a selected project and the open palette
            val projects = FakeProjectsApi(initial = listOf(agent))
            val palette = showWindow(projects)
            await(WindowTags.projectRow("project0001")).performClick()
            runOnIdle { palette.open() }

            // When the user finds "Rename project…" by its keyword and picks it
            await(WindowTags.PALETTE_FIELD).performTextInput("rename")
            await(WindowTags.paletteRow("projects.rename")).performClick()

            // Then the rename dialog opens with the current name; saving shows the new name
            await(WindowTags.RENAME_FIELD).assertTextEquals("Agent Manager").performTextReplacement("Moja aplikacja")
            await(WindowTags.RENAME_SAVE).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.RENAME_DIALOG))
            onNodeWithTag(WindowTags.PALETTE).assertDoesNotExist()
            await(WindowTags.projectRow("project0001")).assertTextContains("Moja aplikacja", substring = true)
            projects.calls shouldBe listOf("rename project0001")
        }

    @Test
    fun `FR-K1 palette lists the add project command without projects`() =
        runComposeUiTest {
            // Given no projects
            val palette = showWindow(FakeProjectsApi())

            // When the palette opens
            runOnIdle { palette.open() }

            // Then "Add project…" is offered, and nothing that needs a selection is listed
            await(WindowTags.paletteRow("projects.add")).assertTextContains("Dodaj projekt…", substring = true)
            onNodeWithTag(WindowTags.paletteRow("projects.rename")).assertDoesNotExist()
            onNodeWithTag(WindowTags.paletteRow("projects.remove")).assertDoesNotExist()
        }
}
