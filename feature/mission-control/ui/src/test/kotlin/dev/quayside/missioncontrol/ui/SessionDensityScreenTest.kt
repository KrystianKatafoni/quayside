package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmDensity
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/**
 * Density of the session list (FR-A4, docs/08, plan AM-803): the rows of Mission Control read `AmTheme.density`.
 * COMFORTABLE is the look of AM-809 (tile 38 dp, 8 dp above and below, name + summary); COMPACT has tile 28 dp,
 * 4 dp and one line, the summary stays in the details panel.
 */
@OptIn(ExperimentalTestApi::class)
internal class SessionDensityScreenTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    private val summary = "Fixing the login form"
    private val id = SessionId("asking")
    private val asking =
        aSession(
            id = "asking",
            project = project.id,
            name = "fix-login",
            state = SessionState.AwaitingPermission("Bash"),
            startedAt = T0,
            lastActivityAt = T0.plusSeconds(480),
            summary = summary,
        )

    /** Mission Control over one session in the plex theme; [density] is read inside the composition. */
    private fun ComposeUiTest.showRows(density: () -> AmDensity): Density {
        var seen: Density? = null
        setContent {
            AmTestTheme(density()) {
                seen = LocalDensity.current
                val scope = rememberCoroutineScope()
                val controller =
                    remember {
                        MissionControlController(
                            SessionsApi(
                                ScriptedSessionMonitor(SessionsSnapshot(listOf(asking), SnapshotStatus.Fresh(T0))),
                                FakeSessions(),
                            ),
                            FakeProjectsApi(initial = listOf(project)),
                            MutableStateFlow<ProjectFocus?>(ProjectFocus(project.id)),
                            TestClock(T0.plusSeconds(600)),
                            scope,
                            NoOpLogger,
                            tick = 1.hours,
                        )
                    }
                MissionControl(controller)
            }
        }
        waitUntilExactlyOneExists(hasTestTag(row()))
        return checkNotNull(seen) { "content was not composed" }
    }

    private fun row(): String = MissionControlTags.row(id)

    private fun ComposeUiTest.inRow(matcher: SemanticsMatcher): Int =
        onAllNodes(matcher and hasAnyAncestor(hasTestTag(row()))).fetchSemanticsNodes().size

    private fun ComposeUiTest.tile(): SemanticsNodeInteraction =
        onAllNodes(hasContentDescription(text("state.awaitingPermission")) and hasAnyAncestor(hasTestTag(row())))
            .onFirst()

    private fun text(key: String) = MissionControlStrings[key]

    private fun ComposeUiTest.rowHeight(): Int = onNodeWithTag(row()).fetchSemanticsNode().size.height

    @Test
    fun `comfortable row shows the summary`() =
        runComposeUiTest {
            showRows { AmDensity.COMFORTABLE }

            inRow(hasText(summary, substring = true)) shouldBe 1
        }

    @Test
    fun `compact row hides the summary`() =
        runComposeUiTest {
            showRows { AmDensity.COMPACT }

            inRow(hasText(summary, substring = true)) shouldBe 0
            // and still names the session
            inRow(hasText("fix-login", substring = true)) shouldBe 1
        }

    @Test
    fun `compact row is shorter than comfortable`() =
        runComposeUiTest {
            // Given the list in the comfortable density
            var density by mutableStateOf(AmDensity.COMFORTABLE)
            showRows { density }
            val comfortable = rowHeight()

            // When the density changes to compact
            density = AmDensity.COMPACT
            waitForIdle()

            // Then the same row is shorter, without a new composition of the screen
            rowHeight() shouldBeLessThan comfortable
        }

    @Test
    fun `comfortable tile is 38 dp`() =
        runComposeUiTest {
            val density = showRows { AmDensity.COMFORTABLE }

            tile().fetchSemanticsNode().size.height shouldBe with(density) { 38.dp.roundToPx() }
        }

    @Test
    fun `compact tile is 28 dp`() =
        runComposeUiTest {
            val density = showRows { AmDensity.COMPACT }

            val size = tile().fetchSemanticsNode().size
            size.height shouldBe with(density) { 28.dp.roundToPx() }
            size.width shouldBe with(density) { 28.dp.roundToPx() }
        }

    @Test
    fun `comfortable row keeps 8 dp above the tile`() =
        runComposeUiTest {
            val density = showRows { AmDensity.COMFORTABLE }

            val above = tile().fetchSemanticsNode().boundsInRoot.top - rowTop()
            above shouldBe with(density) { 8.dp.toPx() }
        }

    @Test
    fun `compact row keeps 4 dp to 8 dp above the tile`() =
        runComposeUiTest {
            // The tile may be centred in a row taller than itself, so the distance is at least the padding
            val density = showRows { AmDensity.COMPACT }

            val above = tile().fetchSemanticsNode().boundsInRoot.top - rowTop()
            above shouldBeGreaterThan with(density) { 4.dp.toPx() } - 1f
            above shouldBeLessThan with(density) { 8.dp.toPx() }
        }

    @Test
    fun `summary stays in the details panel in compact`() =
        runComposeUiTest {
            // Given the compact list
            showRows { AmDensity.COMPACT }

            // When the user selects the session
            onNodeWithTag(row()).performClick()
            waitUntilExactlyOneExists(hasTestTag(MissionControlTags.DETAILS_SUMMARY))

            // Then the panel shows the summary the row does not
            onAllNodes(hasText(summary, substring = true) and hasAnyAncestor(hasTestTag(MissionControlTags.DETAILS)))
                .fetchSemanticsNodes()
                .size shouldBe 1
            inRow(hasText(summary, substring = true)) shouldBe 0
        }

    @Test
    fun `compact row keeps status chip and age`() =
        runComposeUiTest {
            // Status is not carried by colour alone (NFR-6): the tile is announced, the chip and the age are text
            showRows { AmDensity.COMPACT }

            inRow(hasContentDescription(text("state.awaitingPermission"))) shouldBe 1
            inRow(hasText(MissionControlStrings.format(UiText("event.permission", listOf("Bash"))))) shouldBe 1
            inRow(hasText(MissionControlStrings.format(UiText("age.min", listOf("2"))))) shouldBe 1
        }

    private fun ComposeUiTest.rowTop(): Float = onNodeWithTag(row()).fetchSemanticsNode().boundsInRoot.top
}
