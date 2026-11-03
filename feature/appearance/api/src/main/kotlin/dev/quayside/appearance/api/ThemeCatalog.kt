package dev.quayside.appearance.api

import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec

/**
 * Themes and typographies in display order. Non-empty, unique ids, the defaults present, every
 * `defaultTypography` present. Immutable, safe to use from any thread.
 *
 * Built from the built-in data; catalogs assembled from user files (AM-804) must be validated by a
 * parser returning `T?` / Outcome first. The explicit non-empty check is a defence that gives a
 * clearer message than the missing-default checks.
 *
 * @throws IllegalArgumentException when a list is empty, ids are duplicated, a default scheme or
 *   typography is missing, or a `defaultTypography` is outside [typographies].
 */
public data class ThemeCatalog(
    val themes: List<ThemeSpec>,
    val typographies: List<TypographySpec>,
) {
    init {
        val themeIds = themes.map { it.id }
        val typographyIds = typographies.map { it.id }
        require(themes.isNotEmpty() && typographies.isNotEmpty()) { "catalog must not be empty" }
        require(themeIds.toSet().size == themeIds.size) { "duplicate scheme ids" }
        require(typographyIds.toSet().size == typographyIds.size) { "duplicate typography ids" }
        require(DEFAULT_SCHEME in themeIds) { "default scheme missing" }
        require(DEFAULT_TYPOGRAPHY in typographyIds) { "default typography missing" }
        require(themes.all { it.defaultTypography == null || it.defaultTypography in typographyIds }) {
            "default typography outside the catalog"
        }
    }

    /** The theme with [id], or null. */
    public fun theme(id: SchemeId): ThemeSpec? = themes.firstOrNull { it.id == id }

    /** The typography with [id], or null. */
    public fun typography(id: TypographyId): TypographySpec? = typographies.firstOrNull { it.id == id }

    public companion object {
        /** `plex`. */
        public val DEFAULT_SCHEME: SchemeId = SchemeId("plex")

        /** `plex`. */
        public val DEFAULT_TYPOGRAPHY: TypographyId = TypographyId("plex")
    }
}
