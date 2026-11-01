package dev.quayside.notifications.ui

import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupStyle
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Window size and corner of the popup stack (FR-N3). */
class PopupLayoutTest {
    private val area = ScreenArea(x = 0, y = 25, width = 1440, height = 800)

    @Test
    fun `one full card is as tall as the card plus padding`() {
        PopupMetrics.windowHeight(listOf(aPopup("a")), PopupStyle.DETAILED) shouldBe 116 + 16
    }

    @Test
    fun `cards are separated by the gap and collapsed ones are lines`() {
        val popups = listOf(aPopup("a"), aPopup("b", collapsed = true))
        PopupMetrics.windowHeight(popups, PopupStyle.DETAILED) shouldBe 116 + 8 + 32 + 16
    }

    @Test
    fun `compact style shortens full cards only`() {
        val popups = listOf(aPopup("a"), aPopup("b", collapsed = true))
        PopupMetrics.windowHeight(popups, PopupStyle.COMPACT) shouldBe 68 + 8 + 32 + 16
    }

    @Test
    fun `no popups need only the padding`() {
        PopupMetrics.windowHeight(emptyList(), PopupStyle.DETAILED) shouldBe 16
    }

    @Test
    fun `each position picks its corner`() {
        val expected =
            mapOf(
                PopupPosition.TOP_LEFT to Origin(16, 41),
                PopupPosition.TOP_CENTER to Origin(530, 41),
                PopupPosition.TOP_RIGHT to Origin(1044, 41),
                PopupPosition.BOTTOM_LEFT to Origin(16, 609),
                PopupPosition.BOTTOM_CENTER to Origin(530, 609),
                PopupPosition.BOTTOM_RIGHT to Origin(1044, 609),
            )
        PopupPosition.entries.forEach { position ->
            popupOrigin(position, area, width = 380, height = 200) shouldBe expected.getValue(position)
        }
    }

    @Test
    fun `a window taller than the area stays on the area`() {
        popupOrigin(PopupPosition.BOTTOM_RIGHT, area, width = 380, height = 900).y shouldBe 25
    }
}
