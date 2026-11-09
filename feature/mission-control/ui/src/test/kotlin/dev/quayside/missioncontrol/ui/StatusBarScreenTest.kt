package dev.quayside.missioncontrol.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The window status bar on fakes (AM-816 decision 2): mode badge, counters, trailing slot, freshness and version. */
@OptIn(ExperimentalTestApi::class)
internal class StatusBarScreenTest {
    private val colors = SampleTokens.colors()

    private fun snapshot() =
        freshSnapshot(
            aSession(id = "q1", project = MC_PROJECT.id, state = SessionState.AwaitingInput("q")),
            aSession(id = "p1", project = MC_OTHER.id, state = SessionState.AwaitingPermission("Bash")),
            aSession(id = "w1", project = MC_OTHER.id, state = SessionState.Working),
            aSession(id = "d1", project = MC_PROJECT.id, state = SessionState.Done),
        )

    private fun ComposeUiTest.showBar(
        snapshot: SessionsSnapshot = snapshot(),
        terminal: Boolean = false,
        supervisor: String? = null,
    ): MissionControlController =
        showScripted(snapshot) { controller ->
            StatusBar(controller, terminal, supervisor, trailing = { AmText("TRAILING") })
        }

    /** Waits until the bar shows [text] (the controller state arrives after the first frame). */
    private fun ComposeUiTest.barText(text: String): SemanticsNodeInteraction {
        waitUntil {
            onAllNodes(
                hasText(text, substring = true) and inTag(MissionControlTags.MODE_BAR),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        return textUnder(MissionControlTags.MODE_BAR, text)
    }

    private fun ComposeUiTest.barHas(text: String): Boolean =
        onAllNodes(
            hasText(text, substring = true) and inTag(MissionControlTags.MODE_BAR),
        ).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `status bar shows the normal badge outside terminal mode`() =
        runComposeUiTest {
            showBar(terminal = false)

            barText("NORMAL")
            barHas("TERMINAL") shouldBe false
        }

    @Test
    fun `status bar shows terminal badge in terminal`() =
        runComposeUiTest {
            showBar(terminal = true)

            barText("TERMINAL")
            barHas("NORMAL") shouldBe false
        }

    @Test
    fun `status bar badge is accent ink on accent and the bar stands on the panel`() =
        runComposeUiTest {
            showBar()
            barText("NORMAL")

            textUnder(MissionControlTags.MODE_BAR, "NORMAL").argbColors() shouldContain colors.content.accent.argb()
            awaitTag(MissionControlTags.MODE_BAR).argbColors() shouldContain colors.surfaces.panel.argb()
        }

    @Test
    fun `status bar shows project and the counters of every project`() =
        runComposeUiTest {
            showBar()

            barText("Quayside")
            barText("2 czekają")
            barText("1 pracuje")
        }

    @Test
    fun `status bar draws waiting in warn and working in work`() =
        runComposeUiTest {
            showBar()

            barText("2 czekają").argbColors() shouldContain colors.status.warn.argb()
            barText("1 pracuje").argbColors() shouldContain colors.status.work.argb()
        }

    @Test
    fun `status bar shows the project name only on a project page`() =
        runComposeUiTest {
            val controller = showBar()
            barText("Quayside")

            runOnIdle { controller.onIntent(MissionControlIntent.ShowView(GlobalView.WORKING)) }
            waitForIdle()

            barHas("Quayside") shouldBe false
        }

    @Test
    fun `status bar uses the singular for one`() =
        runComposeUiTest {
            showBar(
                freshSnapshot(
                    aSession(id = "q1", project = MC_PROJECT.id, state = SessionState.AwaitingInput("q")),
                    aSession(id = "w1", project = MC_PROJECT.id, state = SessionState.Working),
                ),
            )

            barText("1 czeka")
            barText("1 pracuje")
            barHas("czekają") shouldBe false
        }

    @Test
    fun `status bar uses many for five`() =
        runComposeUiTest {
            val asking = SessionState.AwaitingInput("q")
            val sessions =
                (1..5).map { aSession(id = "q$it", project = MC_PROJECT.id, state = asking) } +
                    (1..5).map { aSession(id = "w$it", project = MC_PROJECT.id, state = SessionState.Working) }
            showBar(freshSnapshot(*sessions.toTypedArray()))

            barText("5 czeka")
            barText("5 pracuje")
            barHas("czekają") shouldBe false
            barHas("pracują") shouldBe false
        }

    @Test
    fun `status bar uses many for zero`() =
        runComposeUiTest {
            showBar(freshSnapshot())

            barText("0 czeka")
            barText("0 pracuje")
            barHas("czekają") shouldBe false
            barHas("pracują") shouldBe false
        }

    @Test
    fun `status bar shows supervisor version`() =
        runComposeUiTest {
            showBar(supervisor = "2.1.150")

            barText("supervisor 2.1.150")
            // the version replaces the freshness text
            barHas(mcText("fresh.live")) shouldBe false
        }

    @Test
    fun `status bar shows freshness without version`() =
        runComposeUiTest {
            showBar(supervisor = null)

            barText(mcText("fresh.live"))
            barHas("supervisor") shouldBe false
        }

    @Test
    fun `status bar puts trailing between the counters and the supervisor`() =
        runComposeUiTest {
            showBar(supervisor = "2.1.150")

            val left = { text: String -> barText(text).fetchSemanticsNode().boundsInRoot.left }
            left("Quayside") shouldBeLessThan left("2 czekają")
            left("2 czekają") shouldBeLessThan left("1 pracuje")
            left("1 pracuje") shouldBeLessThan left("TRAILING")
            left("TRAILING") shouldBeLessThan left("supervisor 2.1.150")
        }

    @Test
    fun `status bar dot is ok when fresh`() =
        runComposeUiTest {
            showBar(supervisor = "2.1.150")

            awaitTag(MissionControlTags.STATUS_DOT).argbColors() shouldContain colors.status.ok.argb()
        }

    @Test
    fun `status bar dot is err when stale`() =
        runComposeUiTest {
            showBar(
                SessionsSnapshot(emptyList(), SnapshotStatus.Stale(T0, RuntimeProblem.TIMEOUT)),
                supervisor = "2.1.150",
            )

            val dot = awaitTag(MissionControlTags.STATUS_DOT).argbColors()
            dot shouldContain colors.status.err.argb()
            dot shouldNotContain colors.status.ok.argb()
        }

    @Test
    fun `status bar dot is muted while loading`() =
        runComposeUiTest {
            showBar(SessionsSnapshot.Initial, supervisor = "2.1.150")

            val dot = awaitTag(MissionControlTags.STATUS_DOT).argbColors()
            dot shouldContain colors.content.muted.argb()
            dot shouldNotContain colors.status.ok.argb()
        }

    @Test
    fun `mission control does not draw the status bar or the shortcuts toggle`() =
        runComposeUiTest {
            // The bar and the cheat sheet toggle moved out of the middle column (to the window and the sidebar)
            showMc(fakeSessions(worldSession("one")))
            awaitTag(
                MissionControlTags.row(
                    dev.quayside.core.domain
                        .SessionId("one"),
                ),
            )

            hasTag(MissionControlTags.MODE_BAR) shouldBe false
            hasTag(MissionControlTags.SHORTCUTS_TOGGLE) shouldBe false
        }
}
