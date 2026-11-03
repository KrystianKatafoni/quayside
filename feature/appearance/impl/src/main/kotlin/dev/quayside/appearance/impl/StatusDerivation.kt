@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.quayside.appearance.impl

import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.hueDistance

/** The `warn`, `ok` and `err` colours picked for one theme. */
internal data class StatusTriple(
    val warn: Rgba,
    val ok: Rgba,
    val err: Rgba,
)

private const val MIN_HUE_DISTANCE = 30.0
private const val OPAQUE = 0xFF000000L

private fun colors(vararg hex: Long): List<Rgba> = hex.map { Rgba(OPAQUE or it) }

private val LIGHT_WARN = colors(0xA16207, 0x0F766E)
private val LIGHT_OK = colors(0x15803D, 0x4D7C0F)
private val LIGHT_ERR = colors(0xB91C1C, 0xBE185D)
private val DARK_WARN = colors(0xF59E0B, 0xFACC15, 0x5EEAD4)
private val DARK_OK = colors(0x4ADE80, 0xA3E635)
private val DARK_ERR = colors(0xF87171, 0xFF6E40, 0xFB7185)

private fun conflicts(
    first: Rgba,
    second: Rgba,
): Boolean = hueDistance(first, second)?.let { it < MIN_HUE_DISTANCE } ?: false

/**
 * First candidate triple (warn, then ok, then err, each list in order of preference) that meets the hue rules of
 * the contrast audit: warn, ok and err are at least 30 degrees from [accent], and warn from ok and from err.
 * Greys never conflict.
 *
 * @throws IllegalStateException when no candidate triple fits [accent]. It is thrown only for built-in catalogue
 *   constants; theme.json (AM-804) needs an Outcome instead.
 */
internal fun deriveStatus(
    dark: Boolean,
    accent: Rgba,
): StatusTriple {
    val warns = if (dark) DARK_WARN else LIGHT_WARN
    val oks = if (dark) DARK_OK else LIGHT_OK
    val errs = if (dark) DARK_ERR else LIGHT_ERR
    return warns
        .filterNot { conflicts(it, accent) }
        .flatMap { warn ->
            val fits = { c: Rgba -> !conflicts(c, accent) && !conflicts(warn, c) }
            oks.filter(fits).flatMap { ok -> errs.filter(fits).map { err -> StatusTriple(warn, ok, err) } }
        }.firstOrNull() ?: error("no status colour triple fits the accent")
}
