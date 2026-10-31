package dev.quayside.terminal.impl.replyspike

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.domain.getOrNull
import dev.quayside.core.domain.info
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val POLL = 2.seconds
private val SETTLE = 3.seconds

/** How the runner replies and how long it waits for the session. */
internal data class TrialSettings(
    val variant: KeyVariant = KeyVariant(),
    val detach: Detach = Detach.HANGUP,
    val limits: ReplyLimits = ReplyLimits(),
    val blockedTimeout: Duration = 120.seconds,
    val observeTimeout: Duration = 90.seconds,
)

private class Trial(
    val name: String,
    val dir: Path,
    val scenario: Scenario,
    val settings: TrialSettings,
    val race: Boolean,
)

/**
 * Runs the trials against the real `claude`: one fresh `--bg` session in `<root>/<n>` per trial, always stopped and
 * removed afterwards. Logs and records carry kinds, categories and times only (ENG-C8).
 */
internal class TrialRunner(
    private val claude: SpikeClaude,
    private val hidden: HiddenReply,
    private val logger: Logger,
    private val root: Path,
    private val random: Random = Random.Default,
) {
    /** One trial of [kind]; [race] answers after the session stopped waiting: nothing may be typed. */
    suspend fun trial(
        kind: Kind,
        n: Int,
        settings: TrialSettings,
        race: Boolean = false,
    ): TrialRecord {
        val name = sessionName(kind, n, race, random)
        val dir = Files.createDirectories(root.resolve(name))
        val trial = Trial(name, dir, Scenarios.of(kind, n, "token${random.nextInt(0, MAX_SUFFIX)}"), settings, race)
        val mark = TimeSource.Monotonic.markNow()
        val facts =
            try {
                play(trial)
            } finally {
                withContext(NonCancellable) { removeAll(name) }
            }
        val verdict = Verdicts.of(facts)
        logger.info("trial", "kind" to kind.name, "race" to race, "verdict" to describe(verdict))
        val clean = (facts.reply as? ReplyOutcome.Acknowledged)?.cleanDetach ?: true
        return TrialRecord(kind, verdict, durationOf(facts.reply, mark.elapsedNow()), race, clean)
    }

    private suspend fun removeAll(name: String) {
        claude.sessions(name).getOrNull()?.forEach { claude.stopAndRemove(it.id) }
    }

    private suspend fun play(trial: Trial): TrialFacts {
        val started = claude.start(trial.name, trial.scenario.prompt, trial.dir).getOrNull() != null
        val blocked = if (started) awaitBlocked(trial.name, trial.settings.blockedTimeout) else null
        return when {
            !started -> {
                TrialFacts(ReplyOutcome.StartFailed, Effect.ABSENT, SpikePhase.OTHER, race = trial.race)
            }

            blocked == null -> {
                TrialFacts(
                    ReplyOutcome.NotReady(Prompt.None),
                    Effect.ABSENT,
                    SpikePhase.WORKING,
                    race = trial.race,
                )
            }

            else -> {
                answer(trial, blocked)
            }
        }
    }

    private suspend fun answer(
        trial: Trial,
        blocked: SpikeSession,
    ): TrialFacts {
        if (trial.race) claude.stopAndRemove(blocked.id)
        val attach = ShellCommand(listOf("claude", "attach", blocked.id), trial.dir)
        val settings = trial.settings
        val reply = hidden.reply(attach, trial.scenario.reply, settings.variant, settings.detach, settings.limits)
        return observe(trial, reply)
    }

    private suspend fun observe(
        trial: Trial,
        reply: ReplyOutcome,
    ): TrialFacts {
        val mark = TimeSource.Monotonic.markNow()
        var phase = phaseOf(trial.name)
        val typed = reply is ReplyOutcome.Acknowledged || reply == ReplyOutcome.NotAcknowledged
        while (typed && phase == SpikePhase.BLOCKED && mark.elapsedNow() < trial.settings.observeTimeout) {
            delay(POLL)
            phase = phaseOf(trial.name)
        }
        if (typed) delay(SETTLE)
        val count = claude.sessions(trial.name).getOrNull()?.size ?: 1
        return TrialFacts(reply, trial.scenario.effect(trial.dir), phaseOf(trial.name), count, trial.race)
    }

    private suspend fun phaseOf(name: String): SpikePhase = claude.find(name).getOrNull()?.phase ?: SpikePhase.OTHER

    private suspend fun awaitBlocked(
        name: String,
        timeout: Duration,
    ): SpikeSession? {
        val mark = TimeSource.Monotonic.markNow()
        while (mark.elapsedNow() < timeout) {
            claude
                .find(name)
                .getOrNull()
                ?.takeIf { it.phase == SpikePhase.BLOCKED }
                ?.let { return it }
            delay(POLL)
        }
        return null
    }

    private fun durationOf(
        reply: ReplyOutcome,
        elapsed: Duration,
    ): Duration = if (reply is ReplyOutcome.Acknowledged) reply.ready + reply.ack else elapsed

    private companion object {
        const val MAX_SUFFIX = 1_000_000
    }
}

/** Fixed category of a verdict, for logs and the report. */
internal fun describe(verdict: TrialVerdict): String =
    when (verdict) {
        TrialVerdict.Success -> "success"
        is TrialVerdict.Failure -> "failure:${verdict.category.name.lowercase()}"
        is TrialVerdict.Harmful -> "harmful:${verdict.category.name.lowercase()}"
    }
