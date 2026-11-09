package dev.quayside.appearance.api

import dev.quayside.core.designtokens.Rgba

/** A custom scheme for contracts and tests: the colours of the built-in `graphite`, so it passes the NFR-6 audit. */
public object SampleSchemes {
    /** Id `mine`, name "Mine", dark, radius 6. */
    public fun mine(): CustomScheme =
        CustomScheme(
            id = SchemeId("mine"),
            name = "Mine",
            mode = ThemeMode.DARK,
            colors =
                SchemeColors(
                    bg = color("#111111"),
                    panel = color("#161616"),
                    text = color("#EDEDED"),
                    accent = color("#60A5FA"),
                    warn = color("#F59E0B"),
                    ok = color("#4ADE80"),
                    err = color("#F87171"),
                    work = color("#22D3EE"),
                ),
            radius = 6,
        )

    /** The opaque colour of [hex] (`#RRGGBB`). */
    public fun color(hex: String): Rgba = checkNotNull(Rgba.parse(hex)) { "bad colour in the sample" }
}
