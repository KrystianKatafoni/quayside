package dev.quayside.missioncontrol.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The place badge in the rows of the session list on a group page (FR-P7, FR-S9, AM-310). Subfolders are `bob`,
 * `Carl`, `Anna`; the project directory and `marketing` are "folder główny".
 */
@OptIn(ExperimentalTestApi::class)
internal class SessionPlaceScreenTest {
    private fun place(id: String) = MissionControlTags.rowPlace(SessionId(id))

    /** True when the badge of session [id] shows exactly [text] (the unmerged tree keeps the cleared texts). */
    private fun ComposeUiTest.shows(
        id: String,
        text: String,
    ) = onAllNodes(hasText(text) and inTag(place(id)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** True when the badge of session [id] is read as [description]. */
    private fun ComposeUiTest.reads(
        id: String,
        description: String,
    ) = onAllNodes(hasContentDescription(description) and inTag(place(id)), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()

    private fun ComposeUiTest.awaitBadge(id: String) {
        awaitTag(MissionControlTags.row(SessionId(id)))
        waitUntil { onAllNodes(hasTestTag(place(id)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `place badge shows the subfolder name and reads folder soft`() =
        runComposeUiTest {
            // Given sessions in the subfolders Carl and bob
            showGroup(
                listOf(
                    groupSession("in-carl", "Carl/api", SessionState.Working, 1),
                    groupSession("in-bob", "bob", SessionState.AwaitingInput("q"), 2),
                ),
            )
            awaitBadge("in-carl")
            awaitBadge("in-bob")

            // Then each badge shows the bare name of its subfolder and is read as "folder: name"
            shows("in-carl", "Carl") shouldBe true
            shows("in-bob", "bob") shouldBe true
            reads("in-carl", "folder: Carl") shouldBe true
            reads("in-bob", "folder: bob") shouldBe true
            // and not the other way round
            shows("in-carl", "bob") shouldBe false
            reads("in-bob", "folder: Carl") shouldBe false
        }

    @Test
    fun `main folder badge reads folder główny`() =
        runComposeUiTest {
            // Given sessions in the project directory, in an unchecked subdirectory and next to a subfolder name
            showGroup(
                listOf(
                    groupSession("rooted", "", SessionState.Working, 1),
                    groupSession("loose", "marketing/site", SessionState.Working, 2),
                    groupSession("sibling", "bobx", SessionState.Working, 3),
                ),
            )

            listOf("rooted", "loose", "sibling").forEach { id ->
                awaitBadge(id)
                // Then each one says "folder główny" and reads the same
                shows(id, "folder główny") shouldBe true
                reads(id, "folder główny") shouldBe true
            }
        }

    @Test
    fun `badge carries text, not only colour`() =
        runComposeUiTest {
            // Given two sessions in different places: both badges are neutral chips of the same colour
            showGroup(
                listOf(
                    groupSession("here", "Anna", SessionState.Working, 1),
                    groupSession("there", "", SessionState.Working, 2),
                ),
            )
            awaitBadge("here")
            awaitBadge("there")

            // Then only their texts tell them apart, and every badge has one
            shows("here", "Anna") shouldBe true
            shows("there", "folder główny") shouldBe true
            shows("here", "folder główny") shouldBe false
            shows("there", "Anna") shouldBe false
        }

    @Test
    fun `no badge on a project without subfolders`() =
        runComposeUiTest {
            // Given a project with the same id and directory but no subfolders
            val plain = aProject(id = OVERVIEW_GROUP.id.value, dir = OVERVIEW_DIR, displayName = "plant-puls")
            showGroup(listOf(groupSession("a", "soft", SessionState.Working, 1)), project = plain)

            awaitTag(MissionControlTags.row(SessionId("a")))

            onAllNodes(hasTestTag(place("a")), useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `no badge on a subfolder page`() =
        runComposeUiTest {
            // Given the page of the subfolder Carl
            val focus = ProjectFocus(OVERVIEW_GROUP.id, SubfolderName("Carl"))
            showGroup(listOf(groupSession("in-carl", "Carl", SessionState.Working, 1)), focus = focus)

            awaitTag(MissionControlTags.row(SessionId("in-carl")))

            onAllNodes(hasTestTag(place("in-carl")), useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `long subfolder name keeps the age column visible`() =
        runComposeUiTest {
            // Given a subfolder whose name is far wider than any row
            val long = "x".repeat(LONG_NAME)
            val group =
                aProject(
                    id = OVERVIEW_GROUP.id.value,
                    dir = OVERVIEW_DIR,
                    displayName = "plant-puls",
                    subfolders = listOf(SubfolderName(long)),
                )
            showGroup(listOf(groupSession("wide", long, SessionState.Working, 1)), project = group)
            awaitBadge("wide")

            // Then the badge is cut at 120 dp and the age (599 s = "9 min") is still on the screen
            (awaitTag(place("wide")).getUnclippedBoundsInRoot().width <= 120.dp) shouldBe true
            textUnder(MissionControlTags.row(SessionId("wide")), mcText("age.min", "9")).assertIsDisplayed()
        }

    private companion object {
        const val LONG_NAME = 200
    }
}
