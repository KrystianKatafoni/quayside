package dev.quayside.terminal.impl.replyspike

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private fun trials(
    kind: Kind,
    successes: Int,
    failures: Int = 0,
    harmful: Int = 0,
    slowest: Duration = 1.seconds,
): List<TrialRecord> =
    List(successes) { i ->
        TrialRecord(kind, TrialVerdict.Success, if (i == successes - 1) slowest else 1.seconds)
    } +
        List(failures) { TrialRecord(kind, TrialVerdict.Failure(FailureCategory.NOT_ACKNOWLEDGED), 1.seconds) } +
        List(harmful) { TrialRecord(kind, TrialVerdict.Harmful(HarmCategory.WRONG_ANSWER), 1.seconds) }

private fun raceTrial(
    kind: Kind,
    verdict: TrialVerdict,
) = TrialRecord(kind, verdict, 1.seconds, race = true)

/** Table 4 of the AM-507 plan; percentiles are nearest-rank over the durations of successes. */
internal class ReplyReportTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("ratings")
    @Suppress("UNUSED_PARAMETER")
    fun `rating of a kind`(
        name: String,
        records: List<TrialRecord>,
        expected: Rating,
    ) {
        ReplyReport.summarize(records).getValue(Kind.TEXT).rating shouldBe expected
    }

    @Test
    fun `counts successes harmful trials and percentiles per kind`() {
        val text = List(10) { i -> TrialRecord(Kind.TEXT, TrialVerdict.Success, (i + 1).seconds) }
        val options = trials(Kind.OPTIONS, successes = 8, failures = 2)

        val summary = ReplyReport.summarize(text + options)

        summary.keys shouldBe setOf(Kind.TEXT, Kind.OPTIONS)
        summary.getValue(Kind.TEXT) shouldBe
            KindSummary(
                trials = 10,
                successes = 10,
                harmful = 0,
                p50 = 5.seconds,
                p95 = 10.seconds,
                raceTypings = 0,
                rating = Rating.BORDERLINE,
                cleanDetaches = 10,
            )
        summary.getValue(Kind.OPTIONS).successes shouldBe 8
        summary.getValue(Kind.OPTIONS).trials shouldBe 10
    }

    @Test
    fun `race trials are counted apart from the measured ones`() {
        val records =
            trials(Kind.PERMISSION, successes = 10) +
                raceTrial(Kind.PERMISSION, TrialVerdict.Success) +
                raceTrial(Kind.PERMISSION, TrialVerdict.Harmful(HarmCategory.TYPED_IN_RACE))

        val summary = ReplyReport.summarize(records).getValue(Kind.PERMISSION)

        summary.trials shouldBe 10
        summary.successes shouldBe 10
        summary.raceTypings shouldBe 1
        summary.harmful shouldBe 1
        summary.rating shouldBe Rating.BAD
    }

    @Test
    fun `counts clean detaches per kind among measured trials`() {
        fun record(
            kind: Kind,
            clean: Boolean,
            race: Boolean = false,
        ) = TrialRecord(kind, TrialVerdict.Success, 1.seconds, race = race, cleanDetach = clean)
        val records =
            List(3) { record(Kind.TEXT, clean = true) } +
                List(2) { record(Kind.TEXT, clean = false) } +
                record(Kind.TEXT, clean = true, race = true) +
                record(Kind.OPTIONS, clean = true) +
                record(Kind.PERMISSION, clean = false)

        val summary = ReplyReport.summarize(records)

        summary.getValue(Kind.TEXT).cleanDetaches shouldBe 3
        summary.getValue(Kind.OPTIONS).cleanDetaches shouldBe 1
        summary.getValue(Kind.PERMISSION).cleanDetaches shouldBe 0
    }

    @Test
    fun `no records give no summary`() {
        ReplyReport.summarize(emptyList()) shouldBe emptyMap()
    }

    companion object {
        @JvmStatic
        @Suppress("LongMethod")
        fun ratings(): Stream<Arguments> =
            Stream.of(
                Arguments.of("10 of 10, p95 within 5 s", trials(Kind.TEXT, 10, slowest = 5.seconds), Rating.GOOD),
                Arguments.of("10 of 10, p95 above 5 s", trials(Kind.TEXT, 10, slowest = 6.seconds), Rating.BORDERLINE),
                Arguments.of("9 of 10, none harmful", trials(Kind.TEXT, 9, failures = 1), Rating.BORDERLINE),
                Arguments.of("8 of 10", trials(Kind.TEXT, 8, failures = 2), Rating.BAD),
                Arguments.of("0 of 10", trials(Kind.TEXT, 0, failures = 10), Rating.BAD),
                Arguments.of("9 of 10 with one harmful", trials(Kind.TEXT, 9, harmful = 1), Rating.BAD),
                Arguments.of(
                    "10 of 10 and a harmful extra trial",
                    trials(Kind.TEXT, 10) + trials(Kind.TEXT, 0, harmful = 1),
                    Rating.BAD,
                ),
                Arguments.of("19 of 20 (20 trials)", trials(Kind.TEXT, 19, failures = 1), Rating.GOOD),
                Arguments.of("18 of 20 (20 trials)", trials(Kind.TEXT, 18, failures = 2), Rating.BORDERLINE),
                Arguments.of("17 of 20 (20 trials)", trials(Kind.TEXT, 17, failures = 3), Rating.BORDERLINE),
                Arguments.of("16 of 20 (20 trials)", trials(Kind.TEXT, 16, failures = 4), Rating.BAD),
            )
    }
}
