@file:Suppress("MagicNumber")

package dev.quayside.core.designtokens

/** Ready-made tokens for tests of this and other modules; built without any parsing logic. */
public object SampleTokens {
    private const val OPAQUE_ALPHA: Long = 0xFF000000

    /** Opaque colour from `0xRRGGBB`. */
    public fun rgb(value: Long): Rgba = Rgba(OPAQUE_ALPHA or value)

    /** A dark theme that passes every contrast and hue rule of [ContrastAudit] (the `plex` row of docs/08). */
    public fun colors(): ColorTokens =
        ColorTokens(
            surfaces =
                SurfaceColors(
                    bg = rgb(0x0B0A10),
                    panel = rgb(0x100E17),
                    raised = rgb(0x1D1B24),
                    sel = rgb(0x2B2540),
                    border = rgb(0x2A2831),
                    border2 = rgb(0x47444E),
                ),
            content =
                ContentColors(
                    text = rgb(0xE8E5F2),
                    muted = rgb(0xA7A5B0),
                    dim = rgb(0x787780),
                    accent = rgb(0xA78BFA),
                    accentInk = rgb(0x000000),
                ),
            status =
                StatusColors(
                    warn = rgb(0xFBBF24),
                    warnInk = rgb(0x000000),
                    warnBg = rgb(0x332919),
                    ok = rgb(0x86EFAC),
                    err = rgb(0xFCA5A5),
                    work = rgb(0xC4B5FD),
                ),
            terminal = TerminalColors(termBg = rgb(0x0B0A10), termText = rgb(0xE8E5F2), ansi = ansi()),
        )

    private val XTERM_HEX =
        longArrayOf(
            0x000000,
            0xCD0000,
            0x00CD00,
            0xCDCD00,
            0x1E90FF,
            0xCD00CD,
            0x00CDCD,
            0xE5E5E5,
            0x4C4C4C,
            0xFF0000,
            0x00FF00,
            0xFFFF00,
            0x4682B4,
            0xFF00FF,
            0x00FFFF,
            0xFFFFFF,
        )

    /** The xterm palette of JediTerm 3.76 as is (not corrected for any background). */
    public fun xtermAnsi(): AnsiColors = AnsiColors(XTERM_HEX.map { rgb(it) })

    /**
     * The xterm palette corrected to 4.5 to 1 on the `plex` terminal background `#0B0A10`, except index 0
     * (the background colour of a dark mode); passes [ContrastAudit] with [colors].
     */
    public fun ansi(): AnsiColors =
        AnsiColors(
            XTERM_HEX.mapIndexed { index, value ->
                when (index) {
                    1 -> rgb(0xDA4242)
                    5 -> rgb(0xD217D2)
                    8 -> rgb(0x797979)
                    else -> rgb(value)
                }
            },
        )

    /** Opaque, square, shadowless shape like the nine base themes. */
    public fun shape(radius: Int = 8): ShapeTokens =
        ShapeTokens(radius = radius, borderWidth = 1, shadow = null, backdrop = Backdrop.OPAQUE)
}
