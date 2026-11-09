package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Search field button (AM-816): looks like a field, behaves like a button that never takes focus. */
@OptIn(ExperimentalTestApi::class)
class AmSearchFieldTest {
    private val colors = SampleTokens.colors()

    @Test
    fun `search field click calls onClick`() =
        runComposeUiTest {
            var clicks = 0
            setContent { PlexTheme { AmSearchField(PLACEHOLDER, "⌘K", onClick = { clicks++ }) } }

            onNodeWithText(PLACEHOLDER).performClick()

            clicks shouldBe 1
        }

    @Test
    fun `search field is a button showing the placeholder and the key cap`() =
        runComposeUiTest {
            setContent { PlexTheme { AmSearchField(PLACEHOLDER, "⌘K", onClick = {}) } }

            onNodeWithText(PLACEHOLDER)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            onNodeWithText("⌘K", useUnmergedTree = true).assertExists()
        }

    @Test
    fun `search field without key cap shows no key cap`() =
        runComposeUiTest {
            setContent { PlexTheme { AmSearchField(PLACEHOLDER, null, onClick = {}) } }

            onNodeWithText("⌘K", useUnmergedTree = true).assertDoesNotExist()
        }

    @Test
    fun `search field is not a text field`() =
        runComposeUiTest {
            setContent { PlexTheme { AmSearchField(PLACEHOLDER, "⌘K", onClick = {}) } }

            onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText),
                useUnmergedTree = true,
            ).assertCountEquals(0)
        }

    @Test
    fun `search field draws the placeholder muted on a raised field`() =
        runComposeUiTest {
            setContent {
                Scaled {
                    PlexTheme {
                        Box(Modifier.testTag(BOX).background(colors.surfaces.bg.toColor())) {
                            AmSearchField("MMMMMMMM", null, onClick = {})
                        }
                    }
                }
            }

            val shown = onNodeWithTag(BOX).pixels().distinctColors()

            shown shouldContain colors.surfaces.raised.argb32()
            shown shouldContain colors.content.muted.argb32()
        }

    @Test
    fun `search field never takes focus`() =
        runComposeUiTest {
            setContent {
                PlexTheme {
                    Column {
                        AmSearchField(PLACEHOLDER, "⌘K", onClick = {})
                        Box(Modifier.testTag(NEXT).focusable())
                    }
                }
            }

            // Tab skips the field and lands on the next focusable element
            press(Key.Tab)
            onNodeWithTag(NEXT).assertIsFocused()

            // a click does not move the focus to the field either
            onNodeWithText(PLACEHOLDER).performClick()
            onNodeWithText(PLACEHOLDER).assert(NOT_FOCUSED)
        }

    @Test
    fun `space after clicking the search field reaches the window handler`() =
        runComposeUiTest {
            var spaces = 0
            var clicks = 0
            setContent {
                PlexTheme {
                    Box(
                        Modifier
                            .testTag(WINDOW)
                            .onKeyEvent {
                                (it.key == Key.Spacebar && it.type == KeyEventType.KeyDown).also { hit ->
                                    if (hit) spaces++
                                }
                            }.focusable(),
                    ) {
                        AmSearchField(PLACEHOLDER, "⌘K", onClick = { clicks++ })
                    }
                }
            }
            onNodeWithTag(WINDOW).focus()

            onNodeWithText(PLACEHOLDER).performClick()
            press(Key.Spacebar)

            spaces shouldBe 1
            clicks shouldBe 1
            onNodeWithTag(WINDOW).assertIsFocused()
        }

    private companion object {
        const val PLACEHOLDER = "Szukaj sesji lub wpisz komendę…"
        const val BOX = "box"
        const val NEXT = "next"
        const val WINDOW = "window"
        val NOT_FOCUSED =
            SemanticsMatcher("is not focused") { it.config.getOrElse(SemanticsProperties.Focused) { false } != true }
    }
}
