package dev.quayside.appearance.impl

/**
 * One row of the docs/08 catalog table as written there: [colors] = `bg panel text muted accent border` (`#RRGGBB`
 * or `#RRGGBB/NN`), [shape] = `<border> <radius> <shadow>` with the shadow `none` or `y/blur/spread/#RRGGBB/NN`.
 */
data class ThemeSource(
    val key: String,
    val colors: String,
    val shape: String,
    val typography: String,
) {
    private val parts: List<String> = colors.split(' ')

    val bg: String get() = parts[0]
    val panel: String get() = parts[1]
    val text: String get() = parts[2]
    val muted: String get() = parts[3]
    val accent: String get() = parts[4]
    val border: String get() = parts[5]
    val borderWidth: Int get() = shape.split(' ')[0].toInt()
    val radius: Int get() = shape.split(' ')[1].toInt()
    val shadow: String get() = shape.split(' ')[2]

    override fun toString(): String = key
}

/** The 20 shaped themes of docs/08 in catalog order; the values are copied from the docs, not from the code. */
object ShapedThemeTable {
    @Suppress("LongMethod")
    val rows: List<ThemeSource> =
        listOf(
            ThemeSource(
                "nordic-light",
                "#F4F6F8 #FFFFFF #2D3748 #718096 #3182CE #E2E8F0",
                "1 8 4/6/-1/#000000/5",
                "jakarta-jb",
            ),
            ThemeSource("cyber-light", "#F3F4F6 #FFFFFF #111827 #6B7280 #8B5CF6 #111827", "2 0 none", "grotesk-fira"),
            ThemeSource(
                "sand-light",
                "#FDFBF7 #F5F2EB #3A3832 #8A867A #6B705C #000000/5",
                "1 12 10/15/-3/#000000/2",
                "outfit-courier",
            ),
            ThemeSource("hc-light", "#FFFFFF #FFFFFF #000000 #4A4A4A #0052CC #000000", "2 4 none", "plex"),
            ThemeSource(
                "steel-light",
                "#EBEDF0 #FFFFFF #172B4D #6B778C #0052CC #C1C7D0",
                "1 4 1/3/0/#091E42/13",
                "inter-fira",
            ),
            ThemeSource(
                "teal-light",
                "#F0FDFA #FFFFFF #0F172A #64748B #0D9488 #99F6E4",
                "1 12 4/6/-1/#0D9488/10",
                "dmsans-jb",
            ),
            ThemeSource("term-light", "#F0F0F0 #FFFFFF #333333 #808080 #008000 #333333", "1 0 none", "ptmono"),
            ThemeSource(
                "glass-light",
                "#F3F0FF #FFFFFF/60 #3B2A5C #796696 #8B5CF6 #8B5CF6/20",
                "1 16 8/32/0/#8B5CF6/10",
                "outfit-dm",
            ),
            ThemeSource(
                "oatmeal-light",
                "#F9F5F0 #FFFFFF #4A3F35 #8C7E70 #D97757 #E6DACE",
                "2 16 4/0/0/#D97757/10",
                "dmsans-courier",
            ),
            ThemeSource(
                "emerald-light",
                "#F0FDF4 #FFFFFF #064E3B #059669 #059669 #A7F3D0",
                "1 2 1/2/0/#064E3B/10",
                "plex",
            ),
            ThemeSource(
                "midnight-pro",
                "#0B0F19 #111827 #F3F4F6 #9CA3AF #3B82F6 #1F2937",
                "1 6 4/6/-1/#000000/50",
                "inter-jb",
            ),
            ThemeSource(
                "dracula",
                "#282A36 #44475A #F8F8F2 #6272A4 #FF79C6 #6272A4",
                "1 8 4/6/0/#000000/30",
                "inter-fira",
            ),
            ThemeSource("monokai", "#272822 #1E1F1C #F8F8F2 #75715E #F92672 #3E3D32", "1 4 none", "grotesk-source"),
            ThemeSource("gruvbox", "#282828 #3C3836 #EBDBB2 #928374 #FE8019 #504945", "1 0 none", "plexmono"),
            ThemeSource(
                "cyberpunk",
                "#000000 #090909 #E0E0E0 #FF00FF #00FFCC #333333",
                "1 0 0/10/0/#00FFCC/10",
                "grotesk-fira",
            ),
            ThemeSource(
                "nord-dark",
                "#2E3440 #3B4252 #ECEFF4 #D8DEE9 #88C0D0 #434C5E",
                "1 6 4/6/0/#000000/20",
                "inter-roboto",
            ),
            ThemeSource("minimal-black", "#000000 #0A0A0A #FFFFFF #888888 #FFFFFF #333333", "1 4 none", "inter-jb"),
            ThemeSource("solarized-dark", "#002B36 #073642 #EEE8D5 #93A1A1 #2AA198 #1A4B57", "1 0 none", "firacode"),
            ThemeSource("developer-gray", "#22272E #2D333B #ADBAC7 #768390 #539BF5 #444C56", "1 6 none", "system"),
            ThemeSource(
                "deep-abyss",
                "#050B14 #0A1423 #CBD5E1 #64748B #F59E0B #1E293B",
                "1 12 4/12/0/#000000/50",
                "jakarta-jb",
            ),
        )
}
