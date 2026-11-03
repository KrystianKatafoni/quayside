package dev.quayside.appearance.impl

import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec

/** The eight base typographies in docs/08 order. */
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
        )

    private fun spec(
        key: String,
        name: String,
        ui: String,
        terminal: String,
    ): TypographySpec = TypographySpec(TypographyId(key), name, ui, terminal)
}
