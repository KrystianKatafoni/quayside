package dev.quayside.core.designsystem

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** The logo resources are on the classpath, parse as SVG and are the drawings the plan describes (ADR 0030). */
internal class LogoResourcesTest {
    private fun bytesOf(drawing: LogoDrawing): ByteArray {
        val stream = LogoResourcesTest::class.java.classLoader.getResourceAsStream(drawing.resource)
        checkNotNull(stream) { "missing resource ${drawing.resource}" }
        return stream.use { it.readBytes() }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LogoDrawing::class)
    fun `both logo resources exist and parse`(drawing: LogoDrawing) {
        val bytes = bytesOf(drawing)

        SVGDOM(Data.makeFromBytes(bytes)).root shouldNotBe null
        val viewBox = Regex("viewBox=\"0 0 (\\d+) (\\d+)\"").find(String(bytes, Charsets.UTF_8))
        val side = if (drawing == LogoDrawing.SMALL) SMALL_SIDE else FULL_SIDE
        viewBox?.groupValues?.drop(1) shouldBe listOf("$side", "$side")
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LogoDrawing::class)
    fun `each drawing has the shapes of the chosen logo`(drawing: LogoDrawing) {
        val text = String(bytesOf(drawing), Charsets.UTF_8)

        val shapes = Regex("<(rect|path)\\b").findAll(text).count()

        shapes shouldBe if (drawing == LogoDrawing.SMALL) SMALL_SHAPES else FULL_SHAPES
    }

    private companion object {
        const val SMALL_SIDE = 16
        const val FULL_SIDE = 32
        const val SMALL_SHAPES = 5
        const val FULL_SHAPES = 8
    }
}
