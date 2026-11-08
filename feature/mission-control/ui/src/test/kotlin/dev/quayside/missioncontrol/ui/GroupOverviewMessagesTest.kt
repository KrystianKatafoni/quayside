package dev.quayside.missioncontrol.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** The texts of the group overview (AM-307); the keys are the tests' choice, the Polish texts come from the plan. */
internal class GroupOverviewMessagesTest {
    @Test
    fun `section row and counter texts`() {
        mcText("overview.title") shouldBe "Podfoldery"
        mcText("overview.rest") shouldBe "Pozostałe"
        mcText("overview.idle") shouldBe "brak agentów"
        mcText("overview.failed", "1") shouldBe "1 z błędem"
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
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
    fun `verbs agree with the number`(
        count: Int,
        waiting: String,
        working: String,
    ) {
        MissionControlStrings.plural("overview.waiting", count) shouldBe waiting
        MissionControlStrings.plural("overview.working", count) shouldBe working
    }
}
