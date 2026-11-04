package dev.quayside.core.designsystem

import androidx.compose.runtime.Composable
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec

private val PLEX_TYPOGRAPHY = TypographySpec(TypographyId("plex"), "IBM Plex", "IBM Plex Sans", "IBM Plex Mono")

/**
 * Wraps [content] in [AmTheme] with the `plex` tokens (`SampleTokens.colors()`, `SampleTokens.shape()`) and the plex
 * typography: the one theme wrapper for UI tests of screens, so that a test sees the colours of the default look.
 */
@Composable
public fun AmTestTheme(content: @Composable () -> Unit) {
    AmTheme(SampleTokens.colors(), SampleTokens.shape(), PLEX_TYPOGRAPHY, content)
}
