package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The component `AmDropdown` seen from outside (AM-817, FR-A1, NFR-6): the closed field, the open list with its
 * options, the selection, the modal layer and the colours. Keys are in [AmDropdownKeysTest].
 */
@OptIn(ExperimentalTestApi::class)
class AmDropdownTest {
    private val plex = SampleTokens.colors()

    @Test
    fun `field shows the selected label`() =
        runComposeUiTest {
            show(DropdownRig(selected = 2))

            onNode(hasTestTag(FIELD) and hasText("Plan", substring = true)).assertExists()
            onNodeWithTag(AmDropdownTags.LIST).assertDoesNotExist()
        }

    @Test
    fun `field shows the placeholder when none is selected`() =
        runComposeUiTest {
            show(DropdownRig(selected = -1, placeholder = "Pick one"))

            onNode(hasTestTag(FIELD) and hasText("Pick one", substring = true)).assertExists()
        }

    @Test
    fun `field has role DropdownList, the label as description and the value as state`() =
        runComposeUiTest {
            show(DropdownRig(selected = 1, label = "Permission mode"))

            onNodeWithTag(FIELD)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
                .assertContentDescriptionEquals("Permission mode")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Accept edits"))
        }

    @Test
    fun `the state follows the selected value`() =
        runComposeUiTest {
            val rig = DropdownRig(selected = 0)
            show(rig)

            runOnIdle { rig.selected = 3 }
            waitForIdle()

            onNodeWithTag(FIELD)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Bypass"))
        }

    @Test
    fun `click opens the list with every option`() =
        runComposeUiTest {
            show(DropdownRig())

            openByClick()

            MODES.indices.forEach { index ->
                onNode(hasTestTag(AmDropdownTags.option(index)) and hasText(MODES[index], substring = true))
                    .assertExists()
            }
            onNodeWithTag(AmDropdownTags.option(MODES.size)).assertDoesNotExist()
        }

    @Test
    fun `the selected option is marked selected and has a check mark`() =
        runComposeUiTest {
            show(DropdownRig(selected = 2))

            openByClick()

            onNodeWithTag(AmDropdownTags.option(2)).assertIsSelected()
            listOf(0, 1, 3).forEach { onNodeWithTag(AmDropdownTags.option(it)).assertIsNotSelected() }
            onAllNodesWithText("✓", useUnmergedTree = true).assertCountEquals(1)
            onNode(hasText("✓") and hasAnyAncestor(hasTestTag(AmDropdownTags.option(2))), useUnmergedTree = true)
                .assertExists()
        }

    @Test
    fun `with no selection no option is marked`() =
        runComposeUiTest {
            show(DropdownRig(selected = -1, placeholder = "Pick one"))

            openByClick()

            MODES.indices.forEach { onNodeWithTag(AmDropdownTags.option(it)).assertIsNotSelected() }
            onAllNodesWithText("✓", useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `options are one selectable group`() =
        runComposeUiTest {
            show(DropdownRig())

            openByClick()

            onNodeWithTag(
                AmDropdownTags.LIST,
            ).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        }

    @Test
    fun `click on an option selects it and closes the list`() =
        runComposeUiTest {
            val rig = DropdownRig(selected = 0)
            show(rig)
            openByClick()

            onNodeWithTag(AmDropdownTags.option(2)).performClick()
            awaitClosed()

            rig.picks shouldBe listOf(2)
            onNode(hasTestTag(FIELD) and hasText("Plan", substring = true)).assertExists()
        }

    @Test
    fun `click on the selected option does not call onSelect and closes the list`() =
        runComposeUiTest {
            val rig = DropdownRig(selected = 1)
            show(rig)
            openByClick()

            onNodeWithTag(AmDropdownTags.option(1)).performClick()
            awaitClosed()

            rig.picks.shouldBeEmpty()
        }

    @Test
    fun `click outside closes the list with no change`() =
        runComposeUiTest {
            val rig = DropdownRig(selected = 1)
            show(rig)
            openByClick()

            onNodeWithTag(OUTSIDE).performClick()
            awaitClosed()

            rig.picks.shouldBeEmpty()
            onNode(hasTestTag(FIELD) and hasText("Accept edits", substring = true)).assertExists()
        }

    @Test
    fun `the list can be opened again after a close`() =
        runComposeUiTest {
            val rig = DropdownRig(selected = 0)
            show(rig)
            openByClick()
            onNodeWithTag(OUTSIDE).performClick()
            awaitClosed()

            openByClick()

            onNodeWithTag(AmDropdownTags.option(0)).assertExists()
        }

    @Test
    fun `disabled field does not open`() =
        runComposeUiTest {
            val rig = DropdownRig()
            rig.enabled = false
            show(rig)

            onNodeWithTag(FIELD).performClick()
            waitForIdle()

            onNodeWithTag(AmDropdownTags.LIST).assertDoesNotExist()
            onNodeWithTag(FIELD).assertIsNotEnabled()
            rig.picks.shouldBeEmpty()
        }

    @Test
    fun `disabling closes the open list`() =
        runComposeUiTest {
            val rig = DropdownRig()
            show(rig)
            openByClick()

            runOnIdle { rig.enabled = false }
            awaitClosed()

            // And enabling again does not bring the list back: the close was a real dismissal
            runOnIdle { rig.enabled = true }
            waitForIdle()
            onNodeWithTag(AmDropdownTags.LIST).assertDoesNotExist()
            rig.picks.shouldBeEmpty()
        }

    @Test
    fun `the list is at least as wide as a wide field`() =
        runComposeUiTest {
            setContent {
                PlexTheme {
                    AmDropdown(
                        items = AmDropdownItems(listOf("A", "B"), selected = 0),
                        label = "Mode",
                        onSelect = {},
                        modifier = Modifier.testTag(FIELD).fillMaxWidth(),
                    )
                }
            }
            openByClick()

            val field = onNodeWithTag(FIELD).getUnclippedBoundsInRoot()
            val list = onNodeWithTag(AmDropdownTags.LIST).getUnclippedBoundsInRoot()

            list.width.value shouldBeGreaterThanOrEqualTo field.width.value
        }

    @Test
    fun `empty list does not open`() =
        runComposeUiTest {
            show(DropdownRig(labels = emptyList(), selected = 0, placeholder = "Nothing"))

            onNodeWithTag(FIELD).performClick()
            waitForIdle()

            onNodeWithTag(AmDropdownTags.LIST).assertDoesNotExist()
            onNode(hasTestTag(FIELD) and hasText("Nothing", substring = true)).assertExists()
        }

    @Test
    fun `open list counts as a modal layer and stops counting when closed`() =
        runComposeUiTest {
            val rig = DropdownRig()
            show(rig)
            rig.gate.active shouldBe false

            openByClick()
            rig.gate.active shouldBe true

            onNodeWithTag(OUTSIDE).performClick()
            awaitClosed()
            rig.gate.active shouldBe false
        }

    @Test
    fun `list in a dialog adds a second layer`() =
        runComposeUiTest {
            // Given a dialog layer (a scrim) next to the dropdown, both counted by one gate
            val gate = AmModalGate()
            var dialog by mutableStateOf(value = true)
            setContent {
                CompositionLocalProvider(LocalAmModalGate provides gate) {
                    PlexTheme {
                        if (dialog) Box(Modifier.testTag("dialog").amScrim())
                        AmDropdown(
                            items = AmDropdownItems(MODES, selected = 0),
                            label = "Mode",
                            onSelect = {},
                            modifier = Modifier.testTag(FIELD),
                        )
                    }
                }
            }
            gate.active shouldBe true

            // When the list opens and then the dialog leaves
            openByClick()
            runOnIdle { dialog = false }
            waitForIdle()

            // Then the list alone keeps the gate active, and the gate is free after the list closes
            gate.active shouldBe true
            onNodeWithTag(AmDropdownTags.option(0)).performClick()
            awaitClosed()
            gate.active shouldBe false
        }

    @Test
    fun `a list longer than 8 rows shows 8 and scrolls to the rest`() =
        runComposeUiTest {
            val many = (0 until 12).map { "Voice $it" }
            val rig = DropdownRig(labels = many, selected = 0)
            show(rig)
            openByClick()

            // The pitch of the rows (row plus the space the focus frame reserves) is the distance of two tops
            val pitch =
                onNodeWithTag(AmDropdownTags.option(1)).getUnclippedBoundsInRoot().top.value -
                    onNodeWithTag(AmDropdownTags.option(0)).getUnclippedBoundsInRoot().top.value
            val list = onNodeWithTag(AmDropdownTags.LIST).getUnclippedBoundsInRoot().height.value
            // exactly 8 rows: a limit of 7 or of 9 rows is half a pitch or more away
            list shouldBeGreaterThanOrEqualTo pitch * 7.5f
            list shouldBeLessThan pitch * 8.5f

            onNodeWithTag(AmDropdownTags.option(11)).performScrollTo().performClick()
            awaitClosed()
            rig.picks shouldBe listOf(11)
        }

    @Test
    fun `selected option row is drawn in sel`() =
        runComposeUiTest {
            setContent {
                Scaled {
                    PlexTheme {
                        AmDropdown(
                            items = AmDropdownItems(listOf("MMMM", "MMMM", "MMMM"), selected = 1),
                            label = "Mode",
                            onSelect = {},
                            modifier = Modifier.testTag(FIELD),
                        )
                    }
                }
            }
            openByClick()

            val selected = onNodeWithTag(AmDropdownTags.option(1)).pixels().distinctColors()
            val other = onNodeWithTag(AmDropdownTags.option(0)).pixels().distinctColors()

            selected shouldContain plex.surfaces.sel.argb32()
            other shouldNotContain plex.surfaces.sel.argb32()
            other shouldContain plex.surfaces.raised.argb32()
        }

    @Test
    fun `field is drawn in raised`() =
        runComposeUiTest {
            setContent {
                Scaled {
                    PlexTheme {
                        Box(Modifier.testTag("wrap").background(plex.surfaces.bg.toColor()).padding(4.dp)) {
                            AmDropdown(
                                items = AmDropdownItems(listOf("MMMM"), selected = 0),
                                label = "Mode",
                                onSelect = {},
                            )
                        }
                    }
                }
            }

            val shown = onNodeWithTag("wrap").pixels().distinctColors()

            shown shouldContain plex.surfaces.raised.argb32()
            shown shouldContain plex.content.text.argb32()
        }

    @Test
    fun `disabled field draws its label in dim and not in text`() =
        runComposeUiTest {
            setContent {
                Scaled {
                    PlexTheme {
                        Box(Modifier.testTag("wrap").background(plex.surfaces.bg.toColor()).padding(4.dp)) {
                            AmDropdown(
                                items = AmDropdownItems(listOf("MMMM"), selected = 0),
                                label = "Mode",
                                onSelect = {},
                                enabled = false,
                            )
                        }
                    }
                }
            }

            val shown = onNodeWithTag("wrap").pixels().distinctColors()

            shown shouldContain plex.content.dim.argb32()
            shown shouldContain plex.surfaces.raised.argb32()
        }
}
