package dev.quayside.app

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.DayNight
import dev.quayside.appearance.api.FakeAppearanceApi
import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.appearance.impl.builtInCatalog
import dev.quayside.core.designsystem.toColor
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.sessions.api.FakeSessions
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Day and night in the whole window on fakes (FR-A5, plan AM-804): the window is drawn in the look of `applied`, which
 * follows the macOS mode the fake `systemDark` stands for. The mode bar is the probe, like in `AppearanceWindowTest`.
 */
@OptIn(ExperimentalTestApi::class)
class DayNightWindowTest {
    private val catalog = builtInCatalog()
    private val plex = checkNotNull(catalog.themes.firstOrNull { it.id.value == "plex" })

    /** A theme whose opaque panel differs from the one of plex, so that the mode bar tells the two apart. */
    private val day: ThemeSpec =
        checkNotNull(
            catalog.themes.firstOrNull {
                it.colors.surfaces.panel.isOpaque && it.colors.surfaces.panel != plex.colors.surfaces.panel
            },
        )

    private val api =
        FakeAppearanceApi(catalog, AppearanceSettings(dayNight = DayNight(true, day.id, plex.id)))

    private fun ComposeUiTest.window() {
        showWindow(
            FakeProjectsApi(),
            FakeSessions(),
            settings = SettingsRepos().also { it.appearance = api },
        )
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

    private fun ComposeUiTest.setMode(dark: Boolean?) {
        runOnIdle { api.systemDark = dark }
    }

    @Test
    fun `macOS turning dark recolors the main window`() =
        runComposeUiTest {
            // Given macOS light: the window stands on the day theme
            api.systemDark = false
            window()
            waitUntil(timeoutMillis = WAIT_MS) { day.panelArgb() in modeBarColors() }
            (plex.panelArgb() in modeBarColors()) shouldBe false

            // When macOS turns dark
            setMode(true)

            // Then the window is drawn in the night theme, while the saved scheme stays untouched
            waitUntil(timeoutMillis = WAIT_MS) { plex.panelArgb() in modeBarColors() }
            (day.panelArgb() in modeBarColors()) shouldBe false
            api.settings.value.scheme shouldBe AppearanceSettings().scheme
        }

    @Test
    fun `macOS turning light again brings the day theme back`() =
        runComposeUiTest {
            api.systemDark = true
            window()
            waitUntil(timeoutMillis = WAIT_MS) { plex.panelArgb() in modeBarColors() }

            setMode(false)

            waitUntil(timeoutMillis = WAIT_MS) { day.panelArgb() in modeBarColors() }
            (plex.panelArgb() in modeBarColors()) shouldBe false
        }

    @Test
    fun `an unknown mode draws the saved scheme`() =
        runComposeUiTest {
            api.systemDark = false
            window()
            waitUntil(timeoutMillis = WAIT_MS) { day.panelArgb() in modeBarColors() }

            setMode(null)

            waitUntil(timeoutMillis = WAIT_MS) { plex.panelArgb() in modeBarColors() }
        }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
