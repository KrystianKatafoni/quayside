package dev.quayside.appearance.impl

import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix

private const val MIX_STEPS = 100

/**
 * Smallest whole `p` in 0..100 for which `mix(toward, p)` has at least [minimum] contrast with every one of the
 * opaque [backgrounds]; `p = 0` returns the colour unchanged, no such `p` returns [toward]. The hue stays the
 * receiver's until [toward] takes over, so a corrected colour keeps the character of the original.
 *
 * @throws IllegalArgumentException when a background is not opaque (from [Wcag.contrast]).
 */
internal fun Rgba.withContrast(
    backgrounds: List<Rgba>,
    minimum: Double,
    toward: Rgba,
): Rgba =
    (0..MIX_STEPS)
        .asSequence()
        .map { step -> mix(toward, step) }
        .firstOrNull { candidate -> backgrounds.all { Wcag.contrast(candidate, it) >= minimum } }
        ?: toward
