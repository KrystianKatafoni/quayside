package dev.quayside.app

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.FakeAppearanceApi
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.appearance.api.UiScale
import dev.quayside.appearance.impl.appearanceModule
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The Appearance dialog in the whole window on fakes (FR-A3, FR-A4, plan AM-803): the window is drawn in the look of
 * the settings of a [FakeAppearanceApi] over the real catalog. `MenuBar` does not work in `runComposeUiTest`, so the
 * dialog is opened with the palette command. The project `Quayside` has two sessions.
 */
@OptIn(ExperimentalTestApi::class)
class AppearanceWindowTest {
    @TempDir
    lateinit var root: Path

    private val catalog = appearanceModule().catalog
    private val api = FakeAppearanceApi(catalog)
    private val projectDir by lazy { Files.createDirectory(root.resolve("quayside")) }
    private val project by lazy { aProject(id = "project0001", dir = projectDir, displayName = "Quayside") }

    private val plex = checkNotNull(catalog.themes.firstOrNull { it.id.value == "plex" })

    /** A theme whose opaque panel differs from the one of plex, so that the mode bar tells the two apart. */
    private val other: ThemeSpec =
        checkNotNull(
            catalog.themes.firstOrNull {
                it.colors.surfaces.panel.isOpaque && it.colors.surfaces.panel != plex.colors.surfaces.panel
            },
        )

    private fun session(
        name: String,
        startedAtSecond: Long,
    ): WorldSession =
        WorldSession(SessionId(name), projectDir, SessionState.Working, name, T0.plusSeconds(startedAtSecond))

    private fun sessions(): FakeSessions =
        FakeSessions(
            SessionsWorld(
                listOf(WorldProject(project.id, project.dir)),
                listOf(session("older-work", 0), session("newer-work", 60)),
            ),
        )

    private fun ComposeUiTest.window(): CommandPaletteController =
        showWindow(
            FakeProjectsApi(initial = listOf(project)),
            sessions(),
            settings = SettingsRepos().also { it.appearance = api },
        )

    private fun ComposeUiTest.openAppearance(palette: CommandPaletteController) {
        runOnIdle { palette.open() }
        await(WindowTags.PALETTE_FIELD).performTextInput(Strings["command.appearance"])
        await(WindowTags.paletteRow("appearance.open")).performClick()
        waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
        await(WindowTags.APPEARANCE_DIALOG)
    }

    private fun ComposeUiTest.pick(tag: String) {
        await(tag).performScrollTo().performClick()
    }

    private fun ComposeUiTest.apply() {
        await(WindowTags.APPEARANCE_APPLY).performScrollTo().performClick()
        waitUntilDoesNotExist(hasTestTag(WindowTags.APPEARANCE_DIALOG))
    }

    private fun ComposeUiTest.rowHeight(): Int =
        await(WindowTags.missionRow("older-work")).fetchSemanticsNode().size.height

    private fun ComposeUiTest.selectProject() {
        await(WindowTags.projectRow("project0001")).performClick()
        await(WindowTags.missionRow("older-work"))
    }

    private fun SemanticsNodeInteraction.argbColors(): Set<Int> {
        val map = captureToImage().toPixelMap()
        return (0 until map.width).flatMapTo(HashSet()) { x -> (0 until map.height).map { y -> map[x, y].toArgb() } }
    }

    private fun ComposeUiTest.modeBarColors(): Set<Int> = await(WindowTags.MODE_BAR).argbColors()

    private fun ThemeSpec.panelArgb(): Int =
        colors.surfaces.panel
            .toColor()
            .toArgb()

    @Test
    fun `FR-A3 palette opens the Appearance dialog`() =
        runComposeUiTest {
            // Given the window with the palette closed
            val palette = window()
            onNodeWithTag(WindowTags.APPEARANCE_DIALOG).assertDoesNotExist()

            // When the user picks "Wygląd…" in the palette
            openAppearance(palette)

            // Then the dialog shows and the palette is gone
            onNodeWithTag(WindowTags.PALETTE).assertDoesNotExist()
        }

    @Test
    fun `FR-A3 apply recolors the main window`() =
        runComposeUiTest {
            // Given the window in plex
            val palette = window()
            (plex.panelArgb() in modeBarColors()) shouldBe true
            openAppearance(palette)

            // When the user picks another theme and applies
            pick(WindowTags.appearanceTheme(other.id.value))
            apply()

            // Then the settings hold it and the window stands on its panel, no longer on the plex one
            api.settings.value.scheme shouldBe other.id
            waitUntil { other.panelArgb() in modeBarColors() }
            (plex.panelArgb() in modeBarColors()) shouldBe false
        }

    @Test
    fun `FR-A3 cancel leaves the window as it was`() =
        runComposeUiTest {
            val palette = window()
            openAppearance(palette)
            pick(WindowTags.appearanceTheme(other.id.value))

            await(WindowTags.APPEARANCE_CANCEL).performScrollTo().performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.APPEARANCE_DIALOG))

            api.settings.value shouldBe AppearanceSettings()
            (plex.panelArgb() in modeBarColors()) shouldBe true
            (other.panelArgb() in modeBarColors()) shouldBe false
        }

    @Test
    fun `FR-A4 apply scale L changes the window density`() =
        runComposeUiTest {
            // Given the session rows at the scale M
            val palette = window()
            selectProject()
            val medium = rowHeight()
            openAppearance(palette)

            // When the user picks L and applies
            pick(WindowTags.appearanceScale(UiScale.L.name))
            apply()

            // Then the rows are bigger by about the factor of L
            waitUntil { rowHeight() > medium }
            val ratio = rowHeight().toFloat() / medium
            ratio shouldBeGreaterThan UiScale.L.factor - SCALE_TOLERANCE
            ratio shouldBeLessThan UiScale.L.factor + SCALE_TOLERANCE
        }

    @Test
    fun `FR-A4 apply compact density shortens the session rows of the window`() =
        runComposeUiTest {
            // Given the comfortable rows
            val palette = window()
            selectProject()
            val comfortable = rowHeight()
            openAppearance(palette)

            // When the user picks the compact density and applies
            pick(WindowTags.appearanceDensity(ListDensity.COMPACT.name))
            apply()

            // Then the rows of the window are shorter
            waitUntil { rowHeight() < comfortable }
        }

    @Test
    fun `FR-A3 preview shows the sample sessions in every status`() =
        runComposeUiTest {
            // Given the open dialog
            val palette = window()
            openAppearance(palette)

            // Then the preview has a tile for every status, announced by its text
            listOf("Czeka na odpowiedź", "Prosi o zgodę", "Pracuje", "Gotowe", "Błąd", "Zatrzymana").forEach { state ->
                waitUntilAtLeastOneExists(
                    hasContentDescription(state) and hasAnyAncestor(hasTestTag(WindowTags.APPEARANCE_PREVIEW)),
                    timeoutMillis = WAIT_MS,
                )
            }
        }

    @Test
    fun `FR-A3 sample sessions stay out of the main list`() =
        runComposeUiTest {
            // Given the project with its two sessions selected and the dialog open
            val palette = window()
            selectProject()
            openAppearance(palette)

            // Then the rows outside the preview are the two of the project, whatever the preview shows
            val row = SemanticsMatcher("is a session row") { it.tag().startsWith(WindowTags.missionRow("")) }
            val outside = row and !hasAnyAncestor(hasTestTag(WindowTags.APPEARANCE_PREVIEW))
            onAllNodes(outside).fetchSemanticsNodes().map { it.tag() }.toSet() shouldBe
                setOf(WindowTags.missionRow("older-work"), WindowTags.missionRow("newer-work"))
        }

    private fun SemanticsNode.tag(): String = config.getOrNull(SemanticsProperties.TestTag).orEmpty()

    /** Keys go to the dialog because its popup takes the focus, not because the commands check for it. */
    @Test
    fun `FR-A3 window shortcuts do nothing while the dialog is open`() =
        runComposeUiTest {
            // Given a selected project and the dialog open
            val palette = window()
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.DETAILS_EMPTY)
            openAppearance(palette)

            // When the user presses j and then g p
            listOf(Key.J, Key.G, Key.P).forEach { key ->
                onNodeWithTag(WindowTags.KEYS).performKeyInput {
                    keyDown(key)
                    keyUp(key)
                }
                waitForIdle()
            }

            // Then no session is selected, no palette opened and the dialog is still open
            onNodeWithTag(WindowTags.DETAILS_EMPTY).assertExists()
            onNodeWithTag(WindowTags.PALETTE).assertDoesNotExist()
            onNodeWithTag(WindowTags.APPEARANCE_DIALOG).assertExists()
        }

    private companion object {
        const val WAIT_MS = 5_000L

        /** The rows grow with the text and the paddings, which round to whole pixels. */
        const val SCALE_TOLERANCE = 0.05f
    }
}
