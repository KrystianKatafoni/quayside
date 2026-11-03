package dev.quayside.appearance.impl

import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens.rgb

/** Opaque colour from `#RRGGBB` for test tables. */
internal fun hex(value: String): Rgba = rgb(java.lang.Long.decode("0x" + value.removePrefix("#")))

/** Colour from `#RRGGBB` or `#RRGGBB/NN` (NN = opacity percent), as in a shaped-theme row. */
internal fun hexAlpha(value: String): Rgba =
    if ('/' in value) hex(value.substringBefore('/')).withAlpha(value.substringAfter('/').toInt()) else hex(value)
