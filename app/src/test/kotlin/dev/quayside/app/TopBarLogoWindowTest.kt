package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** The logo and the wordmark in the top bar on fakes (AM-822, ADR 0030); split from [TopBarWindowTest]. */
@OptIn(ExperimentalTestApi::class)
class TopBarLogoWindowTest {
    @TempDir
    lateinit var root: Path

    private val world by lazy { TopBarWorld(root) }

    @Test
    fun `top bar starts with the logo before the crumbs`() =
        runComposeUiTest {
            with(world) {
                window()
                await(WindowTags.CRUMBS)

                val logo = onNodeWithContentDescription(Strings["topBar.logo"]).fetchSemanticsNode().boundsInRoot
                val root = rootCrumb().fetchSemanticsNode().boundsInRoot

                logo.left shouldBeLessThanOrEqualTo root.left
                logo.right shouldBeLessThanOrEqualTo root.left
            }
        }

    @Test
    fun `top bar logo has the accessibility label`() =
        runComposeUiTest {
            with(world) {
                window()
                await(WindowTags.CRUMBS)

                onAllNodesWithContentDescription(Strings["topBar.logo"], useUnmergedTree = true).assertCountEquals(1)
            }
        }

    @Test
    fun `top bar logo never takes focus`() =
        runComposeUiTest {
            with(world) {
                window()
                await(WindowTags.CRUMBS)
                onNodeWithTag(WindowTags.KEYS).performSemanticsAction(SemanticsActions.RequestFocus)

                // Tab without opening a dialog: a focusable logo would be reached before or after the controls
                press(Key.Tab)
                press(Key.Tab)

                val logo = onNodeWithContentDescription(Strings["topBar.logo"])
                logo.assert(NOT_FOCUSED)
                logo.assert(SemanticsMatcher("has no click action") { SemanticsActions.OnClick !in it.config })
            }
        }

    @Test
    fun `root crumb is the wordmark in both modes`() =
        runComposeUiTest {
            with(world) {
                window()
                awaitText(WindowTags.CRUMBS, "Quayside")

                // Wordmark: Plex Sans SemiBold 15 sp as one node; a plain crumb is 13 sp regular
                crumbStyle("Quayside").fontSize shouldBe 15.sp
                crumbStyle("Quayside").fontWeight shouldBe FontWeight.SemiBold

                terminalMode()
                awaitText(WindowTags.CRUMBS, "fix-login")

                crumbStyle("Quayside").fontSize shouldBe 15.sp
                crumbStyle("Quayside").fontWeight shouldBe FontWeight.SemiBold
            }
        }

    @Test
    fun `space after clicking the logo reaches the window`() =
        runComposeUiTest {
            with(world) {
                window()
                await(WindowTags.BELL).performClick()
                await(WindowTags.missionRow("their-ask"))

                // When the user clicks the logo and presses space, the preview opens: the logo did not take the key
                onNodeWithContentDescription(Strings["topBar.logo"]).performClick()
                press(Key.J)
                press(Key.Spacebar)

                await(WindowTags.PREVIEW)
                onNodeWithContentDescription(Strings["topBar.logo"]).assert(NOT_FOCUSED)
            }
        }

    private fun ComposeUiTest.rootCrumb() =
        onAllNodes(hasText("Quayside") and hasAnyAncestor(hasTestTag(WindowTags.CRUMBS)), useUnmergedTree = true)
            .onFirst()

    private companion object {
        val NOT_FOCUSED =
            SemanticsMatcher("is not focused") { it.config.getOrElse(SemanticsProperties.Focused) { false } != true }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.crumbStyle(text: String): TextStyle {
    val node =
        onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(WindowTags.CRUMBS)), useUnmergedTree = true)
            .onFirst()
            .fetchSemanticsNode()
    val results = mutableListOf<TextLayoutResult>()
    checkNotNull(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)).action?.invoke(results)
    return results.first().layoutInput.style
}
