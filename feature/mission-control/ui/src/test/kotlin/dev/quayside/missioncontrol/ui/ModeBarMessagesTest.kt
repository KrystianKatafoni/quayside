package dev.quayside.missioncontrol.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** The status bar counters (AM-821): the verb follows the shared Polish plural rule. */
internal class ModeBarMessagesTest {
    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "0,0 czeka,0 pracuje",
        "1,1 czeka,1 pracuje",
        "2,2 czekają,2 pracują",
        "4,4 czekają,4 pracują",
        "5,5 czeka,5 pracuje",
        "12,12 czeka,12 pracuje",
        "14,14 czeka,14 pracuje",
        "21,21 czeka,21 pracuje",
        "22,22 czekają,22 pracują",
        "112,112 czeka,112 pracuje",
    )
    fun `mode bar counters agree with the number`(
        count: Int,
        waiting: String,
        working: String,
    ) {
        MissionControlStrings.plural("modeBar.waiting", count) shouldBe waiting
        MissionControlStrings.plural("modeBar.working", count) shouldBe working
    }
}
