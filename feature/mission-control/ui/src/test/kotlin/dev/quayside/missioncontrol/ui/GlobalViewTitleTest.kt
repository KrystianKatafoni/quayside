package dev.quayside.missioncontrol.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Titles of the global views as in the sidebar (AM-816). */
internal class GlobalViewTitleTest {
    @ParameterizedTest(name = "{0}")
    @CsvSource("WAITING,Czeka na mnie", "WORKING,Pracuje", "FAILED,Błędy")
    fun `view title is the sidebar text`(
        view: GlobalView,
        expected: String,
    ) {
        view.title() shouldBe expected
    }
}
