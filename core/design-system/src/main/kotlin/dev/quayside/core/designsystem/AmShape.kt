package dev.quayside.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quayside.core.designtokens.Backdrop
import dev.quayside.core.designtokens.ShadowSpec
import dev.quayside.core.designtokens.ShapeTokens

/**
 * Shape tokens as Compose values. [corner] is a rounded corner of the token radius; [shadow] and [backdrop] are
 * passed on as in [ShapeTokens] (drawn from AM-805).
 */
@Immutable
public data class AmShape(
    val corner: Shape,
    val borderWidth: Dp,
    val shadow: ShadowSpec?,
    val backdrop: Backdrop,
)

/** Maps shape tokens to [AmShape]. */
public fun ShapeTokens.toAmShape(): AmShape = AmShape(RoundedCornerShape(radius.dp), borderWidth.dp, shadow, backdrop)
