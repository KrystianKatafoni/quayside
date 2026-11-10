package dev.quayside.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.FakeSessionHistorySettings
import dev.quayside.sessions.api.HistoryLimit
import dev.quayside.sessions.api.SessionHistorySettings
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldPastSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Old sessions from disk in the whole window on fakes (FR-P9, FR-S9, docs/12 §4, AM-312): the badge, the filters, the
 * actions, the row "jeszcze N starszych". Session ids are the UUIDs below; titles name the folder they started in.
 */
@OptIn(ExperimentalTestApi::class)
class PastSessionsWindowTest {
    @TempDir
    lateinit var root: Path

    private val world by lazy { CountersWorld(root) }
    private val inSoft = id(1)
    private val inRoot = id(2)

    private fun id(n: Int) = SessionId("00000000-0000-4000-8000-${n.toString().padStart(12, '0')}")

    private fun file(
        n: Int,
        cwd: String = "",
        title: String? = "old chat $n",
    ) = WorldPastSession(id(n), world.plantDir.resolve(cwd), T0.minusSeconds(n * 60L), title)

    private fun fake(
        vararg past: WorldPastSession,
        settings: FakeSessionHistorySettings = FakeSessionHistorySettings(),
    ) = world.fake(world.session("live", "", SessionState.Working, 0), past = past.toList(), settings = settings)

    @Test
    fun `FR-P9 disk session shows as stopped with the from-disk badge and its place`() =
        runComposeUiTest {
            // Given a group page with a live session and two old sessions, one started in the subfolder "soft"
            showWindow(world.projects, fake(file(1, "soft/api"), file(2)))

            // When the user opens the project
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow(inSoft.value))

            // Then the old session has a badge with text and a reader label, not only a colour
            shows(WindowTags.rowDisk(inSoft.value), "z dysku") shouldBe true
            describedBy(WindowTags.rowDisk(inSoft.value)) shouldBe true
            // and says where it ran, like every session of a group
            shows(WindowTags.rowPlace(inSoft.value), "soft") shouldBe true
            shows(WindowTags.rowPlace(inRoot.value), "folder główny") shouldBe true
            // while a live session has no such badge
            tagged("missionControl.rowDisk.live") shouldBe 0
            shows(WindowTags.missionRow(inSoft.value), "old chat 1") shouldBe true
        }

    @Test
    fun `FR-S9 disk session is under Zakończone and not under Aktywne`() =
        runComposeUiTest {
            // Given the group page with a live and an old session
            val palette = showWindow(world.projects, fake(file(1)))
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow("live"))
            await(WindowTags.missionRow(inSoft.value))

            // When the user picks the filter Zakończone
            runOnIdle { palette.open() }
            await(WindowTags.PALETTE_FIELD).performTextInput("Zakończone")
            await(WindowTags.paletteRow("sessions.filter.finished")).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.missionRow("live")))

            // Then the old session is there
            onNodeWithTag(WindowTags.missionRow(inSoft.value)).assertExists()

            // And under Aktywne it is gone
            runOnIdle { palette.open() }
            await(WindowTags.PALETTE_FIELD).performTextInput("Aktywne")
            await(WindowTags.paletteRow("sessions.filter.active")).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.missionRow(inSoft.value)))
            onNodeWithTag(WindowTags.missionRow("live")).assertExists()
        }

    @Test
    fun `FR-P9 disk sessions are not in the counters of the sidebar`() =
        runComposeUiTest {
            showWindow(world.projects, fake(file(1), file(2)))
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow(inSoft.value))

            // only the live session counts: one working, nothing waiting or failed
            awaitChips(WindowTags.projectRow("project0001"), working = 1)
        }

    @Test
    fun `FR-P9 removing a disk session hides it and offers no attach or respawn`() =
        runComposeUiTest {
            // Given an old session, selected
            val fake = fake(file(1), file(2))
            showWindow(world.projects, fake)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow(inSoft.value)).performClick()
            await(WindowTags.DETAILS_REMOVE)

            // Then the panel offers Usuń and nothing that needs a runtime
            listOf(
                WindowTags.DETAILS_RESPAWN,
                WindowTags.DETAILS_STOP,
                WindowTags.DETAILS_ATTACH,
                WindowTags.DETAILS_REPLY,
                WindowTags.DETAILS_PREVIEW,
            ).forEach { onNodeWithTag(it).assertDoesNotExist() }

            // When the user removes it
            onNodeWithTag(WindowTags.DETAILS_REMOVE).performClick()
            await(WindowTags.CONFIRM_REMOVE_OK).performClick()

            // Then it is gone from the list, hidden for good, and the other old session stays
            waitUntilDoesNotExist(hasTestTag(WindowTags.missionRow(inSoft.value)))
            onNodeWithTag(WindowTags.missionRow(inRoot.value)).assertExists()
            fake.historySettings.settings.value.hidden shouldBe setOf(inSoft)
        }

    @Test
    fun `FR-P9 older row link opens the settings window`() =
        runComposeUiTest {
            // Given 52 old sessions and a limit of 50
            val settings = FakeSessionHistorySettings(SessionHistorySettings(maxPerProject = HistoryLimit.SESSIONS_50))
            showWindow(world.projects, fake(*(1..52).map { file(it) }.toTypedArray(), settings = settings))
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.OLDER_ROW)

            // Then the last row says how many are left out
            shows(WindowTags.OLDER_ROW, "jeszcze 2 starsze") shouldBe true

            // When the user scrolls to the last row and follows the link (50 rows are taller than the window)
            onNodeWithTag(WindowTags.OLDER_CHANGE).performScrollTo().performClick()

            // Then the settings window opens
            await(WindowTags.HISTORY_DIALOG)
        }

    @Test
    fun `FR-P9 older row is only on the group page`() =
        runComposeUiTest {
            val settings = FakeSessionHistorySettings(SessionHistorySettings(maxPerProject = HistoryLimit.SESSIONS_50))
            showWindow(world.projects, fake(*(1..52).map { file(it) }.toTypedArray(), settings = settings))
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.OLDER_ROW)

            await(WindowTags.subfolderRow("project0001", "soft")).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.missionRow("live")))

            onNodeWithTag(WindowTags.OLDER_ROW).assertDoesNotExist()
        }

    @Test
    fun `FR-P9 no older row when nothing is left out`() =
        runComposeUiTest {
            showWindow(world.projects, fake(file(1)))
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow(inSoft.value))

            onNodeWithTag(WindowTags.OLDER_ROW).assertDoesNotExist()
        }
}

/** True when a node under [tag] has a reader label (content description). */
@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.ComposeUiTest.describedBy(tag: String): Boolean =
    onAllNodes(
        hasTestTag(tag) and
            SemanticsMatcher("has a description") {
                it.config
                    .getOrNull(SemanticsProperties.ContentDescription)
                    .orEmpty()
                    .any { text -> text.isNotBlank() }
            },
        useUnmergedTree = true,
    ).fetchSemanticsNodes().isNotEmpty()
