package dev.quayside.appearance.impl

import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeMode
import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.core.designtokens.Backdrop
import dev.quayside.core.designtokens.ShapeTokens

/** The nine base schemes followed by the 20 shaped themes of docs/08, in docs/08 order. */
internal object BuiltInSchemes {
    val all: List<ThemeSpec> = rows().map(::toSpec) + ShapedThemes.all

    private fun toSpec(row: SchemeRow): ThemeSpec =
        ThemeSpec(
            id = SchemeId(row.key),
            name = row.name,
            mode = row.mode,
            colors = deriveTokens(row),
            shape = ShapeTokens(row.radius, borderWidth = 1, shadow = null, backdrop = Backdrop.OPAQUE),
            defaultTypography = null,
        )

    @Suppress("LongMethod")
    private fun rows(): List<SchemeRow> =
        listOf(
            SchemeRow(
                "plex",
                "Plex Violet",
                ThemeMode.DARK,
                "#0B0A10 #100E17 #E8E5F2 #A78BFA #FBBF24 #86EFAC #FCA5A5 #C4B5FD",
                radius = 8,
            ),
            SchemeRow(
                "graphite",
                "Graphite",
                ThemeMode.DARK,
                "#111111 #161616 #EDEDED #60A5FA #F59E0B #4ADE80 #F87171 #22D3EE",
                radius = 6,
            ),
            SchemeRow(
                "paper",
                "Paper Indigo",
                ThemeMode.LIGHT,
                "#F4F6F8 #FFFFFF #111827 #4338CA #A16207 #15803D #B91C1C #4338CA",
                radius = 10,
            ),
            SchemeRow(
                "phosphor",
                "Phosphor",
                ThemeMode.DARK,
                "#050805 #070B07 #C8F7DB #39FF88 #FFB000 #A3E635 #FF6B66 #5CE1FF",
                radius = 0,
            ),
            SchemeRow(
                "synth",
                "Synth",
                ThemeMode.DARK,
                "#0A0F1F #0D1326 #E6EAF7 #22D3EE #FBBF24 #4ADE80 #FB7185 #C084FC",
                radius = 4,
            ),
            SchemeRow(
                "amber",
                "Amber CRT",
                ThemeMode.DARK,
                "#0E0B07 #13100A #F5E6C8 #FFB000 #5EEAD4 #A3E635 #FF6B57 #FDBA74",
                radius = 4,
            ),
            SchemeRow(
                "sand",
                "Sand",
                ThemeMode.LIGHT,
                "#FBF7EE #FFFDF8 #1F2328 #0F766E #A16207 #15803D #B91C1C #0F766E",
                radius = 8,
            ),
            SchemeRow(
                "rose",
                "Rose Night",
                ThemeMode.DARK,
                "#191724 #1F1D2E #E0DEF4 #C4A7E7 #F6C177 #9CCFD8 #EB6F92 #C4A7E7",
                radius = 10,
            ),
            SchemeRow(
                "contrast",
                "Wysoki kontrast",
                ThemeMode.DARK_HIGH_CONTRAST,
                "#000000 #000000 #FFFFFF #FFD400 #00E5FF #00FF6A #FF5C5C #FFD400",
                radius = 2,
            ),
        )
}
