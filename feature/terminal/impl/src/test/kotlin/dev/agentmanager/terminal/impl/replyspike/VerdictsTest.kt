package dev.agentmanager.terminal.impl.replyspike

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.time.Duration.Companion.milliseconds

private val ACKED = ReplyOutcome.Acknowledged(ready = 300.milliseconds, ack = 100.milliseconds)

private fun facts(
    reply: ReplyOutcome = ACKED,
    effect: Effect = Effect.AS_EXPECTED,
    phaseAfter: SpikePhase = SpikePhase.WORKING,
    sessionsWithName: Int = 1,
    race: Boolean = false,
) = TrialFacts(reply, effect, phaseAfter, sessionsWithName, race)

private fun failure(category: FailureCategory) = TrialVerdict.Failure(category)

private fun harmful(category: HarmCategory) = TrialVerdict.Harmful(category)

/** Table 3 of the AM-507 plan. */
internal class VerdictsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    @Suppress("UNUSED_PARAMETER")
    fun `verdict of a trial`(
        name: String,
        trial: TrialFacts,
        expected: TrialVerdict,
    ) {
        Verdicts.of(trial) shouldBe expected
    }

    companion object {
        @JvmStatic
        @Suppress("LongMethod")
        fun table(): Stream<Arguments> =
            Stream.of(
                Arguments.of("acknowledged, file as expected, left BLOCKED, alive", facts(), TrialVerdict.Success),
                Arguments.of(
                    "session finished its work after the answer",
                    facts(phaseAfter = SpikePhase.DONE),
                    TrialVerdict.Success,
                ),
                Arguments.of(
                    "not ready",
                    facts(ReplyOutcome.NotReady(Prompt.None), Effect.ABSENT, SpikePhase.BLOCKED),
                    failure(FailureCategory.NOT_READY),
                ),
                Arguments.of(
                    "kind mismatch, nothing typed",
                    facts(ReplyOutcome.KindMismatch(Prompt.Permission("Bash")), Effect.ABSENT, SpikePhase.BLOCKED),
                    failure(FailureCategory.KIND_MISMATCH),
                ),
                Arguments.of(
                    "prompt stayed after typing",
                    facts(ReplyOutcome.NotAcknowledged, Effect.ABSENT, SpikePhase.BLOCKED),
                    failure(FailureCategory.NOT_ACKNOWLEDGED),
                ),
                Arguments.of(
                    "attach did not start",
                    facts(reply = ReplyOutcome.StartFailed, effect = Effect.ABSENT, phaseAfter = SpikePhase.BLOCKED),
                    failure(FailureCategory.START_FAILED),
                ),
                Arguments.of(
                    "session stayed BLOCKED until the timeout",
                    facts(effect = Effect.ABSENT, phaseAfter = SpikePhase.BLOCKED),
                    failure(FailureCategory.STILL_BLOCKED),
                ),
                Arguments.of(
                    "acknowledged but the file never appeared",
                    facts(effect = Effect.ABSENT),
                    failure(FailureCategory.NO_EFFECT),
                ),
                Arguments.of(
                    "wrong option or token",
                    facts(effect = Effect.WRONG),
                    harmful(HarmCategory.WRONG_ANSWER),
                ),
                Arguments.of(
                    "approval on deny or file on deny, prompt stayed",
                    facts(reply = ReplyOutcome.NotAcknowledged, effect = Effect.WRONG),
                    harmful(HarmCategory.WRONG_ANSWER),
                ),
                Arguments.of(
                    "session STOPPED after the detach",
                    facts(phaseAfter = SpikePhase.STOPPED),
                    harmful(HarmCategory.SESSION_LOST),
                ),
                Arguments.of(
                    "session FAILED after the detach",
                    facts(phaseAfter = SpikePhase.FAILED),
                    harmful(HarmCategory.SESSION_LOST),
                ),
                Arguments.of(
                    "a second session with the same name",
                    facts(sessionsWithName = 2),
                    harmful(HarmCategory.DUPLICATE_SESSION),
                ),
                Arguments.of(
                    "race: anything typed (acknowledged)",
                    facts(race = true),
                    harmful(HarmCategory.TYPED_IN_RACE),
                ),
                Arguments.of(
                    "race: anything typed (not acknowledged)",
                    facts(reply = ReplyOutcome.NotAcknowledged, effect = Effect.ABSENT, race = true),
                    harmful(HarmCategory.TYPED_IN_RACE),
                ),
                Arguments.of(
                    "race: nothing typed is a success",
                    facts(reply = ReplyOutcome.KindMismatch(Prompt.None), effect = Effect.ABSENT, race = true),
                    TrialVerdict.Success,
                ),
            )
    }
}
