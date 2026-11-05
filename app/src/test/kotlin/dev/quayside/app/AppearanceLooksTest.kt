@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.app

import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.FakeAppearanceApi
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.UiScale
import dev.quayside.appearance.impl.appearanceModule
import dev.quayside.core.designsystem.AmDensity
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.terminal.api.TerminalSettings
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * What the app does with an applied choice (plan AM-803): the looks of the windows follow `AppearanceApi.settings`,
 * and the terminal gets the colours and the font of the applied theme and typography.
 */
class AppearanceLooksTest {
    private val catalog = appearanceModule().catalog

    /** A scope that runs at once, like the one `TerminalLookTest` uses for `typographySettings`. */
    private fun TestScope.eager() = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

    private fun look(
        scheme: String = "plex",
        typography: String = "plex",
    ) = catalog.lookOf(AppearanceSettings(SchemeId(scheme), TypographyId(typography)))

    @Test
    fun `looks start with the look of the current settings`() =
        runTest {
            val scope = eager()
            val api = FakeAppearanceApi(catalog, AppearanceSettings(SchemeId("paper"), uiScale = UiScale.L))

            val looks = appLooks(api, scope)

            looks.value shouldBe catalog.lookOf(api.settings.value)
            scope.cancel()
        }

    @Test
    fun `an applied change reaches the looks`() =
        runTest {
            val scope = eager()
            val api = FakeAppearanceApi(catalog)
            val looks = appLooks(api, scope)

            api.update { it.copy(scheme = SchemeId("paper"), density = ListDensity.COMPACT, uiScale = UiScale.S) }

            looks.value.colors shouldBe catalog.theme(SchemeId("paper"))?.colors
            looks.value.density shouldBe AmDensity.COMPACT
            looks.value.scale shouldBe UiScale.S
            scope.cancel()
        }

    @Test
    fun `terminal settings follow the applied typography font`() =
        runTest {
            // Given the plex typography applied and no own terminal font
            val scope = eager()
            val looks = MutableStateFlow(look(typography = "plex"))
            val stored = MutableStateFlow(TerminalSettings(fontSize = 15))
            val applied = appliedTerminalSettings(stored, looks, scope)
            applied.value shouldBe TerminalSettings(fontSize = 15, fontFamily = "IBM Plex Mono")

            // When another typography is applied
            looks.value = look(typography = "geist")

            // Then new tabs get its terminal font, with the other settings intact
            applied.value shouldBe TerminalSettings(fontSize = 15, fontFamily = "Geist Mono")

            // And an own font of the user wins over every typography
            stored.value = TerminalSettings(fontSize = 15, fontFamily = "Menlo")
            applied.value shouldBe TerminalSettings(fontSize = 15, fontFamily = "Menlo")
            looks.value = look(typography = "plex")
            applied.value.fontFamily shouldBe "Menlo"
            scope.cancel()
        }

    @Test
    fun `terminal colors follow the applied theme`() =
        runTest {
            val scope = eager()
            val looks = MutableStateFlow(look(scheme = "plex"))
            val colors = appliedTerminalColors(looks, scope)
            colors.value shouldBe catalog.theme(SchemeId("plex"))?.colors?.terminal

            looks.value = look(scheme = "paper")

            colors.value shouldBe catalog.theme(SchemeId("paper"))?.colors?.terminal
            (colors.value != catalog.theme(SchemeId("plex"))?.colors?.terminal) shouldBe true
            scope.cancel()
        }
}
