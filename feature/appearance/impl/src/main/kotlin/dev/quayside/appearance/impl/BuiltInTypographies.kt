package dev.quayside.appearance.impl

import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec

/** The eight base typographies, then the 14 added in AM-805, in docs/08 order. */
internal object BuiltInTypographies {
    val all: List<TypographySpec> =
        listOf(
            spec("plex", "IBM Plex", "IBM Plex Sans", "IBM Plex Mono"),
            spec("geist", "Geist", "Geist", "Geist Mono"),
            spec("instrument", "Instrument", "Instrument Sans", "DM Mono"),
            spec("jetbrains", "JetBrains Mono", "JetBrains Mono", "JetBrains Mono"),
            spec("chakra", "Chakra", "Chakra Petch", "Fira Code"),
            spec("space", "Space", "Space Grotesk", "Space Mono"),
            spec("hyper", "Hyperlegible", "Atkinson Hyperlegible Next", "Atkinson Hyperlegible Mono"),
            spec("martian", "Martian Mono", "Martian Mono", "Martian Mono"),
            pair("jakarta-jb", "Plus Jakarta Sans", "JetBrains Mono"),
            pair("grotesk-fira", "Space Grotesk", "Fira Code"),
            pair("outfit-courier", "Outfit", "Courier Prime"),
            pair("outfit-dm", "Outfit", "DM Mono"),
            pair("inter-fira", "Inter", "Fira Code"),
            pair("inter-jb", "Inter", "JetBrains Mono"),
            pair("inter-roboto", "Inter", "Roboto Mono"),
            pair("dmsans-jb", "DM Sans", "JetBrains Mono"),
            pair("dmsans-courier", "DM Sans", "Courier Prime"),
            pair("grotesk-source", "Space Grotesk", "Source Code Pro"),
            spec("ptmono", "PT Mono", "PT Mono", "PT Mono"),
            spec("plexmono", "IBM Plex Mono", "IBM Plex Mono", "IBM Plex Mono"),
            spec("firacode", "Fira Code", "Fira Code", "Fira Code"),
            spec("system", "Systemowa", "SF Pro", "Menlo"),
        )

    private fun pair(
        key: String,
        ui: String,
        terminal: String,
    ): TypographySpec = spec(key, "$ui + $terminal", ui, terminal)

    private fun spec(
        key: String,
        name: String,
        ui: String,
        terminal: String,
    ): TypographySpec = TypographySpec(TypographyId(key), name, ui, terminal)
}
