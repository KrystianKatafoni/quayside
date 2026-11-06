package dev.quayside.notifications.api

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** [VoiceSample]: ranges as in [SpeechSettings], and the text never reaches `toString` (ENG-C8). */
class VoiceSampleTest {
    @ParameterizedTest(name = "rate {0} volume {1} is a programmer error")
    @CsvSource("89,70", "361,70", "190,-1", "190,101")
    fun `a rate or volume outside the ranges throws`(
        rate: Int,
        volume: Int,
    ) {
        shouldThrow<IllegalArgumentException> { VoiceSample(null, "Hi", rate, volume) }
    }

    @ParameterizedTest(name = "rate {0} volume {1} is accepted")
    @CsvSource("90,0", "360,100", "190,70")
    fun `the edges of the ranges are accepted`(
        rate: Int,
        volume: Int,
    ) {
        shouldNotThrowAny { VoiceSample(null, "Hi", rate, volume) }
    }

    @Test
    fun `toString hides the text`() {
        val text = VoiceSample(VoiceId("Zosia"), "secret question", 190, 70).toString()

        text shouldNotContain "secret"
        text shouldNotContain "question"
    }

    @Test
    fun `the sample of an option defaults to empty`() {
        VoiceOption(VoiceId("Zosia"), "pl_PL").sample shouldBe ""
    }
}
