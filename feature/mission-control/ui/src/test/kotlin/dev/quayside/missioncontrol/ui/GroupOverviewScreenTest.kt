package dev.quayside.missioncontrol.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * The "Podfoldery" overview on the group page (FR-P8, AM-307): rows, order, counters, the rest row and the way the
 * rows take clicks and keys. Subfolders are `bob`, `Carl`, `Anna`; none is named like a fixed text.
 */
@OptIn(ExperimentalTestApi::class)
internal class GroupOverviewScreenTest {
    private val rowsOfCarl = MissionControlTags.overviewRow(sub("Carl"))

    private fun busy() =
        listOf(
            groupSession("w1", "Carl", SessionState.AwaitingInput("q"), 1),
            groupSession("w2", "Carl/api", SessionState.AwaitingPermission("Bash"), 2),
            groupSession("k1", "Carl", SessionState.Working, 3),
            groupSession("f1", "bob", SessionState.Failed, 4),
            groupSession("d1", "Anna", SessionState.Done, 5),
        )

    @Test
    fun `group page shows a row per subfolder with its counters`() =
        runComposeUiTest {
            // Given a group where Carl waits and works, bob failed and Anna has only a finished session
            showGroup(busy())

            // Then the section is titled and every subfolder has a row
            awaitTag(MissionControlTags.OVERVIEW)
            onNodeWithText(mcText("overview.title")).assertExists()
            listOf("Anna", "bob", "Carl").forEach { overviewRow(it).assertExists() }
            textUnder(rowsOfCarl, "Carl").assertExists()

            // And each row has only the counters that are not zero
            counterTexts(rowsOfCarl) shouldBe setOf("2 czekają", "1 pracuje")
            counterTexts(MissionControlTags.overviewRow(sub("bob"))) shouldBe setOf("1 z błędem")
            counterTexts(MissionControlTags.overviewRow(sub("Anna"))) shouldBe emptySet()
        }

    @Test
    fun `counters describe count and state`() =
        runComposeUiTest {
            showGroup(busy())
            overviewRow("Carl")

            counterNode(rowsOfCarl, "2 czekają").assertExists()
            counterNode(rowsOfCarl, "1 pracuje").assertExists()
            counterNode(MissionControlTags.overviewRow(sub("bob")), "1 z błędem").assertExists()
        }

    @Test
    fun `rows are ordered waiting then failed then working`() =
        runComposeUiTest {
            // Given Anna works, bob failed and Carl waits: the reverse of the alphabet
            showGroup(
                listOf(
                    groupSession("a", "Anna", SessionState.Working, 1),
                    groupSession("b", "bob", SessionState.Failed, 2),
                    groupSession("c", "Carl", SessionState.AwaitingInput("q"), 3),
                ),
            )
            val tops =
                listOf(
                    "Anna",
                    "bob",
                    "Carl",
                ).associateWith { overviewRow(it).fetchSemanticsNode().boundsInRoot.top }

            // Then the rows run from the top in the order of the tiers
            tops.entries.sortedBy { it.value }.map { it.key } shouldBe listOf("Carl", "bob", "Anna")
        }

    @Test
    fun `idle subfolder says no agents`() =
        runComposeUiTest {
            showGroup(busy())
            overviewRow("Anna")

            textUnder(MissionControlTags.overviewRow(sub("Anna")), mcText("overview.idle")).assertExists()
            onAllNodes(hasText(mcText("overview.idle")) and inTag(rowsOfCarl)).assertCountEquals(0)
        }

    @Test
    fun `rest row shows only with sessions outside subfolders and has no click action`() =
        runComposeUiTest {
            // Given sessions in the group directory and in a folder that is no subfolder
            val clicks = mutableListOf<ProjectFocus>()
            showGroup(
                listOf(
                    groupSession("r1", "", SessionState.AwaitingInput("q"), 1),
                    groupSession("r2", "marketing", SessionState.Working, 2),
                ),
                MissionControlHandlers(onOpenSubfolder = { clicks += it }),
            )

            // Then the rest row says what is going on there and cannot be clicked
            val rest = awaitTag(MissionControlTags.OVERVIEW_REST)
            textUnder(MissionControlTags.OVERVIEW_REST, mcText("overview.rest")).assertExists()
            counterTexts(MissionControlTags.OVERVIEW_REST) shouldBe setOf("1 czeka", "1 pracuje")
            rest.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            rest.performClick()
            waitForIdle()
            clicks shouldBe emptyList()
            // and it is the last one
            val restTop = rest.fetchSemanticsNode().boundsInRoot.top
            listOf("Anna", "bob", "Carl").forEach {
                (overviewRow(it).fetchSemanticsNode().boundsInRoot.top < restTop) shouldBe
                    true
            }
        }

    @Test
    fun `rest row is absent when nothing outside subfolders waits works or failed`() =
        runComposeUiTest {
            showGroup(
                listOf(
                    groupSession("r1", "", SessionState.Done, 1),
                    groupSession("r2", "marketing", SessionState.Stopped, 2),
                    groupSession("a", "Anna", SessionState.Working, 3),
                ),
            )
            overviewRow("Anna")

            exists(MissionControlTags.OVERVIEW_REST) shouldBe false
        }

    @Test
    fun `click on a row reports the focus of its subfolder`() =
        runComposeUiTest {
            val clicks = mutableListOf<ProjectFocus>()
            showGroup(busy(), MissionControlHandlers(onOpenSubfolder = { clicks += it }))

            overviewRow("Carl").performClick()
            waitForIdle()

            clicks shouldBe listOf(ProjectFocus(OVERVIEW_GROUP.id, sub("Carl")))
        }

    @Test
    fun `row click takes no focus and space reaches the window`() =
        runComposeUiTest {
            // Given a click on a row
            val clicks = mutableListOf<ProjectFocus>()
            showGroup(busy(), MissionControlHandlers(onOpenSubfolder = { clicks += it }))
            overviewRow("Carl").performClick()
            waitForIdle()

            // Then the row is not focused, so Space and Enter are not a second click on it
            overviewRow("Carl").assertIsNotFocused()
            onRoot().performKeyInput { pressKey(Key.Spacebar) }
            onRoot().performKeyInput { pressKey(Key.Enter) }
            waitForIdle()
            clicks.size shouldBe 1
        }

    @Test
    fun `tab never focuses an overview row nor the scroll area`() =
        runComposeUiTest {
            // Given the group page and no click before
            showGroup(busy())
            awaitTag(MissionControlTags.OVERVIEW)

            // When Tab walks through the page
            repeat(times = 8) {
                onRoot().performKeyInput { pressKey(Key.Tab) }
                waitForIdle()

                // Then nothing inside the overview (rows, scroll container) holds the focus
                onAllNodes(
                    inTag(MissionControlTags.OVERVIEW) and
                        SemanticsMatcher.expectValue(SemanticsProperties.Focused, true),
                    useUnmergedTree = true,
                ).assertCountEquals(0)
            }
        }

    @Test
    fun `long overview scrolls and the session list stays visible`() =
        runComposeUiTest {
            // Given a group of twelve subfolders and one session of the group
            val names = (1..12).map { "s%02d".format(Locale.ROOT, it) }
            val big =
                aProject(
                    id = "big",
                    dir = OVERVIEW_DIR,
                    displayName = "plant-puls",
                    subfolders = subfolderNames(*names.toTypedArray()),
                )
            showGroup(listOf(groupSession("loose", "", SessionState.Working, 1, big.id)), project = big)
            awaitTag(MissionControlTags.OVERVIEW)

            // Then the card is at most 168 dp high: the first row shows, the last waits below
            (awaitTag(MissionControlTags.OVERVIEW).getUnclippedBoundsInRoot().height <= 168.dp) shouldBe true
            overviewRow("s01").assertIsDisplayed()
            overviewRow("s12").assertIsNotDisplayed()
            // And the session list under it is still on the screen
            awaitTag(MissionControlTags.row(SessionId("loose"))).assertIsDisplayed()

            // When the card is scrolled
            awaitTag(MissionControlTags.OVERVIEW).performTouchInput { swipeUp() }
            waitForIdle()

            // Then the last row shows and the list is still there
            overviewRow("s12").assertIsDisplayed()
            awaitTag(MissionControlTags.row(SessionId("loose"))).assertIsDisplayed()
        }
}
