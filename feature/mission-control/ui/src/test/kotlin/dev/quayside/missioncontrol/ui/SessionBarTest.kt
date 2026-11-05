package dev.quayside.missioncontrol.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The bar above the terminal in terminal mode (plan AM-809, mockup B). */
@OptIn(ExperimentalTestApi::class)
internal class SessionBarTest {
    private val one = SessionId("one")
    private var replies = 0

    private fun androidx.compose.ui.test.ComposeUiTest.showBar(
        sessions: FakeSessions,
        shown: SessionId?,
    ): MissionControlController =
        showMc(sessions) { controller -> SessionBar(controller, shown, onReply = { replies++ }) }

    private fun one(
        state: SessionState,
        name: String = "fix-login",
    ) = fakeSessions(worldSession("one", state, name = name))

    @Test
    fun `bar shows tile name state and age of the session`() =
        runComposeUiTest {
            // Given a session that waits, last active 4 minutes ago
            showBar(one(SessionState.AwaitingInput("q")), one).pickSession("one")

            // Then the bar has the tile announced by the state, the name, the state text and the age
            awaitTag(MissionControlTags.SESSION_BAR)
            onAllNodes(hasContentDescription(mcText("state.awaitingInput"))).fetchSemanticsNodes().size shouldBe 1
            textUnder(MissionControlTags.SESSION_BAR, "fix-login").assertExists()
            textUnder(MissionControlTags.SESSION_BAR, mcText("state.awaitingInput")).assertExists()
            textUnder(MissionControlTags.SESSION_BAR, mcText("age.min", "4")).assertExists()
        }

    @Test
    fun `bar draws nothing for another session`() =
        runComposeUiTest {
            // Given the selected session is "one"
            val sessions = fakeSessions(worldSession("one"), worldSession("two", index = 1))
            val controller = showBar(sessions, SessionId("two"))
            controller.pickSession("one")
            waitForIdle()

            // Then a bar for "two" draws nothing, and neither does a bar for no session
            hasTag(MissionControlTags.SESSION_BAR) shouldBe false
            onAllNodes(hasText("name-two")).fetchSemanticsNodes().size shouldBe 0
        }

    @Test
    fun `bar draws nothing without a session`() =
        runComposeUiTest {
            showBar(one(SessionState.Working), null).pickSession("one")
            waitForIdle()

            hasTag(MissionControlTags.SESSION_BAR) shouldBe false
        }

    @Test
    fun `bar reply calls onReply only when the session waits`() =
        runComposeUiTest {
            // Given a waiting session
            showBar(one(SessionState.AwaitingInput("q")), one).pickSession("one")

            // When the user clicks Odpowiedz, then onReply runs once and the button keeps no focus
            val reply = awaitTag(MissionControlTags.SESSION_BAR_REPLY)
            reply.performClick()
            replies shouldBe 1
            reply.assertIsNotFocused()
        }

    @Test
    fun `bar has no reply for a working session`() =
        runComposeUiTest {
            showBar(one(SessionState.Working), one).pickSession("one")
            awaitTag(MissionControlTags.SESSION_BAR)

            hasTag(MissionControlTags.SESSION_BAR_REPLY) shouldBe false
        }

    @Test
    fun `bar stop runs stop`() =
        runComposeUiTest {
            // Given an active session
            val sessions = one(SessionState.Working)
            showBar(sessions, one).pickSession("one")

            // When the user clicks Stop
            awaitTag(MissionControlTags.SESSION_BAR_STOP).assertIsEnabled().performClick()
            waitForIdle()

            // Then the session was stopped
            sessions.calls shouldBe listOf("stop one")
        }

    @Test
    fun `bar respawn runs respawn`() =
        runComposeUiTest {
            // Given a stopped session: Respawn is active and Stop is not
            val sessions = one(SessionState.Stopped)
            showBar(sessions, one).pickSession("one")
            awaitTag(MissionControlTags.SESSION_BAR_RESPAWN).assertIsEnabled()
            if (hasTag(MissionControlTags.SESSION_BAR_STOP)) {
                awaitTag(MissionControlTags.SESSION_BAR_STOP).assertIsNotEnabled()
            }

            // When the user clicks Respawn
            awaitTag(MissionControlTags.SESSION_BAR_RESPAWN).performClick()
            waitForIdle()

            // Then the session was respawned
            sessions.calls shouldBe listOf("respawn one")
        }

    @Test
    fun `bar respawn is inactive for an active session`() =
        runComposeUiTest {
            showBar(one(SessionState.Working), one).pickSession("one")
            awaitTag(MissionControlTags.SESSION_BAR)

            if (hasTag(MissionControlTags.SESSION_BAR_RESPAWN)) {
                awaitTag(MissionControlTags.SESSION_BAR_RESPAWN).assertIsNotEnabled()
            }
        }

    @Test
    fun `bar stop is announced Zatrzymaj sesję`() =
        runComposeUiTest {
            showBar(one(SessionState.Working), one).pickSession("one")
            awaitTag(MissionControlTags.SESSION_BAR)

            onNodeWithContentDescription("Zatrzymaj sesję").assertExists()
        }
}
