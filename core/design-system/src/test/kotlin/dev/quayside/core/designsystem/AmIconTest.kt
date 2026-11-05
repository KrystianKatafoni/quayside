package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Icons are checked on pixels of a wrapper with the container background (the icon node alone has no background),
 * at 48 dp and 4 pixels per dp so that every stroke has fully covered pixels with the exact tint colour.
 */
@OptIn(ExperimentalTestApi::class)
class AmIconTest {
    private val colors = SampleTokens.colors()
    private val container = colors.surfaces.bg.argb32()
    private val tint = colors.content.accent

    private fun ComposeUiTest.setIcon(
        icon: IconKind,
        description: String? = null,
        tint: Color? = this@AmIconTest.tint.toColor(),
        textColor: Color? = null,
    ) = setContent {
        Scaled {
            PlexTheme {
                CompositionLocalProvider(LocalTextColor provides textColor) {
                    Box(Modifier.testTag(BOX).background(colors.surfaces.bg.toColor())) {
                        AmIcon(icon, description, Modifier.size(48.dp), tint)
                    }
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(IconKind::class)
    fun `every icon draws in tint colour inside its box`(icon: IconKind) =
        runComposeUiTest {
            setIcon(icon)

            val pixels = onNodeWithTag(BOX).pixels()

            pixels.width shouldBe px(48)
            pixels.distinctColors() shouldContain tint.argb32()
            (0 until pixels.width).forEach {
                pixels.argbAt(it, 0) shouldBe container
                pixels.argbAt(it, pixels.height - 1) shouldBe container
            }
            (0 until pixels.height).forEach {
                pixels.argbAt(0, it) shouldBe container
                pixels.argbAt(pixels.width - 1, it) shouldBe container
            }
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(IconKind::class, names = ["FOLDER", "REPLY", "ATTACH", "STOP", "STOPPED"])
    fun `outline icons are not filled`(icon: IconKind) =
        runComposeUiTest {
            setIcon(icon)

            val pixels = onNodeWithTag(BOX).pixels()

            // the middle of these icons is inside the outline and has no stroke
            pixels.argbAt(pixels.width / 2, pixels.height / 2) shouldBe container
        }

    @Test
    fun `ring icon draws both halves of the circle`() =
        runComposeUiTest {
            setIcon(IconKind.WAITING)

            val pixels = onNodeWithTag(BOX).pixels()
            val unit = pixels.width / GRID_UNITS

            // top (12, 3) and bottom (12, 21) of the radius 9 ring: a wrong sweep flag draws one half twice
            pixels.argbAt(12 * unit, 3 * unit) shouldBe tint.argb32()
            pixels.argbAt(12 * unit, 21 * unit) shouldBe tint.argb32()
        }

    @Test
    fun `icon without tint uses container text colour`() =
        runComposeUiTest {
            setIcon(IconKind.PLUS, tint = null)

            val pixels = onNodeWithTag(BOX).pixels()

            pixels.argbAt(pixels.width / 2, pixels.height / 2) shouldBe colors.content.text.argb32()
        }

    @Test
    fun `icon without tint follows the text colour set by its container`() =
        runComposeUiTest {
            setIcon(IconKind.PLUS, tint = null, textColor = colors.content.muted.toColor())

            val pixels = onNodeWithTag(BOX).pixels()

            pixels.argbAt(pixels.width / 2, pixels.height / 2) shouldBe colors.content.muted.argb32()
            pixels.distinctColors().contains(colors.content.text.argb32()) shouldBe false
        }

    @Test
    fun `decorative icon has no semantics node`() =
        runComposeUiTest {
            setIcon(IconKind.WAITING, description = null)

            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription), useUnmergedTree = true)
                .assertCountEquals(0)
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role), useUnmergedTree = true)
                .assertCountEquals(0)
        }

    @Test
    fun `icon with description is announced as image`() =
        runComposeUiTest {
            setIcon(IconKind.WAITING, description = "Waiting")

            onNodeWithContentDescription("Waiting")
                .assertExists()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image))
        }

    private companion object {
        const val BOX = "box"
        const val GRID_UNITS = 24
    }
}
