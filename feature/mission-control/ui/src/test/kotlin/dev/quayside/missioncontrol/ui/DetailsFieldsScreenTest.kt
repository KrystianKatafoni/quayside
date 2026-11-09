package dev.quayside.missioncontrol.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.SessionMeta
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The fields of the details panel as label and value pairs in two columns (AM-816). */
@OptIn(ExperimentalTestApi::class)
internal class DetailsFieldsScreenTest {
    private val colors = SampleTokens.colors()
    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    private val started = T0
    private val active = T0.plusSeconds(480)

    private fun session(
        cwd: java.nio.file.Path = MC_PROJECT.dir.resolve("tree"),
        meta: SessionMeta = SessionMeta(),
    ) = aSession(
        id = "one",
        project = MC_PROJECT.id,
        state = SessionState.Working,
        cwd = cwd,
        startedAt = started,
        lastActivityAt = active,
        summary = "Fixing the login form",
        meta = meta,
    )

    private fun ComposeUiTest.show(
        cwd: java.nio.file.Path = MC_PROJECT.dir.resolve("tree"),
        meta: SessionMeta = SessionMeta(),
    ) {
        showScripted(freshSnapshot(session(cwd, meta))).pickSession("one")
        awaitTag(MissionControlTags.DETAILS)
    }

    private fun ComposeUiTest.bounds(text: String): Rect =
        onAllNodes(hasText(text) and inTag(MissionControlTags.DETAILS)).fetchSemanticsNodes().first().boundsInRoot

    private fun ComposeUiTest.has(text: String): Boolean =
        onAllNodes(hasText(text, substring = true) and inTag(MissionControlTags.DETAILS))
            .fetchSemanticsNodes()
            .isNotEmpty()

    @Test
    fun `details fields are label value pairs`() =
        runComposeUiTest {
            show()
            val pairs =
                listOf(
                    mcText("details.worktree") to "tree",
                    mcText("details.id") to "one",
                    mcText("details.startedAt") to time.format(started),
                    mcText("details.lastActivity") to time.format(active),
                )

            val labels = pairs.map { (label, _) -> bounds(label) }
            val values = pairs.map { (_, value) -> bounds(value) }

            // each value stands on the same row as its label, to the right of it
            labels.zip(values).forEach { (label, value) ->
                value.left shouldBeGreaterThanOrEqualTo label.right
                (value.center.y in label.top..label.bottom) shouldBe true
            }
            // two columns: the labels start at one x, the values at another
            labels.map { it.left }.distinct().size shouldBe 1
            values.map { it.left }.distinct().size shouldBe 1
            // the rows follow each other from top to bottom
            labels.map { it.top }.zipWithNext().forEach { (above, below) -> below shouldBeGreaterThanOrEqualTo above }
        }

    @Test
    fun `worktree is shown relative to the project`() =
        runComposeUiTest {
            show(MC_PROJECT.dir.resolve(".claude/worktrees/fix-login"))

            has(".claude/worktrees/fix-login") shouldBe true
            has(MC_PROJECT.dir.toString()) shouldBe false
        }

    @Test
    fun `worktree outside the project is shown in full`() =
        runComposeUiTest {
            show(
                java.nio.file.Path
                    .of("/Users/test/elsewhere/tree"),
            )

            has("/Users/test/elsewhere/tree") shouldBe true
        }

    @Test
    fun `worktree of the project directory is shown as a dot`() =
        runComposeUiTest {
            show(MC_PROJECT.dir)

            onAllNodes(hasText(".") and inTag(MissionControlTags.DETAILS)).fetchSemanticsNodes().size shouldBe 1
        }

    @Test
    fun `details fields stand in a framed card`() =
        runComposeUiTest {
            show()

            awaitTag(MissionControlTags.DETAILS).argbColors() shouldContain colors.surfaces.border.argb()
        }

    @Test
    fun `details show no model or mode`() =
        runComposeUiTest {
            show()

            listOf("Model", "Tryb", "Gałąź", "Branch").forEach { has(it) shouldBe false }
        }

    @Test
    fun `details show the model, permission mode and branch after the times`() =
        runComposeUiTest {
            show(meta = SessionMeta(model = "claude-opus-5", permissionMode = "plan", branch = "feat/AM-818"))

            val last = bounds(time.format(active))
            listOf(
                mcText("details.model") to "claude-opus-5",
                mcText("details.permissionMode") to "plan",
                mcText("details.branch") to "feat/AM-818",
            ).forEach { (label, value) ->
                bounds(value).left shouldBeGreaterThanOrEqualTo bounds(label).right
                (bounds(value).center.y in bounds(label).top..bounds(label).bottom) shouldBe true
                bounds(label).top shouldBeGreaterThanOrEqualTo last.bottom
            }
        }

    @Test
    fun `details show only the parts of the meta that are known`() =
        runComposeUiTest {
            show(meta = SessionMeta(model = "claude-opus-5"))

            has("claude-opus-5") shouldBe true
            has(mcText("details.permissionMode")) shouldBe false
            has(mcText("details.branch")) shouldBe false
        }
}
