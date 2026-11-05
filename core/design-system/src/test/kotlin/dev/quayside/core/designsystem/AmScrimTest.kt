package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The scrim seen from outside: a white frame with the scrim over it (the frame is the wrapper with the background). */
@OptIn(ExperimentalTestApi::class)
class AmScrimTest {
    @Test
    fun `scrim darkens what is behind it`() =
        runComposeUiTest {
            // Given a white 40 dp box with a scrim on top of it
            setContent {
                Box(Modifier.testTag("frame").size(40.dp).background(Color.White)) {
                    Box(Modifier.size(40.dp).amScrim())
                }
            }

            // When the frame is captured
            val pixels = onNodeWithTag("frame").pixels()

            // Then white is black at 60 % alpha over white: 0.4 of 255 per channel (+/- 1 for rounding)
            val argb = pixels.argbAt(pixels.width / 2, pixels.height / 2)
            val channel = argb and 0xFF
            (channel in 101..103) shouldBe true
            ((argb shr 16) and 0xFF) shouldBe channel
            ((argb shr 8) and 0xFF) shouldBe channel
        }

    @Test
    fun `scrim leaves pixels outside it untouched`() =
        runComposeUiTest {
            setContent {
                Box(Modifier.testTag("frame").size(40.dp).background(Color.White)) {
                    Box(Modifier.size(10.dp).amScrim())
                }
            }

            val pixels = onNodeWithTag("frame").pixels()

            pixels.argbAt(pixels.width * 9 / 10, pixels.height * 9 / 10) shouldBe Color.White.toArgb()
        }

    @Test
    fun `modal layer counts in the gate while composed and stops when it leaves`() =
        runComposeUiTest {
            // Given a gate and a layer that can be removed
            val gate = AmModalGate()
            val shown = mutableStateOf(value = true)
            setContent {
                CompositionLocalProvider(LocalAmModalGate provides gate) {
                    if (shown.value) Box(Modifier.size(10.dp).amModalLayer())
                }
            }

            // Then the gate is active, and not any more when the layer is gone
            gate.active shouldBe true
            runOnIdle { shown.value = false }
            waitForIdle()
            gate.active shouldBe false
        }

    @Test
    fun `modal layer draws nothing`() =
        runComposeUiTest {
            setContent {
                Box(Modifier.testTag("frame").size(40.dp).background(Color.White)) {
                    Box(Modifier.size(40.dp).amModalLayer())
                }
            }

            val pixels = onNodeWithTag("frame").pixels()

            pixels.argbAt(pixels.width / 2, pixels.height / 2) shouldBe Color.White.toArgb()
        }

    @Test
    fun `scrim and modal layer each add one layer`() =
        runComposeUiTest {
            // Given a scrim and a modal layer shown together, then the scrim removed
            val gate = AmModalGate()
            val scrim = mutableStateOf(value = true)
            setContent {
                CompositionLocalProvider(LocalAmModalGate provides gate) {
                    if (scrim.value) Box(Modifier.size(10.dp).amScrim())
                    Box(Modifier.size(10.dp).amModalLayer())
                }
            }
            gate.active shouldBe true

            // When the scrim leaves, the layer still counts
            runOnIdle { scrim.value = false }
            waitForIdle()

            // Then the gate stays active: the scrim and the layer were two counts
            gate.active shouldBe true
        }
}
