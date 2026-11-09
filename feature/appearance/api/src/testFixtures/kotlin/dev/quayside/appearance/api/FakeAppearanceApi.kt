package dev.quayside.appearance.api

import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [AppearanceApi] for tests of other modules: a small fixed catalog unless one is given, the settings in
 * memory, the same validation as `impl` (ids must be in the catalog, also the day and night ones). A test sets
 * [failure] to simulate a refusal, [failSaves] to simulate a theme.json that cannot be written and [systemDark] to
 * simulate the macOS mode. Passes [AppearanceApiContract].
 */
public class FakeAppearanceApi(
    initialCatalog: ThemeCatalog = defaultCatalog(),
    initial: AppearanceSettings = AppearanceSettings(),
) : AppearanceApi {
    private val mutex = Mutex()
    private val themes = MutableStateFlow(initialCatalog)
    private val current = MutableStateFlow(initial)
    private val effective = MutableStateFlow(initial.appliedFor(null))

    @Volatile
    private var dark: Boolean? = null

    /** When set, [update] returns this error and keeps the settings. */
    @Volatile
    public var failure: AppearanceError? = null

    /** When true, [update] returns [AppearanceError.StorageFailed] and keeps the settings. */
    @Volatile
    public var failSaves: Boolean = false

    /** The macOS mode as the app sees it: true dark, false light, null unknown. Setting it republishes [applied]. */
    public var systemDark: Boolean?
        get() = dark
        set(value) {
            dark = value
            effective.value = current.value.appliedFor(value)
        }

    /** Number of accepted updates. */
    @Volatile
    public var updates: Int = 0
        private set

    override val catalog: StateFlow<ThemeCatalog> get() = themes

    override val settings: StateFlow<AppearanceSettings> get() = current

    override val applied: StateFlow<AppearanceSettings> get() = effective

    override suspend fun update(
        change: (AppearanceSettings) -> AppearanceSettings,
    ): Outcome<AppearanceSettings, AppearanceError> =
        mutex.withLock {
            val refused = failure
            val next = change(current.value).copy(customSchemes = current.value.customSchemes)
            val unknown = refused ?: refusalOf(next) ?: AppearanceError.StorageFailed.takeIf { failSaves }
            if (unknown != null) {
                Outcome.Err(unknown)
            } else {
                current.value = next
                effective.value = next.appliedFor(dark)
                updates++
                Outcome.Ok(next)
            }
        }

    override suspend fun addScheme(scheme: CustomScheme): Outcome<ThemeSpec, SchemeError> =
        mutex.withLock {
            val ratio = Wcag.contrast(scheme.colors.text, scheme.colors.bg)
            when {
                themes.value.theme(scheme.id) != null -> {
                    Outcome.Err(SchemeError.Taken(scheme.id))
                }

                ratio < Wcag.TEXT_MIN -> {
                    Outcome.Err(SchemeError.LowContrast(listOf("text/bg")))
                }

                failSaves -> {
                    Outcome.Err(SchemeError.StorageFailed)
                }

                else -> {
                    val spec = specOf(scheme)
                    themes.value = themes.value.copy(themes = themes.value.themes + spec)
                    current.value = current.value.copy(customSchemes = current.value.customSchemes + scheme)
                    Outcome.Ok(spec)
                }
            }
        }

    private fun specOf(scheme: CustomScheme): ThemeSpec =
        ThemeSpec(
            id = scheme.id,
            name = scheme.name,
            mode = scheme.mode,
            colors =
                SampleTokens.colors().recolored(
                    Palette(scheme.colors.bg.argb, scheme.colors.text.argb, scheme.colors.accent.argb),
                ),
            shape = SampleTokens.shape().copy(radius = scheme.radius),
            defaultTypography = null,
        )

    private fun refusalOf(next: AppearanceSettings): AppearanceError? {
        val catalog = themes.value
        return when {
            catalog.theme(next.scheme) == null -> AppearanceError.UnknownScheme(next.scheme)
            catalog.theme(next.dayNight.day) == null -> AppearanceError.UnknownScheme(next.dayNight.day)
            catalog.theme(next.dayNight.night) == null -> AppearanceError.UnknownScheme(next.dayNight.night)
            catalog.typography(next.typography) == null -> AppearanceError.UnknownTypography(next.typography)
            else -> null
        }
    }

    public companion object {
        /** Two themes (`plex` dark, `paper` light) and two typographies (`plex`, `geist`); no defaults suggested. */
        public fun defaultCatalog(): ThemeCatalog =
            ThemeCatalog(
                themes =
                    listOf(
                        theme("plex", "Plex Violet", ThemeMode.DARK),
                        theme("paper", "Paper Indigo", ThemeMode.LIGHT),
                    ),
                typographies =
                    listOf(
                        TypographySpec(TypographyId("plex"), "IBM Plex", "IBM Plex Sans", "IBM Plex Mono"),
                        TypographySpec(TypographyId("geist"), "Geist", "Geist", "Geist Mono"),
                    ),
            )

        /**
         * Three themes with colours that differ in every token a screen test looks at (`plex` dark, `paper` light
         * suggesting `geist`, `sunset` dark high contrast suggesting `plex`) and three typographies (`plex`, `geist`,
         * `mono` with the terminal font `JetBrains Mono`). Ids and names: `plex`/"Plex Violet", `paper`/"Paper Indigo",
         * `sunset`/"Sunset Ember".
         */
        public fun showcaseCatalog(): ThemeCatalog =
            ThemeCatalog(
                themes =
                    listOf(
                        theme("plex", "Plex Violet", ThemeMode.DARK),
                        theme(
                            "paper",
                            "Paper Indigo",
                            ThemeMode.LIGHT,
                            Palette(bg = 0xF7F4EC, text = 0x1B1A22, accent = 0x4F46E5),
                            TypographyId("geist"),
                        ),
                        theme(
                            "sunset",
                            "Sunset Ember",
                            ThemeMode.DARK_HIGH_CONTRAST,
                            Palette(bg = 0x1A0F0A, text = 0xFFE9D6, accent = 0xF97316),
                            TypographyId("plex"),
                        ),
                    ),
                typographies =
                    listOf(
                        TypographySpec(TypographyId("plex"), "IBM Plex", "IBM Plex Sans", "IBM Plex Mono"),
                        TypographySpec(TypographyId("geist"), "Geist", "Geist", "Geist Mono"),
                        TypographySpec(TypographyId("mono"), "JetBrains Mono", "JetBrains Mono", "JetBrains Mono"),
                    ),
            )

        /** Colours (ARGB) that replace the ones of the sample tokens; null keeps them. */
        private class Palette(
            val bg: Long? = null,
            val text: Long? = null,
            val accent: Long? = null,
        )

        private fun theme(
            key: String,
            name: String,
            mode: ThemeMode,
            palette: Palette = Palette(),
            suggests: TypographyId? = null,
        ): ThemeSpec =
            ThemeSpec(
                id = SchemeId(key),
                name = name,
                mode = mode,
                colors = SampleTokens.colors().recolored(palette),
                shape = SampleTokens.shape(),
                defaultTypography = suggests,
            )

        /** The sample tokens with the given surface, text and accent (and the terminal following them). */
        private fun ColorTokens.recolored(palette: Palette): ColorTokens {
            fun Long?.orElse(old: Rgba): Rgba = this?.let(SampleTokens::rgb) ?: old
            val newBg = palette.bg.orElse(surfaces.bg)
            val newText = palette.text.orElse(content.text)
            return copy(
                surfaces = surfaces.copy(bg = newBg, panel = newBg),
                content = content.copy(text = newText, accent = palette.accent.orElse(content.accent)),
                terminal = terminal.copy(termBg = newBg, termText = newText),
            )
        }
    }
}
