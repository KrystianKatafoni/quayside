package dev.agentmanager.terminal.impl.replyspike

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val GOOD_PERCENT = 95
private const val BORDERLINE_PERCENT = 85
private const val PERCENT = 100
private const val P50 = 50
private const val P95 = 95
private val P95_LIMIT = 5.seconds

internal enum class Rating { GOOD, BORDERLINE, BAD }

/** One finished trial; [duration] is the time from opening the attach to the end of the ack wait. */
internal data class TrialRecord(
    val kind: Kind,
    val verdict: TrialVerdict,
    val duration: Duration,
    val race: Boolean = false,
    // The client of the attach ended by itself (ReplyOutcome.Acknowledged.cleanDetach).
    val cleanDetach: Boolean = true,
)

/**
 * Per kind: [trials] and [successes] count measured (non-race) trials; [harmful] and [raceTypings] count all
 * records; [p50] and [p95] are nearest-rank over the durations of successes.
 */
internal data class KindSummary(
    val trials: Int,
    val successes: Int,
    val harmful: Int,
    val p50: Duration,
    val p95: Duration,
    val raceTypings: Int,
    val rating: Rating,
    // Measured (non-race) trials with cleanDetach = true.
    val cleanDetaches: Int = 0,
)

/** Pure: per kind successes, harmful count, p50/p95, race typings and the rating (plan table 4). */
internal object ReplyReport {
    fun summarize(trials: List<TrialRecord>): Map<Kind, KindSummary> =
        trials.groupBy { it.kind }.mapValues { (_, records) -> summarizeKind(records) }

    private fun summarizeKind(records: List<TrialRecord>): KindSummary {
        val measured = records.filterNot { it.race }
        val durations =
            measured.filter { it.verdict == TrialVerdict.Success }.map { it.duration }.sorted()
        val harmful = records.count { it.verdict is TrialVerdict.Harmful }
        val p95 = percentile(durations, P95)
        return KindSummary(
            trials = measured.size,
            successes = durations.size,
            harmful = harmful,
            p50 = percentile(durations, P50),
            p95 = p95,
            raceTypings = records.count { it.race && it.verdict == TrialVerdict.Harmful(HarmCategory.TYPED_IN_RACE) },
            rating = rate(measured.size, durations.size, harmful, p95),
            cleanDetaches = measured.count { it.cleanDetach },
        )
    }

    /** Nearest rank; [sorted] ascending. */
    private fun percentile(
        sorted: List<Duration>,
        percent: Int,
    ): Duration = if (sorted.isEmpty()) Duration.ZERO else sorted[ceilPercent(sorted.size, percent) - 1]

    private fun ceilPercent(
        count: Int,
        percent: Int,
    ): Int = (count * percent + PERCENT - 1) / PERCENT

    private fun rate(
        trials: Int,
        successes: Int,
        harmful: Int,
        p95: Duration,
    ): Rating =
        when {
            harmful > 0 || trials == 0 -> Rating.BAD
            successes >= ceilPercent(trials, GOOD_PERCENT) -> if (p95 <= P95_LIMIT) Rating.GOOD else Rating.BORDERLINE
            successes >= ceilPercent(trials, BORDERLINE_PERCENT) -> Rating.BORDERLINE
            else -> Rating.BAD
        }
}
