package dev.agentmanager.sessions.impl

import kotlin.math.pow
import kotlin.time.Duration

/**
 * Pure (ENG-C9): wait before the next listing. Up to [GRACE_FAILURES] failures in a row keep [interval];
 * every further failure doubles the wait, never above [maxRetryInterval].
 */
internal data class PollSchedule(
    val interval: Duration,
    val maxRetryInterval: Duration,
) {
    init {
        require(interval.isPositive()) { "interval must be positive" }
        require(maxRetryInterval >= interval) { "maxRetryInterval must not be shorter than interval" }
    }

    /** Wait after [consecutiveFailures] failed listings in a row (0 = the last listing succeeded). */
    fun delayAfter(consecutiveFailures: Int): Duration {
        if (consecutiveFailures <= GRACE_FAILURES) return interval
        val doublings = (consecutiveFailures - GRACE_FAILURES).coerceAtMost(MAX_DOUBLINGS)
        return minOf(interval * 2.0.pow(doublings), maxRetryInterval)
    }

    private companion object {
        const val GRACE_FAILURES = 2

        // 2^32 times any positive interval already exceeds every sensible maximum; keeps the math finite.
        const val MAX_DOUBLINGS = 32
    }
}
