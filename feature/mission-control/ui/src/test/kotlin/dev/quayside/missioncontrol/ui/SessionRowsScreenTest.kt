package dev.quayside.missioncontrol.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Rows of the session list (plan AM-809): status tile, reply and attach buttons of the selected row. */
@OptIn(ExperimentalTestApi::class)
internal class SessionRowsScreenTest {
    private val waiting = SessionId("waiting1")
    private val working = SessionId("working1")
    private val calls = mutableListOf<String>()
    private val handlers =
        MissionControlHandlers(onAttach = { calls += "attach" }, onReply = { calls += "reply" })

    private fun sessions() =
        fakeSessions(
            worldSession("waiting1", SessionState.AwaitingInput("q"), index = 2),
            worldSession("working1", SessionState.Working, index = 1),
        )

    private fun row(id: SessionId): String = MissionControlTags.row(id)

    @Test
    fun `row status tile is announced by the state text`() =
        runComposeUiTest {
            showMc(sessions())
            awaitTag(row(waiting))
            awaitTag(row(working))

            val tile = { id: SessionId, key: String ->
                onAllNodes(hasContentDescription(mcText(key)) and hasAnyAncestor(hasTestTag(row(id))))
                    .fetchSemanticsNodes()
                    .size
            }

            tile(waiting, "state.awaitingInput") shouldBe 1
            tile(working, "state.working") shouldBe 1
            tile(waiting, "state.working") shouldBe 0
        }

    @Test
    fun `row status tile is drawn in the colour of its tone`() =
        runComposeUiTest {
            // Given a waiting (warn) and a working (work) session in the plex theme
            val status = SampleTokens.colors().status
            showMc(sessions())
            awaitTag(row(waiting))

            fun colorsOf(
                id: SessionId,
                key: String,
            ): Set<Int> =
                onNode(hasContentDescription(mcText(key)) and hasAnyAncestor(hasTestTag(row(id)))).argbColors()

            // Then each tile shows its own tone and none of the other status tones
            val warn = colorsOf(waiting, "state.awaitingInput")
            val work = colorsOf(working, "state.working")
            warn shouldContain status.warn.toColor().toArgb()
            warn shouldNotContain status.work.toColor().toArgb()
            work shouldContain status.work.toColor().toArgb()
            work shouldNotContain status.warn.toColor().toArgb()
        }

    @Test
    fun `selected row shows reply and attach that never take focus`() =
        runComposeUiTest {
            // Given a waiting session
            showMc(sessions()).pickSession("waiting1")

            // Then its row has both buttons
            val reply = awaitTag(MissionControlTags.rowReply(waiting))
            val attach = awaitTag(MissionControlTags.rowAttach(waiting))

            // When the user clicks them, then neither takes focus
            reply.performClick()
            reply.assertIsNotFocused()
            attach.performClick()
            attach.assertIsNotFocused()
        }

    @Test
    fun `row reply button replies`() =
        runComposeUiTest {
            showMc(sessions(), handlers).pickSession("waiting1")

            awaitTag(MissionControlTags.rowReply(waiting)).performClick()

            calls shouldBe listOf("reply")
        }

    @Test
    fun `row attach button attaches`() =
        runComposeUiTest {
            showMc(sessions(), handlers).pickSession("waiting1")

            awaitTag(MissionControlTags.rowAttach(waiting)).performClick()

            calls shouldBe listOf("attach")
        }

    @Test
    fun `unselected row has no action buttons`() =
        runComposeUiTest {
            // Given two waiting sessions, one selected
            val other = SessionId("waiting2")
            val two =
                fakeSessions(
                    worldSession("waiting1", SessionState.AwaitingInput("q"), index = 2),
                    worldSession("waiting2", SessionState.AwaitingInput("q"), index = 1),
                )
            showMc(two).pickSession("waiting1")
            awaitTag(MissionControlTags.rowReply(waiting))

            // Then the other row has neither button
            hasTag(MissionControlTags.rowReply(other)) shouldBe false
            hasTag(MissionControlTags.rowAttach(other)) shouldBe false
        }
}
