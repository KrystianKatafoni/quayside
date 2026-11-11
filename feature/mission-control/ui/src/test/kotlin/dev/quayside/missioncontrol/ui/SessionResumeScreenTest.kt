package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.ResumeProblem
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldPastSession
import dev.quayside.sessions.api.WorldProject
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/** "Wznów" in the details panel of a disk session (FR-P9, AM-313): the button, the busy text, the result. */
@OptIn(ExperimentalTestApi::class)
internal class SessionResumeScreenTest {
    private val uuid = SessionId("aaaa0001-0000-4000-8000-000000000001")
    private val live = SessionId("aaaa0001")
    private val secretTitle = "secret-chat-title"

    private fun sessions(): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(MC_PROJECT.id, MC_PROJECT.dir)),
                pastSessions = listOf(WorldPastSession(uuid, MC_PROJECT.dir, T0.minusSeconds(60), secretTitle)),
            ),
            TestClock(T0),
        )

    private fun ComposeUiTest.show(
        sessions: FakeSessions,
        commands: SessionCommands = sessions,
    ) {
        setContent {
            AmTestTheme {
                val scope = rememberCoroutineScope()
                val controller =
                    remember {
                        MissionControlController(
                            SessionsApi(monitor = sessions, commands = commands),
                            FakeProjectsApi(initial = listOf(MC_PROJECT)),
                            MutableStateFlow<ProjectFocus?>(ProjectFocus(MC_PROJECT.id)),
                            TestClock(T0.plusSeconds(60)),
                            scope,
                            NoOpLogger,
                            tick = 1.hours,
                        )
                    }
                MissionControl(controller)
            }
        }
    }

    private fun ComposeUiTest.openDisk() {
        awaitTag(MissionControlTags.row(uuid)).performClick()
        awaitTag(MissionControlTags.details("resume"))
    }

    @Test
    fun `a disk session shows resume and remove only`() =
        runComposeUiTest {
            show(sessions())

            openDisk()

            awaitTag(MissionControlTags.details("resume")).assertIsEnabled()
            awaitTag(MissionControlTags.details("remove")).assertIsEnabled()
            listOf("respawn", "stop", "attach", "reply", "preview").forEach { control ->
                hasTag(MissionControlTags.details(control)) shouldBe false
            }
            textUnder(MissionControlTags.details("resume"), "Wznów").assertExists()
        }

    @Test
    fun `clicking resume shows the live session and the disk row is gone`() =
        runComposeUiTest {
            show(sessions())
            openDisk()

            awaitTag(MissionControlTags.details("resume")).performClick()

            awaitTag(MissionControlTags.row(live))
            waitUntilDoesNotExist(hasTestTag(MissionControlTags.row(uuid)))
            awaitTag(MissionControlTags.details("stop"))
            hasTag(MissionControlTags.details("resume")) shouldBe false
        }

    @Test
    fun `while resume runs the panel says Wznawianie and offers nothing else`() =
        runComposeUiTest {
            val fake = sessions()
            val gated = GatedCommands(fake)
            show(fake, gated)
            openDisk()

            awaitTag(MissionControlTags.details("resume")).performClick()

            textUnder(MissionControlTags.DETAILS_BUSY, mcText("action.resume.busy")).assertExists()
            mcText("action.resume.busy") shouldBe "Wznawianie…"
            gated.started shouldBe listOf("resume ${uuid.value}")
            // the button is either not shown or shown disabled; both mean "cannot be used now"
            if (hasTag(MissionControlTags.details("remove"))) {
                onNodeWithTag(MissionControlTags.details("remove")).assertIsNotEnabled()
            }
        }

    @Test
    fun `a failed resume shows its reason without the title and keeps the row`() =
        runComposeUiTest {
            val fake = sessions()
            fake.failNext(SessionError.ResumeUnavailable(uuid, ResumeProblem.DIRECTORY_MISSING))
            show(fake)
            openDisk()

            awaitTag(MissionControlTags.details("resume")).performClick()

            val reason = mcText("action.reason.resume.DIRECTORY_MISSING")
            textUnder(MissionControlTags.DETAILS_FAILURE, reason).assertExists()
            reason shouldNotContain secretTitle
            hasTag(MissionControlTags.row(uuid)) shouldBe true
            awaitTag(MissionControlTags.details("resume")).assertIsEnabled()
        }

    @Test
    fun `a resumed copy shows the copy notice`() =
        runComposeUiTest {
            val fake = sessions()
            fake.opensElsewhere(uuid)
            show(fake)
            openDisk()

            awaitTag(MissionControlTags.details("resume")).performClick()

            awaitTag(MissionControlTags.DETAILS_NOTICE)
            textUnder(MissionControlTags.DETAILS_NOTICE, mcText("action.resume.copyNotice")).assertExists()
            hasTag(MissionControlTags.row(uuid)) shouldBe true
        }
}
