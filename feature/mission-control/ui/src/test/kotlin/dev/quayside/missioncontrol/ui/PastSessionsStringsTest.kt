package dev.quayside.missioncontrol.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Texts of the disk sessions (FR-P9, NFR-10) in `i18n/missioncontrol.properties`. */
internal class PastSessionsStringsTest {
    @Test
    fun `the badge says z dysku`() {
        MissionControlStrings["row.fromDisk"] shouldBe "z dysku"
    }

    @Test
    fun `the link of the older row says Zmień limit`() {
        MissionControlStrings["older.change"] shouldBe "Zmień limit"
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "1, jeszcze 1 starsza",
        "2, jeszcze 2 starsze",
        "4, jeszcze 4 starsze",
        "5, jeszcze 5 starszych",
        "12, jeszcze 12 starszych",
        "14, jeszcze 14 starszych",
        "22, jeszcze 22 starsze",
        "112, jeszcze 112 starszych",
    )
    fun `older row text follows the Polish plural of the count`(
        count: Int,
        expected: String,
    ) {
        MissionControlStrings.plural("older.row", count) shouldBe expected
    }
}
