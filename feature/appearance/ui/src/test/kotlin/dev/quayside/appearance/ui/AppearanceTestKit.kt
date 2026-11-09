@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.appearance.ui

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.AppearanceError
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.CustomScheme
import dev.quayside.appearance.api.DayNight
import dev.quayside.appearance.api.FakeAppearanceApi
import dev.quayside.appearance.api.FakeThemeFiles
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeFileError
import dev.quayside.appearance.api.ThemeFiles
import dev.quayside.appearance.api.UiScale
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.domain.Outcome
import dev.quayside.terminal.api.FakeTerminalSettingsRepo
import dev.quayside.terminal.api.TerminalSettings
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import java.nio.file.Path

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

/** The settings in the theme file the picker offers: every field differs from [SAVED]. */
internal val IMPORTED =
    AppearanceSettings(SUNSET, MONO, UiScale.S, ListDensity.COMFORTABLE, DayNight(true, PLEX, SUNSET))

internal val IMPORT_PATH: Path = Path.of("/themes/in.json")
internal val EXPORT_PATH: Path = Path.of("/themes/out.json")

/**
 * [ThemeFiles] over [inner] that counts the calls and can hold them until [release] completes (when [hold] is true),
 * so a test can act while a file is being read or written.
 */
internal class HeldFiles(
    private val inner: FakeThemeFiles,
    private val hold: Boolean = false,
) : ThemeFiles {
    val release = CompletableDeferred<Unit>()
    var reads: Int = 0
        private set

    override suspend fun read(source: Path): Outcome<AppearanceSettings, ThemeFileError> {
        reads++
        if (hold) release.await()
        return inner.read(source)
    }

    override suspend fun readSchemes(source: Path): Outcome<List<CustomScheme>, ThemeFileError> {
        reads++
        if (hold) release.await()
        return inner.readSchemes(source)
    }

    override suspend fun write(
        settings: AppearanceSettings,
        target: Path,
    ): Outcome<Unit, ThemeFileError> {
        if (hold) release.await()
        return inner.write(settings, target)
    }
}

/** The file dialogs of the tests: they answer with [openResult] and [saveResult] (null = cancelled). */
internal class ScriptedPicker : ThemeFilePicker {
    var openResult: Path? = IMPORT_PATH
    var saveResult: Path? = EXPORT_PATH
    var opens: Int = 0
        private set
    val suggestedNames = mutableListOf<String>()

    override suspend fun open(): Path? {
        opens++
        return openResult
    }

    override suspend fun save(suggestedName: String): Path? {
        suggestedNames += suggestedName
        return saveResult
    }
}

/** Fakes of the dialog tests: the showcase catalog and the saved settings above; [IMPORT_PATH] holds [IMPORTED]. */
internal class DialogWorld(
    hold: Boolean = false,
    appearanceStart: AppearanceSettings = SAVED,
    terminalStart: TerminalSettings = SAVED_TERMINAL,
    holdFiles: Boolean = false,
) {
    val fake = FakeAppearanceApi(FakeAppearanceApi.showcaseCatalog(), appearanceStart)
    val appearance = HeldAppearance(fake, hold)
    val terminal = FakeTerminalSettingsRepo(terminalStart)
    val files = FakeThemeFiles().also { it.put(IMPORT_PATH, IMPORTED) }
    val heldFiles = HeldFiles(files, holdFiles)
    val picker = ScriptedPicker()

    /** The controller over these fakes, running in [scope]. */
    fun controller(scope: CoroutineScope) = AppearanceController(appearance, terminal, heldFiles, picker, scope)
}

/** The controller over the fakes of [world], running in the background scope of the test. */
internal fun TestScope.controllerOf(world: DialogWorld) = world.controller(backgroundScope)

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
