package dev.quayside.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The four counters of the sidebar rows in the whole window on fakes (FR-P6, FR-P7, docs/12 §4, AM-310): every row of a
 * project and of a subfolder shows waiting, working, done and failed, zeros included; the group row sums the whole
 * group. Sessions are named after where they run.
 */
@OptIn(ExperimentalTestApi::class)
class SidebarCountersWindowTest {
    @TempDir
    lateinit var root: Path

    private val world by lazy { CountersWorld(root) }
    private val group get() = WindowTags.projectRow("project0001")
    private val soft get() = WindowTags.subfolderRow("project0001", "soft")
    private val docs get() = WindowTags.subfolderRow("project0001", "docs")
    private val other get() = WindowTags.projectRow("project0002")

    @Test
    fun `FR-P7 every sidebar row shows four counters also for zero`() =
        runComposeUiTest {
            // Given sessions in the project directory, in soft and in docs, of every kind
            val sessions =
                world.fake(
                    world.session("rooted", "", SessionState.Working, 0),
                    world.session("in-soft", "soft/api", SessionState.Working, 1),
                    world.session("fin", "docs", SessionState.Done, 2),
                    world.session("bad", "docs/x", SessionState.Failed, 3),
                    world.session("off", "docs", SessionState.Stopped, 4),
                )

            showWindow(world.projects, sessions)

            // Then the group row sums the whole group, each subfolder row only its own sessions, and the project
            // without sessions shows the four zeros (a stopped session is not counted anywhere)
            awaitCounters(group, waiting = 0, working = 2, done = 1, failed = 1)
            awaitCounters(soft, waiting = 0, working = 1, done = 0, failed = 0)
            awaitCounters(docs, waiting = 0, working = 0, done = 1, failed = 1)
            awaitCounters(other, waiting = 0, working = 0, done = 0, failed = 0)
        }

    @Test
    fun `FR-P6 a session started in soft counts on soft and on the main folder`() =
        runComposeUiTest {
            // Given a group with one session in its directory, and the page of the subfolder "soft" selected
            val fake = world.fake(world.session("rooted", "", SessionState.Working, 0))
            val setup = WindowSetup(world.projects, fake, starts = RecordingStarts(fake))
            showWindowOf(setup)
            awaitCounters(group, waiting = 0, working = 1, done = 0, failed = 0)
            awaitCounters(soft, waiting = 0, working = 0, done = 0, failed = 0)
            await(group).performClick()
            await(soft).performClick()

            // When the user starts a new agent there (cmd N, a prompt, Start)
            pressCmdN(setup)
            await(WindowTags.NEW_AGENT_DIALOG)
            awaitText(WindowTags.NEW_AGENT_SUBFOLDER, "soft")
            awaitFocus(WindowTags.NEW_AGENT_PROMPT)
            typePrompt("Fix the login")
            await(WindowTags.NEW_AGENT_START).performClick()
            awaitGone(WindowTags.NEW_AGENT_DIALOG)

            // Then the new session counts on soft and on the group row, which still counts the old one
            awaitCounters(soft, waiting = 0, working = 1, done = 0, failed = 0)
            awaitCounters(group, waiting = 0, working = 2, done = 0, failed = 0)
            fake.snapshot.value.sessions
                .single { it.id == SessionId("00000001") }
                .cwd shouldBe world.plantDir.resolve("soft")
        }
}
