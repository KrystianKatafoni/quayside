package dev.quayside.core.designsystem

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.ShadowSpec
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** One shadow of the theme and the compose shadow it must become. */
data class ShadowCase(
    val spec: ShadowSpec,
) {
    override fun toString(): String = "offsetY=${spec.offsetY} blur=${spec.blur} spread=${spec.spread}"
}

/** [toShadow] copies every field of the theme shadow; the horizontal offset is always zero. */
class ShadowSpecToShadowTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `maps every field of the spec to the compose shadow`(case: ShadowCase) {
        val shadow = case.spec.toShadow()

        shadow.radius shouldBe case.spec.blur.dp
        shadow.spread shouldBe case.spec.spread.dp
        shadow.offset shouldBe DpOffset(0.dp, case.spec.offsetY.dp)
        shadow.color shouldBe case.spec.color.toColor()
    }

    companion object {
        @JvmStatic
        fun cases(): Stream<ShadowCase> =
            Stream.of(
                ShadowCase(ShadowSpec(offsetY = 4, blur = 12, spread = -1, color = Rgba(0xFF8B5CF6L).withAlpha(10))),
                ShadowCase(ShadowSpec(offsetY = 7, blur = 20, spread = 3, color = Rgba(0xFF22C55EL).withAlpha(35))),
            )
    }
}
