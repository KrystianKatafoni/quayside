@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.appearance.ui

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.AppearanceError
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.FakeAppearanceApi
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.UiScale
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.domain.Outcome
import dev.quayside.terminal.api.FakeTerminalSettingsRepo
import dev.quayside.terminal.api.TerminalSettings
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

internal val PLEX = SchemeId("plex")
internal val PAPER = SchemeId("paper")
internal val SUNSET = SchemeId("sunset")
internal val PLEX_TYPOGRAPHY = TypographyId("plex")
internal val GEIST = TypographyId("geist")
internal val MONO = TypographyId("mono")

/** The settings the dialog tests start from: not the defaults, so that a draft built from defaults is seen. */
internal val SAVED =
    AppearanceSettings(PAPER, GEIST, UiScale.L, ListDensity.COMPACT)

/** The terminal settings the dialog tests start from. */
internal val SAVED_TERMINAL = TerminalSettings(fontFamily = "Menlo", fontSize = 17, scrollbackLines = 5_000)

/**
 * An [AppearanceApi] over [inner] that counts the calls of `update` and can hold them until [release] completes
 * (when [hold] is true), so a test can act while a change is being applied.
 */
internal class HeldAppearance(
    private val inner: FakeAppearanceApi,
    private val hold: Boolean = false,
) : AppearanceApi by inner {
    val release = CompletableDeferred<Unit>()
    var calls: Int = 0
        private set

    override suspend fun update(
        change: (AppearanceSettings) -> AppearanceSettings,
    ): Outcome<AppearanceSettings, AppearanceError> {
        calls++
        if (hold) release.await()
        return inner.update(change)
    }
}

/** Fakes of the dialog tests: the showcase catalog and the saved settings above. */
internal class DialogWorld(
    hold: Boolean = false,
    appearanceStart: AppearanceSettings = SAVED,
    terminalStart: TerminalSettings = SAVED_TERMINAL,
) {
    val fake = FakeAppearanceApi(FakeAppearanceApi.showcaseCatalog(), appearanceStart)
    val appearance = HeldAppearance(fake, hold)
    val terminal = FakeTerminalSettingsRepo(terminalStart)
}

/** The controller over the fakes of [world], running in the background scope of the test. */
internal fun TestScope.controllerOf(world: DialogWorld) =
    AppearanceController(world.appearance, world.terminal, backgroundScope)

/** The controller of [world] with the dialog open. */
internal fun TestScope.opened(world: DialogWorld = DialogWorld()): AppearanceController =
    controllerOf(world).also {
        it.open()
        runCurrent()
    }

/** Sends [intents] and lets everything they scheduled run. */
internal fun TestScope.send(
    controller: AppearanceController,
    vararg intents: AppearanceIntent,
) {
    intents.forEach(controller::onIntent)
    runCurrent()
}

/** The draft of the open dialog. */
internal fun AppearanceController.draft(): AppearanceDraft = state.value.shouldNotBeNull().draft
