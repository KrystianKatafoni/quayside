package dev.quayside.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.ShapeTokens
import dev.quayside.core.designtokens.TypographySpec

internal val LocalAmColors = staticCompositionLocalOf<ColorTokens> { error("AmTheme is missing") }
internal val LocalAmShape = staticCompositionLocalOf<AmShape> { error("AmTheme is missing") }
internal val LocalAmTypography = staticCompositionLocalOf<AmTypography> { error("AmTheme is missing") }

/** Compose colour of a token; alpha kept (a translucent panel is drawn over bg by the caller's layout). */
public fun Rgba.toColor(): Color = Color(red = red, green = green, blue = blue, alpha = alpha)

/** Provides theme values to [content]; a nested AmTheme overrides the outer one. */
@Composable
public fun AmTheme(
    colors: ColorTokens,
    shape: ShapeTokens,
    typography: TypographySpec,
    content: @Composable () -> Unit,
) {
    val amShape = remember(shape) { shape.toAmShape() }
    val amTypography = remember(typography) { typography.toAmTypography() }
    CompositionLocalProvider(
        LocalAmColors provides colors,
        LocalAmShape provides amShape,
        LocalAmTypography provides amTypography,
        content = content,
    )
}

/**
 * Current theme.
 *
 * @throws IllegalStateException with "AmTheme is missing" when a value is read outside [AmTheme] (a programmer error).
 */
public object AmTheme {
    /** Colour tokens; convert with [toColor]. */
    public val colors: ColorTokens
        @Composable @ReadOnlyComposable
        get() = LocalAmColors.current

    /** Shape tokens as Compose values. */
    public val shape: AmShape
        @Composable @ReadOnlyComposable
        get() = LocalAmShape.current

    /** Fonts and text styles. */
    public val typography: AmTypography
        @Composable @ReadOnlyComposable
        get() = LocalAmTypography.current
}
