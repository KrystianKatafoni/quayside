package dev.agentmanager.terminal.impl.replyspike

/** What kind of question a scenario asks. */
internal enum class Kind { TEXT, OPTIONS, PERMISSION }

/** What the answer did in the working directory of the session. */
internal enum class Effect {
    /** File with the right content / approval file exists on allow / is absent on deny. */
    AS_EXPECTED,

    /** Nothing observable (yet). */
    ABSENT,

    /** Wrong option or token, approval on deny, file on deny. */
    WRONG,
}

/**
 * Facts of one trial. [phaseAfter] is the session phase after the detach and the observation wait;
 * [sessionsWithName] counts sessions with the scenario name (1 expected); [race] marks trials where
 * nothing may be typed (visible attach card, session stops waiting before the send).
 */
internal data class TrialFacts(
    val reply: ReplyOutcome,
    val effect: Effect,
    val phaseAfter: SpikePhase,
    val sessionsWithName: Int = 1,
    val race: Boolean = false,
)

internal enum class FailureCategory {
    NOT_READY,
    KIND_MISMATCH,
    NOT_ACKNOWLEDGED,
    START_FAILED,
    STILL_BLOCKED,
    NO_EFFECT,
}

internal enum class HarmCategory { WRONG_ANSWER, SESSION_LOST, DUPLICATE_SESSION, TYPED_IN_RACE }

internal sealed interface TrialVerdict {
    data object Success : TrialVerdict

    data class Failure(
        val category: FailureCategory,
    ) : TrialVerdict

    data class Harmful(
        val category: HarmCategory,
    ) : TrialVerdict
}

/**
 * Pure: verdict of one trial. A harmful result outranks a failure; in a race trial only typing matters.
 * An acknowledged reply is a success only when the effect is as expected and the session left `BLOCKED`.
 */
internal object Verdicts {
    fun of(trial: TrialFacts): TrialVerdict =
        when {
            trial.race -> ofRace(trial.reply)
            else -> harm(trial)?.let(TrialVerdict::Harmful) ?: ofReply(trial)
        }

    /**
     * Race trial: the session is stopped before the reply, so the effect, the phase and the duplicate count say
     * nothing about the answer (the stop itself ends the session). Only typing matters; hence [harm] is skipped.
     * Approximation: a duplicate session started by the attach would go unnoticed here.
     */
    private fun ofRace(reply: ReplyOutcome): TrialVerdict =
        when (reply) {
            is ReplyOutcome.Acknowledged, ReplyOutcome.NotAcknowledged -> {
                TrialVerdict.Harmful(
                    HarmCategory.TYPED_IN_RACE,
                )
            }

            ReplyOutcome.StartFailed -> {
                TrialVerdict.Failure(FailureCategory.START_FAILED)
            }

            is ReplyOutcome.NotReady, is ReplyOutcome.KindMismatch -> {
                TrialVerdict.Success
            }
        }

    private fun harm(trial: TrialFacts): HarmCategory? =
        when {
            trial.effect == Effect.WRONG -> HarmCategory.WRONG_ANSWER
            trial.phaseAfter == SpikePhase.STOPPED || trial.phaseAfter == SpikePhase.FAILED -> HarmCategory.SESSION_LOST
            trial.sessionsWithName > 1 -> HarmCategory.DUPLICATE_SESSION
            else -> null
        }

    private fun ofReply(trial: TrialFacts): TrialVerdict =
        when (trial.reply) {
            is ReplyOutcome.NotReady -> TrialVerdict.Failure(FailureCategory.NOT_READY)
            is ReplyOutcome.KindMismatch -> TrialVerdict.Failure(FailureCategory.KIND_MISMATCH)
            ReplyOutcome.NotAcknowledged -> TrialVerdict.Failure(FailureCategory.NOT_ACKNOWLEDGED)
            ReplyOutcome.StartFailed -> TrialVerdict.Failure(FailureCategory.START_FAILED)
            is ReplyOutcome.Acknowledged -> ofAcknowledged(trial)
        }

    private fun ofAcknowledged(trial: TrialFacts): TrialVerdict =
        when {
            trial.phaseAfter == SpikePhase.BLOCKED -> TrialVerdict.Failure(FailureCategory.STILL_BLOCKED)
            trial.effect == Effect.ABSENT -> TrialVerdict.Failure(FailureCategory.NO_EFFECT)
            else -> TrialVerdict.Success
        }
}
