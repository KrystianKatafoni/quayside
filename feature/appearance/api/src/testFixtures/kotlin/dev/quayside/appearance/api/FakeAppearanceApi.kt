package dev.quayside.appearance.api

import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec

/** In-memory [AppearanceApi] for tests of other modules: a small fixed catalog unless one is given. */
public class FakeAppearanceApi(
    override val catalog: ThemeCatalog = defaultCatalog(),
) : AppearanceApi {
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

        private fun theme(
            key: String,
            name: String,
            mode: ThemeMode,
        ): ThemeSpec =
            ThemeSpec(
                id = SchemeId(key),
                name = name,
                mode = mode,
                colors = SampleTokens.colors(),
                shape = SampleTokens.shape(),
                defaultTypography = null,
            )
    }
}
