package dev.quayside.core.designtokens

/** All colour tokens of a theme (docs/08), grouped to keep constructors small (ENG-C2). */
public data class ColorTokens(
    val surfaces: SurfaceColors,
    val content: ContentColors,
    val status: StatusColors,
    val terminal: TerminalColors,
)

/** Backgrounds and borders. [bg] is opaque; [panel] may be translucent and is drawn on [bg]. */
public data class SurfaceColors(
    val bg: Rgba,
    val panel: Rgba,
    val raised: Rgba,
    val sel: Rgba,
    val border: Rgba,
    val border2: Rgba,
)

/** Text and accent colours. */
public data class ContentColors(
    val text: Rgba,
    val muted: Rgba,
    val dim: Rgba,
    val accent: Rgba,
    val accentInk: Rgba,
)

/** warn = agent waits for you, work = working, ok = done, err = failed (docs/08). */
public data class StatusColors(
    val warn: Rgba,
    val warnInk: Rgba,
    val warnBg: Rgba,
    val ok: Rgba,
    val err: Rgba,
    val work: Rgba,
)

/** Terminal colours: default text on default background, and the ANSI palette drawn on [termBg]. */
public data class TerminalColors(
    val termBg: Rgba,
    val termText: Rgba,
    val ansi: AnsiColors,
)
