package dev.quayside.projects.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import dev.quayside.core.designsystem.AmChip
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.testing.aSessionId
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.math.abs

/**
 * The chips of the sidebar rows read from pixels at 4 px per dp (FR-P6, FR-P7, NFR-6, AM-310 as reduced): a project row
 * draws a chip (icon and number) for waiting, working and failed, only for a non-zero count; a subfolder row draws none
 * but still tints its folder icon. Rows: the group `plant` with the subfolders `Anna`, `bob` and `cara`, the project
 * `plain` under test, and the project `marker` whose chip tells that the sessions have loaded. Names differ from fixed
 * texts.
 */
@OptIn(ExperimentalTestApi::class)
internal class SidebarCountersLookTest {
    private val plantDir = Path.of("/Users/test/projects/plant-puls")
    private val plant =
        aProject(
            id = "project0001",
            dir = plantDir,
            displayName = "Plant",
            subfolders = subfolderNames("bob", "Anna", "cara"),
        )
    private val plain = aProject(id = "project0002", dir = Path.of("/Users/test/projects/plain"), displayName = "Plain")
    private val marker =
        aProject(id = "project0003", dir = Path.of("/Users/test/projects/marker"), displayName = "Marker")
    private val colors = SampleTokens.colors()
    private val groupRow = ProjectsTags.row(plant.id)
    private val annaRow = ProjectsTags.subfolderRow(plant.id, SubfolderName("Anna"))
    private val bobRow = ProjectsTags.subfolderRow(plant.id, SubfolderName("bob"))
    private val caraRow = ProjectsTags.subfolderRow(plant.id, SubfolderName("cara"))
    private val plainRow = ProjectsTags.row(plain.id)
    private val markerRow = ProjectsTags.row(marker.id)

    private fun world(vararg sessions: WorldSession) =
        ControllerWorld(
            projects = FakeProjectsApi(initial = listOf(plant, plain, marker)),
            running = sessions.toList() + WorldSession(aSessionId("marker"), marker.dir, SessionState.Working),
        )

    private fun plainSessions(vararg states: SessionState) =
        states.mapIndexed { i, state -> WorldSession(aSessionId("p$i"), plain.dir, state) }.toTypedArray()

    private fun ComposeUiTest.show(
        on: ControllerWorld,
        extra: @Composable () -> Unit = {},
    ) {
        showProjects(on) {
            Scaled4 {
                Row {
                    extra()
                    ProjectsSidebar(it)
                }
            }
        }
    }

    /** The descriptions of the chips of [row] ("czeka: 1"), left to right. */
    private fun ComposeUiTest.chipTexts(row: String): List<String> =
        onAllNodes(hasContentDescription(": ", substring = true) and hasAnyAncestor(hasTestTag(row)), true)
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.left }
            .flatMap { it.config[SemanticsProperties.ContentDescription] }

    /** Waits for the chip of the marker project: from then on the sessions are listed and zeros really mean zero. */
    private fun ComposeUiTest.awaitLoaded() = waitUntil { chipTexts(markerRow) == listOf("pracuje: 1") }

    private fun ComposeUiTest.chip(
        row: String,
        description: String,
    ): SemanticsNodeInteraction {
        val matcher = hasContentDescription(description) and hasAnyAncestor(hasTestTag(row))
        waitUntil { onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        return onAllNodes(matcher, useUnmergedTree = true).onFirst()
    }

    /** Colours of the strip with the folder icon, [from] to [to] dp from the left edge of the row. */
    private fun SemanticsNodeInteraction.iconStrip(
        from: Int,
        to: Int,
    ): Set<Int> {
        val pixels = pixelMap()
        return (from * SCALE until to * SCALE).flatMapTo(HashSet()) { x ->
            (0 until pixels.height).map { pixels.argbAt(x, it) }
        }
    }

    /** The reference chips, the way the design system draws them, over the fill of an unselected row. */
    @Composable
    private fun References() {
        Column {
            REFERENCES.forEach { ref ->
                Box(
                    Modifier.testTag(ref.tag).background(panelColor()),
                ) {
                    AmChip(
                        label = ref.number,
                        tone = ref.tone,
                        icon = ref.icon,
                        description = "ref ${ref.tag}",
                    )
                }
            }
        }
    }

    /**
     * Pixels of [a] that differ visibly from [b] (more than [TOLERANCE] in a channel): the edge of a pill that sits on
     * a fraction of a pixel is anti-aliased a little differently, an icon or a tone of another kind is not.
     */
    private fun differences(
        a: PixelMap,
        b: PixelMap,
    ): Int {
        if (a.width != b.width || a.height != b.height) return Int.MAX_VALUE
        return (0 until a.width).sumOf { x -> (0 until a.height).count { y -> apart(a.argbAt(x, y), b.argbAt(x, y)) } }
    }

    private fun apart(
        p: Int,
        q: Int,
    ): Boolean =
        (0..SHIFT_MAX step BYTE).any { shift ->
            abs(((p shr shift) and MASK) - ((q shr shift) and MASK)) >
                TOLERANCE
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("states")
    fun `chip of a state is drawn as the design system chip with that tone and icon`(
        tag: String,
        description: String,
    ) = runWideTest {
        // Given a project with 1 waiting, 2 working and 3 failed sessions
        val sessions =
            plainSessions(
                SessionState.AwaitingInput(null),
                SessionState.Working,
                SessionState.Working,
                SessionState.Failed,
                SessionState.Failed,
                SessionState.Failed,
            )
        show(world(*sessions)) { References() }
        awaitLoaded()

        // When the chip is drawn at 4 px per dp
        val drawn = chip(plainRow, description).pixelMap()

        // Then it is pixel for pixel the AmChip of that state: tone fill, icon and number
        differences(drawn, onNodeWithTag(tag).pixelMap()) shouldBe 0
        // (the three states are drawn differently, so the match above is not by accident)
        REFERENCES.filter { it.tag != tag }.forEach { other ->
            (differences(drawn, onNodeWithTag(other.tag).pixelMap()) > 0) shouldBe true
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chipCases")
    fun `only states with sessions have a chip and they come waiting working failed`(
        @Suppress("UNUSED_PARAMETER") name: String,
        states: List<SessionState>,
        expected: List<String>,
    ) = runTallTest {
        // Given a project with these sessions (done and stopped ones among them)
        show(world(*plainSessions(*states.toTypedArray())))
        awaitLoaded()

        // Then its chips are exactly the expected ones, in that order
        chipTexts(plainRow) shouldBe expected
    }

    @Test
    fun `project with only zero counts has no row of chips`() =
        runTallTest {
            // Given a project with a done and a stopped session only, next to one with a working session
            show(world(*plainSessions(SessionState.Done, SessionState.Stopped)))
            awaitLoaded()

            // Then it has no chip, and its row is lower than the row with a chip (no empty row of chips)
            chipTexts(plainRow) shouldBe emptyList()
            val quiet = onNodeWithTag(plainRow).fetchSemanticsNode().boundsInRoot.height
            val busy = onNodeWithTag(markerRow).fetchSemanticsNode().boundsInRoot.height
            quiet shouldBeLessThan busy
        }

    private fun groupSessions() =
        arrayOf(
            WorldSession(aSessionId("asks"), plantDir.resolve("Anna/x"), SessionState.AwaitingInput(null)),
            WorldSession(aSessionId("fin"), plantDir.resolve("Anna"), SessionState.Done),
            WorldSession(aSessionId("works"), plantDir.resolve("bob"), SessionState.Working),
            WorldSession(aSessionId("rooted"), plantDir, SessionState.Working),
        )

    @Test
    fun `subfolder rows have no chips and the group row sums the whole group`() =
        runTallTest {
            // Given sessions in Anna (waiting, done), in bob (working) and in the project directory (working)
            show(world(*groupSessions()))
            awaitLoaded()

            // Then the group row has chips for waiting and working only (done is not shown) ...
            chipTexts(groupRow) shouldBe listOf("czeka: 1", "pracuje: 2")
            // ... and no subfolder row has a chip, whatever its sessions
            listOf(annaRow, bobRow, caraRow).forEach { row ->
                awaitTag(row)
                chipTexts(row) shouldBe emptyList()
            }
        }

    @Test
    fun `subfolder folder icon keeps the tone of its sessions`() =
        runTallTest {
            // Given the same group: Anna waits, bob works, cara has nothing
            show(world(*groupSessions()))
            awaitLoaded()

            // When the folder icons (16 dp after 28 dp of indent) are drawn
            val warn = colors.status.warn.argb()
            val work = colors.status.work.argb()
            val anna = awaitTag(annaRow).iconStrip(from = 28, to = 44)
            val bob = awaitTag(bobRow).iconStrip(from = 28, to = 44)
            val cara = awaitTag(caraRow).iconStrip(from = 28, to = 44)

            // Then waiting gives warn, working gives work, and none gives neither
            anna shouldContain warn
            anna shouldNotContain work
            bob shouldContain work
            bob shouldNotContain warn
            cara shouldNotContain warn
            cara shouldNotContain work
        }

    @Test
    fun `no chips while the sessions load`() =
        runTallTest {
            // Given sessions that are running, but a monitor that has not listed yet
            val loading = world(*plainSessions(SessionState.Working)).also { it.monitor = LoadingSessionMonitor() }

            show(loading)
            awaitTag(plainRow)
            awaitTag(groupRow)
            awaitTag(markerRow)
            waitForIdle()

            // Then no row has a chip
            listOf(groupRow, plainRow, markerRow, annaRow).forEach { chipTexts(it) shouldBe emptyList() }
        }

    @Test
    fun `chips never take focus and are not clickable`() =
        runTallTest {
            // Given the sidebar with a click on a row that has chips
            show(world(*groupSessions()))
            awaitLoaded()
            chip(groupRow, "czeka: 1")
            awaitTag(groupRow).performClick()
            waitForIdle()

            // When Tab walks through the window
            repeat(times = 6) {
                onRoot().performKeyInput {
                    keyDown(Key.Tab)
                    keyUp(Key.Tab)
                }
                waitForIdle()

                // Then no row, chip or button of the sidebar holds the focus
                onAllNodes(
                    hasAnyAncestor(hasTestTag(ProjectsTags.SIDEBAR)) and
                        SemanticsMatcher.expectValue(SemanticsProperties.Focused, true),
                    useUnmergedTree = true,
                ).assertCountEquals(0)
            }
            // and a chip has no click action of its own
            chip(groupRow, "czeka: 1")
                .fetchSemanticsNode()
                .config
                .contains(SemanticsActions.OnClick) shouldBe false
            chipTexts(groupRow).size shouldBe 2
        }

    /** Like [runTallTest], but wide enough for the sidebar (260 dp) and the reference chips beside it. */
    private fun runWideTest(block: suspend SkikoComposeUiTest.() -> Unit) =
        runSkikoComposeUiTest(size = Size(width = 1400f, height = 1500f), block = block)

    private data class Reference(
        val tag: String,
        val number: String,
        val tone: StatusTone,
        val icon: IconKind,
    )

    companion object {
        private const val TOLERANCE = 8
        private const val BYTE = 8
        private const val SHIFT_MAX = 16
        private const val MASK = 0xFF

        private val REFERENCES =
            listOf(
                Reference("ref-waiting", "1", StatusTone.WARN, IconKind.WAITING),
                Reference("ref-working", "2", StatusTone.WORK, IconKind.WORKING),
                Reference("ref-failed", "3", StatusTone.ERR, IconKind.FAILED),
            )

        private val ASKING = SessionState.AwaitingInput(null)

        @JvmStatic
        fun states(): List<Arguments> =
            listOf(
                arguments("ref-waiting", "czeka: 1"),
                arguments("ref-working", "pracuje: 2"),
                arguments("ref-failed", "błąd: 3"),
            )

        @JvmStatic
        fun chipCases(): List<Arguments> =
            listOf(
                arguments("none", emptyList<SessionState>(), emptyList<String>()),
                arguments(
                    "done and stopped only",
                    listOf(SessionState.Done, SessionState.Stopped),
                    emptyList<String>(),
                ),
                arguments("working only", listOf(SessionState.Working), listOf("pracuje: 1")),
                arguments("failed only", listOf(SessionState.Failed), listOf("błąd: 1")),
                arguments("waiting by permission", listOf(SessionState.AwaitingPermission("Bash")), listOf("czeka: 1")),
                arguments(
                    "failed and waiting, input order reversed",
                    listOf(SessionState.Failed, SessionState.Failed, ASKING),
                    listOf("czeka: 1", "błąd: 2"),
                ),
                arguments(
                    "all three with done between",
                    listOf(SessionState.Failed, SessionState.Done, SessionState.Working, ASKING, ASKING, ASKING),
                    listOf("czeka: 3", "pracuje: 1", "błąd: 1"),
                ),
            )
    }
}
