package dev.quayside.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.ResumeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldPastSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Resuming an old session from disk in the whole window on fakes (FR-P9, docs/12 §4, AM-313): the button, the live
 * session in its place, the failure, the palette. The first 8 characters of the UUIDs differ: they are the live ids.
 */
@OptIn(ExperimentalTestApi::class)
class ResumeWindowTest {
    @TempDir
    lateinit var root: Path

    private val world by lazy { CountersWorld(root) }
    private val disk = SessionId("aaaa0001-0000-4000-8000-000000000001")
    private val other = SessionId("bbbb0002-0000-4000-8000-000000000002")
    private val live = SessionId("aaaa0001")
    private val title = "secret chat title"

    private fun fake() =
        world.fake(
            world.session("live", "", SessionState.Working, 0),
            world.session("stopped", "", SessionState.Stopped, 1),
            past =
                listOf(
                    WorldPastSession(disk, world.plantDir.resolve("soft"), T0.minusSeconds(60), title),
                    WorldPastSession(other, world.plantDir, T0.minusSeconds(120), "another chat"),
                ),
        )

    private fun ComposeUiTest.openDisk() {
        await(WindowTags.projectRow("project0001")).performClick()
        await(WindowTags.missionRow(disk.value)).performClick()
        await(WindowTags.DETAILS_RESUME)
    }

    private fun ComposeUiTest.textUnder(
        tag: String,
        text: String,
    ): Boolean =
        onAllNodes(
            hasText(text, substring = true) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `FR-P9 resuming a disk session shows it live and selected`() =
        runComposeUiTest {
            // Given an old session of the subfolder "soft", selected
            val fake = fake()
            showWindow(world.projects, fake)
            openDisk()

            // When the user clicks Wznów
            onNodeWithTag(WindowTags.DETAILS_RESUME).performClick()

            // Then the live session is in the list and selected: the panel offers Stop, not Wznów
            await(WindowTags.missionRow(live.value))
            await(WindowTags.DETAILS_STOP)
            onNodeWithTag(WindowTags.DETAILS_RESUME).assertDoesNotExist()
            // and it is the old conversation: same name, still in the subfolder
            textUnder(WindowTags.missionRow(live.value), title) shouldBe true
            textUnder(WindowTags.rowPlace(live.value), "soft") shouldBe true
            fake.calls shouldBe listOf("resume ${disk.value}")
        }

    @Test
    fun `FR-P9 resumed session is not listed twice`() =
        runComposeUiTest {
            showWindow(world.projects, fake())
            openDisk()

            onNodeWithTag(WindowTags.DETAILS_RESUME).performClick()

            await(WindowTags.missionRow(live.value))
            waitUntilDoesNotExist(hasTestTag(WindowTags.missionRow(disk.value)))
            onNodeWithTag(WindowTags.rowDisk(disk.value)).assertDoesNotExist()
            // the other old session is untouched
            onNodeWithTag(WindowTags.missionRow(other.value)).assertExists()
            tagged("missionControl.row.${live.value}") shouldBe 1
        }

    @Test
    fun `FR-P9 resume failure shows the reason and keeps the row`() =
        runComposeUiTest {
            // Given a session file that is gone since the scan
            val fake = fake()
            fake.failNext(SessionError.ResumeUnavailable(disk, ResumeProblem.FILE_MISSING))
            showWindow(world.projects, fake)
            openDisk()

            // When the user clicks Wznów
            onNodeWithTag(WindowTags.DETAILS_RESUME).performClick()

            // Then a fixed reason shows, without the title or a path, and the old row stays selected
            await(WindowTags.DETAILS_FAILURE)
            textUnder(WindowTags.DETAILS_FAILURE, title) shouldBe false
            textUnder(WindowTags.DETAILS_FAILURE, root.toString()) shouldBe false
            onNodeWithTag(WindowTags.missionRow(disk.value)).assertExists()
            onNodeWithTag(WindowTags.DETAILS_RESUME).assertExists()
            fake.snapshot.value.sessions
                .none { it.id == live } shouldBe true
        }

    @Test
    fun `FR-P9 a copy leaves the old row and says so`() =
        runComposeUiTest {
            val fake = fake()
            fake.opensElsewhere(disk)
            showWindow(world.projects, fake)
            openDisk()

            onNodeWithTag(WindowTags.DETAILS_RESUME).performClick()

            await(WindowTags.DETAILS_NOTICE)
            onNodeWithTag(WindowTags.missionRow(disk.value)).assertExists()
            onNodeWithTag(WindowTags.missionRow(live.value)).assertDoesNotExist()
        }

    @Test
    fun `FR-P9 palette offers resume only for a disk session`() =
        runComposeUiTest {
            // Given a working, a stopped and an old session
            val palette = showWindow(world.projects, fake())
            await(WindowTags.projectRow("project0001")).performClick()

            // When the old session is selected and the palette opens
            await(WindowTags.missionRow(disk.value)).performClick()
            await(WindowTags.DETAILS_RESUME)
            runOnIdle { palette.open() }

            // Then Wznów sesję is offered and Respawn is not
            await(WindowTags.paletteRow("sessions.resume"))
            onNodeWithTag(WindowTags.paletteRow("sessions.respawn")).assertDoesNotExist()

            // When the stopped session is selected and the palette opens again
            runOnIdle { palette.close() }
            await(WindowTags.missionRow("stopped")).performClick()
            await(WindowTags.DETAILS_RESPAWN)
            runOnIdle { palette.open() }

            // Then Respawn is offered and Wznów sesję is not
            await(WindowTags.paletteRow("sessions.respawn"))
            onNodeWithTag(WindowTags.paletteRow("sessions.resume")).assertDoesNotExist()
        }

    @Test
    fun `FR-P9 palette command resumes the selected disk session`() =
        runComposeUiTest {
            val fake = fake()
            val palette = showWindow(world.projects, fake)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.missionRow(disk.value)).performClick()
            await(WindowTags.DETAILS_RESUME)
            runOnIdle { palette.open() }

            await(WindowTags.paletteRow("sessions.resume")).performClick()

            waitUntil { fake.calls == listOf("resume ${disk.value}") }
            await(WindowTags.missionRow(live.value))
            fake.calls.size shouldBe 1
        }
}
