package dev.quayside.projects.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.DirectoryReport
import dev.quayside.projects.api.GitInfo
import dev.quayside.projects.api.HooksPresence
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.HistoryAge
import dev.quayside.sessions.api.HistoryLimit
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The question after adding a folder and the choices of the add dialog, drawn without a controller (FR-P9). */
@OptIn(ExperimentalTestApi::class)
class PastSessionsDialogScreenTest {
    private val events = mutableListOf<SidebarEvent>()
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Plant")

    private fun ComposeUiTest.show(dialog: Dialog) {
        events.clear()
        setContent { AmTestTheme { ProjectsDialog(dialog) { events += it } } }
        waitUntil {
            onAllNodesWithTagCount(ProjectsTags.PAST_DIALOG) + onAllNodesWithTagCount(ProjectsTags.CONFIRM_ADD) >
                0
        }
    }

    private fun ComposeUiTest.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    private fun ComposeUiTest.textUnder(
        tag: String,
        text: String,
    ): Boolean =
        onAllNodes(
            hasText(text, substring = true) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    private val report = DirectoryReport(A_PROJECT_DIR, GitInfo("main"), true, 1, HooksPresence.NONE, null)

    @Test
    fun `the question names the number and, for a project with subfolders, the split`() =
        runComposeUiTest {
            show(Dialog.PastSessions(project, PastCounts(total = 5, inSubfolders = 3, inRoot = 2)))

            textUnder(ProjectsTags.PAST_DIALOG, "Znaleziono 5 starych sesji") shouldBe true
            textUnder(ProjectsTags.PAST_DIALOG, "podfolder: 3, folder główny: 2") shouldBe true
        }

    @Test
    fun `the title follows the Polish plural of the count`() =
        runComposeUiTest {
            listOf(
                1 to "Znaleziono 1 starą sesję",
                3 to "Znaleziono 3 stare sesje",
                12 to "Znaleziono 12 starych sesji",
            ).forEach { (count, text) ->
                show(Dialog.PastSessions(project, PastCounts(count, null, null)))
                textUnder(ProjectsTags.PAST_DIALOG, text) shouldBe true
            }
        }

    @Test
    fun `a project without subfolders gets no split`() =
        runComposeUiTest {
            show(Dialog.PastSessions(project, PastCounts(total = 5, inSubfolders = null, inRoot = null)))

            textUnder(ProjectsTags.PAST_DIALOG, "Znaleziono 5 starych sesji") shouldBe true
            textUnder(ProjectsTags.PAST_DIALOG, "podfolder") shouldBe false
        }

    @Test
    fun `the answers send their events and the start focus is on Nie teraz`() =
        runComposeUiTest {
            show(Dialog.PastSessions(project, PastCounts(1, null, null)))
            waitUntil {
                onAllNodes(
                    hasTestTag(ProjectsTags.PAST_LATER) and isFocused(),
                ).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithTag(ProjectsTags.PAST_SHOW).performClick()
            onNodeWithTag(ProjectsTags.PAST_LATER).performClick()

            events shouldBe listOf(SidebarEvent.ShowPastSessions, SidebarEvent.Dismiss)
        }

    @Test
    fun `the add dialog shows the saved choices and a click sends the new one`() =
        runComposeUiTest {
            show(Dialog.ConfirmAdd(report, history = HistoryChoice(HistoryAge.DAYS_30, HistoryLimit.SESSIONS_200)))

            onNodeWithTag(ProjectsTags.historyAge("DAYS_30")).assertIsSelected()
            onNodeWithTag(ProjectsTags.historyAge("DAYS_7")).assertIsNotSelected()
            onNodeWithTag(ProjectsTags.historyLimit("SESSIONS_200")).assertIsSelected()

            onNodeWithTag(ProjectsTags.historyAge("DAYS_7")).performClick()
            onNodeWithTag(ProjectsTags.historyLimit("SESSIONS_50")).performClick()

            events shouldBe
                listOf(
                    SidebarEvent.ChooseHistoryAge(HistoryAge.DAYS_7),
                    SidebarEvent.ChooseHistoryLimit(HistoryLimit.SESSIONS_50),
                )
        }

    @Test
    fun `the add dialog without choices draws none`() =
        runComposeUiTest {
            show(Dialog.ConfirmAdd(report))

            onNodeWithTag(ProjectsTags.historyAge("DAYS_30")).assertDoesNotExist()
            onNodeWithTag(ProjectsTags.historyLimit("SESSIONS_200")).assertDoesNotExist()
        }
}
