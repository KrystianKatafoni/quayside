package dev.quayside.projects.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.mix
import dev.quayside.core.designtokens.over
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.aSessionId
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/**
 * The four counters of the sidebar rows read from pixels at 4 px per dp (FR-P6, FR-P7, NFR-6, AM-310): a zero counter
 * is a ring in the status token with the number in muted, filled opaquely with the background of its row. Rows: the
 * group `plant` with the subfolders `Anna` and `bob`, and the plain project `plain`. Names differ from fixed texts.
 */
@OptIn(ExperimentalTestApi::class)
internal class SidebarCountersLookTest {
    private val plantDir = Path.of("/Users/test/projects/plant-puls")
    private val plant =
        aProject(id = "project0001", dir = plantDir, displayName = "Plant", subfolders = subfolderNames("bob", "Anna"))
    private val plain = aProject(id = "project0002", dir = Path.of("/Users/test/projects/plain"), displayName = "Plain")
    private val colors = SampleTokens.colors()
    private val groupRow = ProjectsTags.row(plant.id)
    private val annaRow = ProjectsTags.subfolderRow(plant.id, SubfolderName("Anna"))
    private val bobRow = ProjectsTags.subfolderRow(plant.id, SubfolderName("bob"))
    private val plainRow = ProjectsTags.row(plain.id)

    private fun world(vararg sessions: WorldSession) =
        ControllerWorld(projects = FakeProjectsApi(initial = listOf(plant, plain)), running = sessions.toList())

    private fun ComposeUiTest.show(on: ControllerWorld = world()) {
        showProjects(on) { Scaled4 { ProjectsSidebar(it) } }
    }

    private fun ComposeUiTest.counter(
        row: String,
        description: String,
    ): SemanticsNodeInteraction {
        val matcher = hasContentDescription(description) and hasAnyAncestor(hasTestTag(row))
        waitUntil { onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        return onAllNodes(matcher, useUnmergedTree = true).onFirst()
    }

    private fun ComposeUiTest.countersIn(row: String): Int =
        onAllNodes(hasContentDescription(": ", substring = true) and hasAnyAncestor(hasTestTag(row)), true)
            .fetchSemanticsNodes()
            .size

    private fun ComposeUiTest.select(row: String) {
        awaitTag(row).performClick()
        waitForIdle()
    }

    private fun ring(
        tone: StatusTone,
        tokens: ColorTokens = colors,
    ): Rgba =
        when (tone) {
            StatusTone.WARN -> tokens.status.warn
            StatusTone.WORK -> tokens.status.work
            StatusTone.OK -> tokens.status.ok
            else -> tokens.status.err
        }

    @ParameterizedTest(name = "{1} on {0}")
    @MethodSource("zeroCounters")
    fun `zero counters show the muted number and the ring of each tone`(
        row: String,
        description: String,
        tone: StatusTone,
    ) = runTallTest {
        // Given a sidebar without any session: every row has four zero counters
        show()

        // When the counter is drawn at 4 px per dp
        val counter = counter(row, description)
        val pixels = counter.pixelMap()
        val bounds = counter.fetchSemanticsNode().boundsInRoot

        // Then it is a circle of at least 22 dp with a 1 dp ring in the status token (4 px at the left edge)
        bounds.width shouldBe bounds.height
        (bounds.width >= 22 * SCALE) shouldBe true
        pixels.argbAt(2, pixels.height / 2) shouldBe ring(tone).argb()
        // and inside the ring the background of the row, nothing else
        pixels.argbAt(2 * SCALE, pixels.height / 2) shouldBe colors.surfaces.panel.argb()
        // and the number is muted, not the plain text colour
        counter.argbColors() shouldContain colors.content.muted.argb()
        counter.argbColors() shouldNotContain colors.content.text.argb()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    fun `zero counter on a selected row is filled with sel`(row: String) =
        runTallTest {
            show()
            select(row)

            ZERO_TONES.forEach { (description, tone) ->
                val pixels = counter(row, description).pixelMap()

                pixels.argbAt(2, pixels.height / 2) shouldBe ring(tone).argb()
                pixels.argbAt(2 * SCALE, pixels.height / 2) shouldBe colors.surfaces.sel.argb()
            }
            counter(row, "czeka: 0").argbColors() shouldContain colors.content.muted.argb()
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    fun `hovered row keeps the opaque fill of a zero counter`(row: String) =
        runTallTest {
            // Given the sidebar and a counter at rest
            show()
            val node = awaitTag(row)
            val atRest = node.pixelMap()
            val topMiddle = atRest.width / 2

            // When the mouse moves over the row (on its left padding, away from the counters)
            node.performMouseInput { moveTo(Offset(3f * SCALE, atRest.height / 2f)) }
            waitForIdle()

            // Then the click layer does draw its overlay over the row (the premise of the test) ...
            node.pixelMap().argbAt(topMiddle, 2) shouldNotBe atRest.argbAt(topMiddle, 2)
            // ... and the counters, filled opaquely, do not show it through
            ZERO_TONES.forEach { (description, tone) ->
                val pixels = counter(row, description).pixelMap()
                pixels.argbAt(2 * SCALE, pixels.height / 2) shouldBe colors.surfaces.panel.argb()
                pixels.argbAt(2, pixels.height / 2) shouldBe ring(tone).argb()
            }
        }

    @Test
    fun `non zero counter keeps its tone fill`() =
        runTallTest {
            // Given a project with one session of each kind
            val dir = plain.dir
            show(
                world(
                    WorldSession(aSessionId("w"), dir, SessionState.AwaitingInput(null)),
                    WorldSession(aSessionId("k"), dir, SessionState.Working),
                    WorldSession(aSessionId("d"), dir, SessionState.Done),
                    WorldSession(aSessionId("f"), dir, SessionState.Failed),
                ),
            )

            // Then each counter is filled with the tint of its tone and has no ring of the status token
            val tint = { token: Rgba ->
                colors.surfaces.panel
                    .over(colors.surfaces.bg)
                    .mix(token, TINT)
            }
            listOf(
                Triple("czeka: 1", colors.status.warnBg, colors.status.warn),
                Triple("pracuje: 1", tint(colors.status.work), colors.status.work),
                Triple("gotowe: 1", tint(colors.status.ok), colors.status.ok),
                Triple("błąd: 1", tint(colors.status.err), colors.status.err),
            ).forEach { (description, fill, token) ->
                val pixels = counter(plainRow, description).pixelMap()
                pixels.argbAt(2, pixels.height / 2) shouldBe fill.argb()
                pixels.argbAt(2 * SCALE, pixels.height / 2) shouldBe fill.argb()
                (fill == token) shouldBe false
            }
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    fun `counters read czeka 0 pracuje 0 gotowe 0 błąd 0`(row: String) =
        runTallTest {
            show()

            // The four descriptions, once each, from left to right in the order of the roadmap
            val lefts =
                ZERO_TONES.map { (description, _) -> counter(row, description).fetchSemanticsNode().boundsInRoot.left }
            lefts shouldBe lefts.sorted()
            lefts.toSet().size shouldBe 4
            countersIn(row) shouldBe 4
        }

    @Test
    fun `group row sums the group and the subfolder row only its own`() =
        runTallTest {
            // Given sessions in the project directory, in Anna, in bob and in a directory that is no subfolder
            show(
                world(
                    WorldSession(aSessionId("root"), plantDir, SessionState.Working),
                    WorldSession(aSessionId("asks"), plantDir.resolve("Anna/x"), SessionState.AwaitingInput(null)),
                    WorldSession(aSessionId("fin1"), plantDir.resolve("bob"), SessionState.Done),
                    WorldSession(aSessionId("fin2"), plantDir.resolve("bob"), SessionState.Done),
                    WorldSession(aSessionId("bad1"), plantDir.resolve("bob/y"), SessionState.Failed),
                    WorldSession(aSessionId("bad2"), plantDir.resolve("marketing/z"), SessionState.Failed),
                ),
            )

            // Then the group row counts all of them and the subfolder rows only their own
            listOf("czeka: 1", "pracuje: 1", "gotowe: 2", "błąd: 2").forEach { counter(groupRow, it) }
            listOf("czeka: 1", "pracuje: 0", "gotowe: 0", "błąd: 0").forEach { counter(annaRow, it) }
            listOf("czeka: 0", "pracuje: 0", "gotowe: 2", "błąd: 1").forEach { counter(bobRow, it) }
            // and the plain project is unaffected
            listOf("czeka: 0", "pracuje: 0", "gotowe: 0", "błąd: 0").forEach { counter(plainRow, it) }
        }

    @Test
    fun `counters are drawn at zero while the sessions load`() =
        runTallTest {
            // Given sessions that are running, but a monitor that has not listed yet
            val running = WorldSession(aSessionId("w"), plantDir, SessionState.Working)
            val loading =
                ControllerWorld(
                    projects = FakeProjectsApi(initial = listOf(plant, plain)),
                    running = listOf(running),
                ).also { it.monitor = LoadingSessionMonitor() }

            show(loading)

            // Then every row has its four counters, all at zero
            listOf(groupRow, annaRow, plainRow).forEach { row ->
                listOf("czeka: 0", "pracuje: 0", "gotowe: 0", "błąd: 0").forEach { counter(row, it) }
            }
        }

    @Test
    fun `counters never take focus and are not clickable`() =
        runTallTest {
            // Given the sidebar with a click on a counter's row
            show()
            counter(groupRow, "czeka: 0")
            select(groupRow)

            // When Tab walks through the window
            repeat(times = 6) {
                onRoot().performKeyInput {
                    keyDown(Key.Tab)
                    keyUp(Key.Tab)
                }
                waitForIdle()

                // Then no row, counter or button of the sidebar holds the focus
                onAllNodes(
                    hasAnyAncestor(hasTestTag(ProjectsTags.SIDEBAR)) and
                        SemanticsMatcher.expectValue(SemanticsProperties.Focused, true),
                    useUnmergedTree = true,
                ).assertCountEquals(0)
            }
            // and a counter has no click action of its own
            counter(groupRow, "czeka: 0")
                .fetchSemanticsNode()
                .config
                .contains(SemanticsActions.OnClick) shouldBe false
            countersIn(groupRow) shouldBe 4
        }

    companion object {
        private const val TINT = 15
        private val ZERO_TONES =
            listOf(
                "czeka: 0" to StatusTone.WARN,
                "pracuje: 0" to StatusTone.WORK,
                "gotowe: 0" to StatusTone.OK,
                "błąd: 0" to StatusTone.ERR,
            )
        private val group = ProjectsTags.row(ProjectId("project0001"))
        private val sub = ProjectsTags.subfolderRow(ProjectId("project0001"), SubfolderName("Anna"))

        @JvmStatic
        fun rows(): List<Arguments> = listOf(arguments(group), arguments(sub))

        @JvmStatic
        fun zeroCounters(): List<Arguments> =
            listOf(group, sub).flatMap { row -> ZERO_TONES.map { (text, tone) -> arguments(row, text, tone) } }
    }
}
