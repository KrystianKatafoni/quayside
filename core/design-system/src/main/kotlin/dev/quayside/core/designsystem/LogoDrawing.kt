package dev.quayside.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The two drawings of the mark: [SMALL] below 32 dp, [FULL] from 32 dp; [resource] is their classpath path. */
internal enum class LogoDrawing(
    val resource: String,
) {
    SMALL("logo/quayside-logo-16.svg"),
    FULL("logo/quayside-logo.svg"),
}

private val FULL_FROM = 32.dp

/** The drawing for [size]: below 32 dp the simplified one, from 32 dp the full one. */
internal fun logoDrawingFor(size: Dp): LogoDrawing = if (size < FULL_FROM) LogoDrawing.SMALL else LogoDrawing.FULL
