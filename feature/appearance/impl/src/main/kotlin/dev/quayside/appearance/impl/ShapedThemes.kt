package dev.quayside.appearance.impl

import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeMode
import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.core.designtokens.TypographyId

/**
 * The 20 shaped themes of docs/08 in catalog order. Each row lists six colours and the shape as in the docs; the
 * other tokens come from [deriveShapedTokens]. Typography is only the suggestion shown with the theme.
 */
internal object ShapedThemes {
    val all: List<ThemeSpec> = rows().map(::toSpec)

    private fun toSpec(row: ShapedRow): ThemeSpec =
        ThemeSpec(
            id = SchemeId(row.key),
            name = row.name,
            mode = row.mode,
            colors = deriveShapedTokens(row),
            shape = row.shapeTokens,
            defaultTypography = TypographyId(row.typography),
        )

    @Suppress("LongMethod")
    private fun rows(): List<ShapedRow> =
        listOf(
            ShapedRow(
                "nordic-light",
                "Nordic Slate",
                ThemeMode.LIGHT,
                "#F4F6F8 #FFFFFF #2D3748 #718096 #3182CE #E2E8F0",
                "1 8 4/6/-1/#000000/5",
                "jakarta-jb",
            ),
            ShapedRow(
                "cyber-light",
                "Cyber Ink Light",
                ThemeMode.LIGHT,
                "#F3F4F6 #FFFFFF #111827 #6B7280 #8B5CF6 #111827",
                "2 0 none",
                "grotesk-fira",
            ),
            ShapedRow(
                "sand-light",
                "Minimal Sand",
                ThemeMode.LIGHT,
                "#FDFBF7 #F5F2EB #3A3832 #8A867A #6B705C #000000/5",
                "1 12 10/15/-3/#000000/2",
                "outfit-courier",
            ),
            ShapedRow(
                "hc-light",
                "High-Contrast Accessibility",
                ThemeMode.LIGHT_HIGH_CONTRAST,
                "#FFFFFF #FFFFFF #000000 #4A4A4A #0052CC #000000",
                "2 4 none",
                "plex",
            ),
            ShapedRow(
                "steel-light",
                "Steel & Cobalt",
                ThemeMode.LIGHT,
                "#EBEDF0 #FFFFFF #172B4D #6B778C #0052CC #C1C7D0",
                "1 4 1/3/0/#091E42/13",
                "inter-fira",
            ),
            ShapedRow(
                "teal-light",
                "Teal & Obsidian",
                ThemeMode.LIGHT,
                "#F0FDFA #FFFFFF #0F172A #64748B #0D9488 #99F6E4",
                "1 12 4/6/-1/#0D9488/10",
                "dmsans-jb",
            ),
            ShapedRow(
                "term-light",
                "Terminal Classic",
                ThemeMode.LIGHT,
                "#F0F0F0 #FFFFFF #333333 #808080 #008000 #333333",
                "1 0 none",
                "ptmono",
            ),
            ShapedRow(
                "glass-light",
                "Amethyst Glass",
                ThemeMode.LIGHT,
                "#F3F0FF #FFFFFF/60 #3B2A5C #796696 #8B5CF6 #8B5CF6/20",
                "1 16 8/32/0/#8B5CF6/10",
                "outfit-dm",
            ),
            ShapedRow(
                "oatmeal-light",
                "Oatmeal & Terracotta",
                ThemeMode.LIGHT,
                "#F9F5F0 #FFFFFF #4A3F35 #8C7E70 #D97757 #E6DACE",
                "2 16 4/0/0/#D97757/10",
                "dmsans-courier",
            ),
            ShapedRow(
                "emerald-light",
                "Corporate Emerald",
                ThemeMode.LIGHT,
                "#F0FDF4 #FFFFFF #064E3B #059669 #059669 #A7F3D0",
                "1 2 1/2/0/#064E3B/10",
                "plex",
            ),
            ShapedRow(
                "midnight-pro",
                "Midnight Pro",
                ThemeMode.DARK,
                "#0B0F19 #111827 #F3F4F6 #9CA3AF #3B82F6 #1F2937",
                "1 6 4/6/-1/#000000/50",
                "inter-jb",
            ),
            ShapedRow(
                "dracula",
                "Dracula",
                ThemeMode.DARK,
                "#282A36 #44475A #F8F8F2 #6272A4 #FF79C6 #6272A4",
                "1 8 4/6/0/#000000/30",
                "inter-fira",
            ),
            ShapedRow(
                "monokai",
                "Monokai",
                ThemeMode.DARK,
                "#272822 #1E1F1C #F8F8F2 #75715E #F92672 #3E3D32",
                "1 4 none",
                "grotesk-source",
            ),
            ShapedRow(
                "gruvbox",
                "Gruvbox Dark",
                ThemeMode.DARK,
                "#282828 #3C3836 #EBDBB2 #928374 #FE8019 #504945",
                "1 0 none",
                "plexmono",
            ),
            ShapedRow(
                "cyberpunk",
                "Cyberpunk",
                ThemeMode.DARK,
                "#000000 #090909 #E0E0E0 #FF00FF #00FFCC #333333",
                "1 0 0/10/0/#00FFCC/10",
                "grotesk-fira",
            ),
            ShapedRow(
                "nord-dark",
                "Nord Dark",
                ThemeMode.DARK,
                "#2E3440 #3B4252 #ECEFF4 #D8DEE9 #88C0D0 #434C5E",
                "1 6 4/6/0/#000000/20",
                "inter-roboto",
            ),
            ShapedRow(
                "minimal-black",
                "Minimal Black",
                ThemeMode.DARK,
                "#000000 #0A0A0A #FFFFFF #888888 #FFFFFF #333333",
                "1 4 none",
                "inter-jb",
            ),
            ShapedRow(
                "solarized-dark",
                "Solarized Dark",
                ThemeMode.DARK,
                "#002B36 #073642 #EEE8D5 #93A1A1 #2AA198 #1A4B57",
                "1 0 none",
                "firacode",
            ),
            ShapedRow(
                "developer-gray",
                "Developer Gray",
                ThemeMode.DARK,
                "#22272E #2D333B #ADBAC7 #768390 #539BF5 #444C56",
                "1 6 none",
                "system",
            ),
            ShapedRow(
                "deep-abyss",
                "Deep Abyss",
                ThemeMode.DARK,
                "#050B14 #0A1423 #CBD5E1 #64748B #F59E0B #1E293B",
                "1 12 4/12/0/#000000/50",
                "jakarta-jb",
            ),
        )
}
