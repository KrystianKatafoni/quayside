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
 * The chips of the sidebar in the whole window on fakes (FR-P6, FR-P7, docs/12 §4, AM-310 as reduced): a project row
 * shows a chip for waiting, working and failed sessions, only for non-zero counts, and sums the whole group; a
 * subfolder row has no chips. Sessions are named after where they run.
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
    fun `FR-P7 project row shows chips of non-zero counts and subfolder rows none`() =
        runComposeUiTest {
            // Given sessions in the project directory, in soft and in docs, of every kind
            val sessions =
                world.fake(
                    world.session("rooted", "", SessionState.Working, 0),
                    world.session("in-soft", "soft/api", SessionState.Working, 1),
                    world.session("asks", "docs", SessionState.AwaitingInput("q"), 2),
                    world.session("fin", "docs", SessionState.Done, 3),
                    world.session("bad", "docs/x", SessionState.Failed, 4),
                    world.session("off", "docs", SessionState.Stopped, 5),
                )

            showWindow(world.projects, sessions)

            // Then the group row sums the whole group, with no chip for done or stopped sessions ...
            awaitChips(group, waiting = 1, working = 2, failed = 1)
            // ... the project without sessions has no chips ...
            awaitChips(other)
            // ... and the subfolder rows (shown, and past loading, as the group row has its chips) have none
            await(soft)
            await(docs)
            counters(soft) shouldBe emptyMap()
            counters(docs) shouldBe emptyMap()
        }

    @Test
    fun `FR-P6 a session started in soft counts on the group row`() =
        runComposeUiTest {
            // Given a group with one session in its directory, and the page of the subfolder "soft" selected
            val fake = world.fake(world.session("rooted", "", SessionState.Working, 0))
            val setup = WindowSetup(world.projects, fake, starts = RecordingStarts(fake))
            showWindowOf(setup)
            awaitChips(group, working = 1)
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

            // Then the new session counts on the group row, which still counts the old one (no chips on soft)
            awaitChips(group, working = 2)
            counters(soft) shouldBe emptyMap()
            fake.snapshot.value.sessions
                .single { it.id == SessionId("00000001") }
                .cwd shouldBe world.plantDir.resolve("soft")
        }
}
