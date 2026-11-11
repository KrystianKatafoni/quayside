package dev.quayside.core.designsystem

import androidx.compose.ui.graphics.vector.PathParser
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** Path data of the 23 icons: parses, is distinct and stays on the 24 grid (geometry table of the plan). */
class IconKindTest {
    private val statusIcons =
        listOf(
            IconKind.WAITING,
            IconKind.WORKING,
            IconKind.DONE,
            IconKind.FAILED,
            IconKind.PERMISSION,
            IconKind.STOPPED,
        )

    @Test
    fun `there are 23 icons`() {
        IconKind.entries.size shouldBe 23
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(IconKind::class)
    fun `every icon has path data that parses to nodes`(icon: IconKind) {
        PathParser().parsePathString(icon.pathData).toNodes().shouldNotBeEmpty()
    }

    @Test
    fun `icons have pairwise different path data`() {
        IconKind.entries
            .map { it.pathData }
            .toSet()
            .size shouldBe IconKind.entries.size
    }

    @Test
    fun `status icons have pairwise different paths`() {
        // NFR-6: a status is told apart by shape, not by colour
        statusIcons.map { it.pathData }.toSet().size shouldBe statusIcons.size
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(IconKind::class)
    fun `every path stays inside the 24 grid`(icon: IconKind) {
        val bounds = PathParser().parsePathString(icon.pathData).toPath().getBounds()

        bounds.left shouldBeGreaterThanOrEqual 0f
        bounds.top shouldBeGreaterThanOrEqual 0f
        bounds.right shouldBeLessThanOrEqual GRID
        bounds.bottom shouldBeLessThanOrEqual GRID
        // and it is not empty (MORE is three dots in one row: thin, but wide)
        (bounds.width + bounds.height > 1f) shouldBe true
    }

    private companion object {
        const val GRID = 24f
    }
}
