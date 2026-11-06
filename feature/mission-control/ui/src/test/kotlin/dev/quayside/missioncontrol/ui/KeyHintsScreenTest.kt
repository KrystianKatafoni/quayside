package dev.quayside.missioncontrol.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/** The hints under the session list (AM-816): the key texts come from the handlers, not from the screen. */
@OptIn(ExperimentalTestApi::class)
internal class KeyHintsScreenTest {
    /** Caps unlike any real key, so that a literal in the screen cannot pass for a cap from the handlers. */
    private val caps = SessionKeyCaps("AT1", "RE2", "ST3", "NX4", "PV5", "PR6")
    private val one = SessionId("one")

    private fun sessions() = fakeSessions(worldSession("one", SessionState.AwaitingInput("q")))

    private fun ComposeUiTest.hintsText(): String =
        onAllNodes(inTag(MissionControlTags.KEY_HINTS), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } }
            .joinToString(" ") { it.text }

    @Test
    fun `key hints show caps from handlers`() =
        runComposeUiTest {
            showMc(sessions(), MissionControlHandlers(keyCaps = caps))
            awaitTag(MissionControlTags.KEY_HINTS)

            val shown = hintsText()
            listOf("AT1", "RE2", "ST3", "NX4", "PV5", "PR6").forEach { shown shouldContain it }
            // no literal key of the screen: the real keys are not in the text
            listOf("⏎", "␣", "⌘").forEach { shown shouldNotContain it }
        }

    @Test
    fun `key hints name the actions`() =
        runComposeUiTest {
            showMc(sessions(), MissionControlHandlers(keyCaps = caps))
            awaitTag(MissionControlTags.KEY_HINTS)

            val shown = hintsText().lowercase()
            listOf("ruch", "attach", "odpowiedz", "stop", "podgląd").forEach { shown shouldContain it }
        }

    @Test
    fun `key hints keep the order move attach reply stop preview`() =
        runComposeUiTest {
            showMc(sessions(), MissionControlHandlers(keyCaps = caps))
            awaitTag(MissionControlTags.KEY_HINTS)

            val lefts =
                listOf("NX4", "PV5", "AT1", "RE2", "ST3", "PR6").map { cap ->
                    onAllNodes(hasText(cap) and inTag(MissionControlTags.KEY_HINTS), useUnmergedTree = true)
                        .onFirst()
                        .fetchSemanticsNode()
                        .boundsInRoot.left
                }

            // j then k (next, previous), then the actions
            lefts shouldBe lefts.sorted()
        }

    @Test
    fun `no key hints without key caps`() =
        runComposeUiTest {
            showMc(sessions())
            awaitTag(MissionControlTags.row(one))

            hasTag(MissionControlTags.KEY_HINTS) shouldBe false
        }

    @Test
    fun `key hints sit under the list`() =
        runComposeUiTest {
            showMc(sessions(), MissionControlHandlers(keyCaps = caps))

            val row = awaitTag(MissionControlTags.row(one)).fetchSemanticsNode().boundsInRoot
            val hints = awaitTag(MissionControlTags.KEY_HINTS).fetchSemanticsNode().boundsInRoot

            hints.top shouldBeGreaterThanOrEqualTo row.bottom
        }

    @Test
    fun `key hints are shown also in a global view`() =
        runComposeUiTest {
            val controller = showMc(sessions(), MissionControlHandlers(keyCaps = caps))
            awaitTag(MissionControlTags.KEY_HINTS)

            runOnIdle { controller.onIntent(MissionControlIntent.ShowView(GlobalView.WAITING)) }
            waitForIdle()

            awaitTag(MissionControlTags.KEY_HINTS)
            hintsText() shouldContain "NX4"
        }
}
