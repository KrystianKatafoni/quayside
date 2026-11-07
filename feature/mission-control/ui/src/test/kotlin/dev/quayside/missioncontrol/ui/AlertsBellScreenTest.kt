package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The bell of the window top bar (AM-816 decision 1): the count of "Czeka na mnie", a click shows that view. */
@OptIn(ExperimentalTestApi::class)
internal class AlertsBellScreenTest {
    private var opened = 0

    /** Two waiting sessions in the selected project, one in the other and one outside any project; others. */
    private fun snapshot() =
        freshSnapshot(
            aSession(id = "q1", project = MC_PROJECT.id, state = SessionState.AwaitingInput("q")),
            aSession(id = "q2", project = MC_PROJECT.id, state = SessionState.AwaitingPermission("Bash")),
            aSession(id = "p1", project = MC_OTHER.id, state = SessionState.AwaitingInput(null)),
            aSession(id = "n1", project = null, state = SessionState.AwaitingInput(null)),
            aSession(id = "w1", project = MC_PROJECT.id, state = SessionState.Working),
            aSession(id = "d1", project = MC_PROJECT.id, state = SessionState.Done),
            aSession(id = "f1", project = MC_PROJECT.id, state = SessionState.Failed),
        )

    private fun ComposeUiTest.showBell(snapshot: SessionsSnapshot = snapshot()): MissionControlController =
        showScripted(snapshot) { controller -> AlertsBell(controller, onOpen = { opened++ }) }

    /** Every number that a screen reader finds in the bell: texts and descriptions of its whole subtree. */
    private fun ComposeUiTest.announcedNumbers(): List<String> =
        onAllNodes(inTag(MissionControlTags.ALERTS_BELL), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node ->
                val texts = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }
                val descriptions = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                texts + descriptions
            }.flatMap { Regex("\\d+").findAll(it).map { match -> match.value }.toList() }
            .distinct()

    @Test
    fun `bell badge counts waiting sessions`() =
        runComposeUiTest {
            // Given waiting sessions in two projects and outside any project, and sessions in other states
            showBell()
            awaitTag(MissionControlTags.ALERTS_BELL)

            // Then the badge announces the count of the "Czeka na mnie" view over every project, and no other number
            waitUntil { announcedNumbers() == listOf("4") }
            announcedNumbers() shouldBe listOf("4")
        }

    @Test
    fun `bell click shows waiting view and calls onOpen`() =
        runComposeUiTest {
            // Given the project page
            val controller = showBell()
            awaitTag(MissionControlTags.ALERTS_BELL)

            // When the user clicks the bell
            onNodeWithTag(MissionControlTags.ALERTS_BELL).performClick()
            waitForIdle()

            // Then the "Czeka na mnie" view is shown and the callback ran once
            controller.global().view shouldBe GlobalView.WAITING
            opened shouldBe 1
        }

    @Test
    fun `bell click shows waiting view also while another view is shown`() =
        runComposeUiTest {
            val controller = showBell()
            runOnIdle { controller.onIntent(MissionControlIntent.ShowView(GlobalView.FAILED)) }
            awaitTag(MissionControlTags.ALERTS_BELL).performClick()
            waitForIdle()

            controller.global().view shouldBe GlobalView.WAITING
        }

    @Test
    fun `bell never takes focus`() =
        runComposeUiTest {
            showBell()

            awaitTag(MissionControlTags.ALERTS_BELL).performClick()
            waitForIdle()

            onNodeWithTag(MissionControlTags.ALERTS_BELL).assertIsNotFocused()
        }

    @Test
    fun `space after clicking the bell reaches the window handler`() =
        runComposeUiTest {
            var spaces = 0
            showScripted(snapshot()) { controller ->
                Box(
                    Modifier
                        .testTag(WINDOW)
                        .onKeyEvent {
                            (it.key == Key.Spacebar && it.type == KeyEventType.KeyDown).also { hit ->
                                if (hit) spaces++
                            }
                        }.focusable(),
                ) {
                    AlertsBell(controller, onOpen = { opened++ })
                }
            }
            onNodeWithTag(WINDOW).performSemanticsAction(SemanticsActions.RequestFocus)

            awaitTag(MissionControlTags.ALERTS_BELL).performClick()
            onRoot().performKeyInput {
                keyDown(Key.Spacebar)
                keyUp(Key.Spacebar)
            }
            waitForIdle()

            spaces shouldBe 1
            opened shouldBe 1
            onNodeWithTag(WINDOW).assertIsFocused()
        }

    private companion object {
        const val WINDOW = "window"
    }
}
