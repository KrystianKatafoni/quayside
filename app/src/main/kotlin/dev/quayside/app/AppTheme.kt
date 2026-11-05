package dev.quayside.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.UiScale
import dev.quayside.core.designsystem.AmTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** AmTheme over [content] with [look]; wraps both windows inside `application {}` in Main. */
@Composable
internal fun AppTheme(
    look: AppLook,
    content: @Composable () -> Unit,
) {
    AmTheme(look.colors, look.shape, look.typography, look.density, content)
}

/** AmTheme over [content] with the current value of [looks]; a new look recolours the windows at once. */
@Composable
internal fun AppTheme(
    looks: StateFlow<AppLook>,
    content: @Composable () -> Unit,
) {
    val look by looks.collectAsState()
    AppTheme(look, content)
}

/**
 * [content] drawn [scale] times bigger (the factor multiplies the density of the window). Used inside the content of
 * a `Window`, which sets its own density: a scale given above the `Window` would be overridden.
 */
@Composable
internal fun AppScaled(
    scale: UiScale,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scaled = remember(density, scale) { density.scaledBy(scale) }
    CompositionLocalProvider(LocalDensity provides scaled, content = content)
}

/** [this] with `density` multiplied by the factor of [scale]; the font scale stays. */
internal fun Density.scaledBy(scale: UiScale): Density = Density(density * scale.factor, fontScale)

/** The looks of [appearance]'s current settings, in [scope] (Eagerly); the value is current before the first read. */
internal fun appLooks(
    appearance: AppearanceApi,
    scope: CoroutineScope,
): StateFlow<AppLook> {
    val catalog = appearance.catalog
    return appearance.settings
        .map(catalog::lookOf)
        .stateIn(scope, SharingStarted.Eagerly, catalog.lookOf(appearance.settings.value))
}
