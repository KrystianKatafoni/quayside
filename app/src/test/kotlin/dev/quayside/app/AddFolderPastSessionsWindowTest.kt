package dev.quayside.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.HistoryAge
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldPastSession
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Adding a folder that has old sessions (FR-P9, AM-312): the sidebar asks once whether to show them, "Pokaż" opens the
 * project on the filter Zakończone, "Nie teraz" only closes. The first scan of the new project is held back until the
 * test lets it finish, as it is in the real app.
 */
@OptIn(ExperimentalTestApi::class)
class AddFolderPastSessionsWindowTest {
    @TempDir
    lateinit var root: Path

    private val first = SessionId("00000000-0000-4000-8000-000000000001")
    private val second = SessionId("00000000-0000-4000-8000-000000000002")
    private val dir by lazy { Files.createDirectory(root.resolve("legacy")) }

    private fun fake(withFiles: Boolean = true) =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(ProjectId("project0001"), dir)),
                sessions = listOf(WorldSession(SessionId("live"), dir, SessionState.Working, "live", T0)),
                pastSessions =
                    if (withFiles) {
                        listOf(
                            WorldPastSession(first, dir, T0.minusSeconds(60)),
                            WorldPastSession(second, dir, T0.minusSeconds(120)),
                        )
                    } else {
                        emptyList()
                    },
            ),
            startPaused = true,
        )

    private fun androidx.compose.ui.test.ComposeUiTest.addFolder(fake: FakeSessions) {
        await(WindowTags.ADD_BUTTON).performClick()
        await("projects.dialog.add.confirm").performClick()
        waitForIdle()
        runOnIdle { fake.resumeScan() }
    }

    private fun setup(fake: FakeSessions) =
        WindowSetup(FakeProjectsApi(), fake).also {
            it.pastSessions = true
            it.picker.answer = dir
        }

    @Test
    fun `FR-P9 adding a folder with old sessions asks to show them`() =
        runComposeUiTest {
            val fake = fake()
            val setup = setup(fake)
            showWindowOf(setup)

            addFolder(fake)

            // Then the question names the number found
            await(WindowTags.PAST_DIALOG)
            shows(WindowTags.PAST_DIALOG, "Znaleziono 2 stare sesje") shouldBe true
        }

    @Test
    fun `FR-P9 Pokaż opens the project on the finished filter`() =
        runComposeUiTest {
            val fake = fake()
            val setup = setup(fake)
            showWindowOf(setup)
            addFolder(fake)
            await(WindowTags.PAST_SHOW).performClick()

            // Then the old sessions are listed under Zakończone for the new project, and the live one is not
            waitUntilDoesNotExist(hasTestTag(WindowTags.PAST_DIALOG))
            await(WindowTags.missionRow(first.value))
            onNodeWithTag(WindowTags.missionRow(second.value)).assertExists()
            onNodeWithTag(WindowTags.missionRow("live")).assertDoesNotExist()
            fake.historySettings.updates shouldBe 0
        }

    @Test
    fun `FR-P9 Nie teraz only closes the question`() =
        runComposeUiTest {
            val fake = fake()
            val setup = setup(fake)
            showWindowOf(setup)
            addFolder(fake)
            await(WindowTags.PAST_LATER).performClick()

            waitUntilDoesNotExist(hasTestTag(WindowTags.PAST_DIALOG))
            fake.historySettings.updates shouldBe 0
        }

    @Test
    fun `FR-P9 no question when the folder has no old sessions`() =
        runComposeUiTest {
            val fake = fake(withFiles = false)
            val setup = setup(fake)
            showWindowOf(setup)

            addFolder(fake)
            await(WindowTags.projectRow("project0001"))

            onNodeWithTag(WindowTags.PAST_DIALOG).assertDoesNotExist()
        }

    @Test
    fun `FR-P9 add dialog choice is saved as the global setting`() =
        runComposeUiTest {
            val fake = fake()
            val setup = setup(fake)
            showWindowOf(setup)
            await(WindowTags.ADD_BUTTON).performClick()
            await("projects.dialog.add.confirm")

            onNodeWithTag(WindowTags.addHistoryAge(HistoryAge.DAYS_7.name)).performClick()

            waitUntil { fake.historySettings.settings.value.maxAge == HistoryAge.DAYS_7 }
        }
}
