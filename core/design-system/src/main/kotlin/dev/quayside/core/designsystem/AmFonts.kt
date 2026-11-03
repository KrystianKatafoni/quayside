package dev.quayside.core.designsystem

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/** One font file of a family: [path] under `fonts/`, the [weight] it is used for, and whether the file is variable. */
private class FontFile(
    val path: String,
    val weight: Int,
    val variable: Boolean,
)

private fun variable(
    path: String,
    vararg weights: Int,
): List<FontFile> = weights.map { FontFile(path, it, variable = true) }

private fun static(
    path: String,
    weight: Int,
): FontFile = FontFile(path, weight, variable = false)

private val FILES: Map<String, List<FontFile>> =
    mapOf(
        "IBM Plex Sans" to variable("ibmplexsans/IBMPlexSans[wdth,wght].ttf", 400, 600),
        "IBM Plex Mono" to
            listOf(
                static("ibmplexmono/IBMPlexMono-Regular.ttf", 400),
                static("ibmplexmono/IBMPlexMono-SemiBold.ttf", 600),
            ),
        "Geist" to variable("geist/Geist[wght].ttf", 400, 600),
        "Geist Mono" to variable("geistmono/GeistMono[wght].ttf", 400, 600),
        "Instrument Sans" to variable("instrumentsans/InstrumentSans[wdth,wght].ttf", 400, 600),
        "DM Mono" to
            listOf(static("dmmono/DMMono-Regular.ttf", 400), static("dmmono/DMMono-Medium.ttf", 500)),
        "JetBrains Mono" to variable("jetbrainsmono/JetBrainsMono[wght].ttf", 400, 600),
        "Chakra Petch" to
            listOf(
                static("chakrapetch/ChakraPetch-Regular.ttf", 400),
                static("chakrapetch/ChakraPetch-SemiBold.ttf", 600),
            ),
        "Fira Code" to variable("firacode/FiraCode[wght].ttf", 400, 600),
        "Space Grotesk" to variable("spacegrotesk/SpaceGrotesk[wght].ttf", 400, 600),
        "Space Mono" to
            listOf(static("spacemono/SpaceMono-Regular.ttf", 400), static("spacemono/SpaceMono-Bold.ttf", 700)),
        "Atkinson Hyperlegible Next" to
            variable("atkinsonhyperlegiblenext/AtkinsonHyperlegibleNext[wght].ttf", 400, 600),
        "Atkinson Hyperlegible Mono" to
            variable("atkinsonhyperlegiblemono/AtkinsonHyperlegibleMono[wght].ttf", 400, 600),
        "Martian Mono" to variable("martianmono/MartianMono[wdth,wght].ttf", 400, 600),
    )

private fun FontFile.toFont(): Font =
    if (variable) {
        Font(
            resource = "fonts/$path",
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    } else {
        Font(resource = "fonts/$path", weight = FontWeight(weight))
    }

/**
 * Bundled OFL fonts (ADR 0021), looked up by family name as in TypographySpec. Never throws: a family is built on the
 * first lookup and cached. Thread-safe: the cache is guarded by `synchronized`.
 */
public object AmFonts {
    private val cache = HashMap<String, FontFamily>()

    /** The 14 bundled family names, spelled as in docs/08. */
    public val families: Set<String> = FILES.keys

    /** Every font resource path under the classpath (`fonts/<directory>/<file>`), each once. */
    internal val resourcePaths: List<String> =
        FILES.values.flatMap { files -> files.map { "fonts/${it.path}" } }.distinct()

    /** The family called [name], or null when it is not bundled (e.g. a system font). */
    public fun family(name: String): FontFamily? {
        val files = FILES[name] ?: return null
        return synchronized(cache) { cache.getOrPut(name) { FontFamily(files.map { it.toFont() }) } }
    }
}
