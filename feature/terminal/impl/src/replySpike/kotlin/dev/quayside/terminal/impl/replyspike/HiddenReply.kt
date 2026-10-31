package dev.quayside.terminal.impl.replyspike

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.domain.info
import dev.quayside.terminal.impl.PtyLauncher
import dev.quayside.terminal.impl.PtyTtyConnector
import dev.quayside.terminal.impl.ShellLaunch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * How the hidden `claude attach` client is left: [KEY] types [DETACH_KEY]; [HANGUP] sends SIGHUP to the client
 * process only. `PtyTtyConnector.close()` is no detach: it kills the whole process tree.
 */
internal enum class Detach { KEY, HANGUP }

/** Detach key of `claude attach`: a placeholder (left arrow), to be determined in the measurement (step 2). */
internal const val DETACH_KEY = "\u001b[D"

private val DETACH_WAIT = 1.seconds
private val POLL = 20.milliseconds
private val FILLED_WAIT = 300.milliseconds

internal data class ReplyLimits(
    val ready: Duration = 5.seconds,
    val ack: Duration = 5.seconds,
    val keyGap: Duration = 30.milliseconds,
)

/** Result of one hidden reply; no screen text, no answer (ENG-C8). */
internal sealed interface ReplyOutcome {
    data class Acknowledged(
        val ready: Duration,
        val ack: Duration,
        // True when the client ended by itself within DETACH_WAIT after the detach (before the connector is closed).
        val cleanDetach: Boolean = true,
    ) : ReplyOutcome

    /** Nothing typed. */
    data class NotReady(
        val seen: Prompt,
    ) : ReplyOutcome

    /** Nothing typed. */
    data class KindMismatch(
        val seen: Prompt,
    ) : ReplyOutcome

    /** Typed, prompt still shown after the ack limit. */
    data object NotAcknowledged : ReplyOutcome

    data object StartFailed : ReplyOutcome
}

/**
 * Replies to a session through a hidden `claude attach`: nothing is typed until the screen shows a prompt of the
 * matching kind. Blocking calls (PTY start and writes); call on an IO dispatcher. Logs kind and outcome only.
 */
internal class HiddenReply(
    private val shell: Path,
    private val baseEnvironment: Map<String, String>,
    private val logger: Logger,
    private val time: TimeSource = TimeSource.Monotonic,
) {
    /** Opens [attach] hidden, waits for a prompt matching [reply], types it, waits for the prompt to go, detaches. */
    suspend fun reply(
        attach: ShellCommand,
        reply: Reply,
        variant: KeyVariant,
        detach: Detach,
        limits: ReplyLimits = ReplyLimits(),
    ): ReplyOutcome {
        val keys = ReplyKeys.keys(reply, variant)
        val argv = ShellLaunch.command(shell, attach.argv)
        val environment = ShellLaunch.environment(baseEnvironment, attach.env)
        val outcome =
            when (val started = PtyLauncher.start(argv, attach.workDir, environment)) {
                is Outcome.Err -> {
                    ReplyOutcome.StartFailed
                }

                is Outcome.Ok -> {
                    val process = started.value
                    val connector = PtyTtyConnector(process)
                    try {
                        HeadlessScreen(connector).use { screen ->
                            val result = converse(screen, connector, reply, keys, limits)
                            // After nothing was typed the detach key must not reach the session either.
                            val mode = if (result.typed) detach else Detach.HANGUP
                            val clean = detach(process.pid(), mode, connector)
                            (result.outcome as? ReplyOutcome.Acknowledged)?.copy(cleanDetach = clean) ?: result.outcome
                        }
                    } finally {
                        withContext(NonCancellable) { connector.close() }
                    }
                }
            }
        logger.info("hidden reply", "kind" to kindName(reply), "outcome" to outcomeName(outcome))
        return outcome
    }

    private class Conversation(
        val outcome: ReplyOutcome,
        val typed: Boolean,
    )

    private suspend fun converse(
        screen: HeadlessScreen,
        connector: PtyTtyConnector,
        reply: Reply,
        keys: List<String>,
        limits: ReplyLimits,
    ): Conversation {
        val opened = time.markNow()
        screen.await(limits.ready) { matches(reply, ScreenClassifier.classify(it)) }
        val seen = ScreenClassifier.classify(screen.rows())
        val ready = opened.elapsedNow()
        if (!matches(reply, seen)) return Conversation(refused(seen), typed = false)
        val typed = time.markNow()
        val filled = type(screen, connector, reply, keys, limits.keyGap)
        val outcome =
            when {
                filled == null -> ReplyOutcome.NotAcknowledged
                acknowledged(screen, reply, filled, limits.ack) -> ReplyOutcome.Acknowledged(ready, typed.elapsedNow())
                else -> ReplyOutcome.NotAcknowledged
            }
        return Conversation(outcome, typed = true)
    }

    /** A text reply is confirmed by a spinner, a gone box, or an empty box after the draft was seen in it. */
    private suspend fun acknowledged(
        screen: HeadlessScreen,
        reply: Reply,
        filledSeen: Boolean,
        limit: Duration,
    ): Boolean {
        var filled = filledSeen
        return screen.await(limit) { rows ->
            val now = ScreenClassifier.classify(rows)
            if (now == Prompt.TextFilled) filled = true
            if (reply is Reply.Text) now.confirmsText(filled) else !matches(reply, now)
        }
    }

    /** Nothing typed: no prompt (yet) is [ReplyOutcome.NotReady], another kind of prompt a mismatch. */
    private fun refused(seen: Prompt): ReplyOutcome =
        if (seen == Prompt.None || seen == Prompt.Unknown || seen == Prompt.Busy) {
            ReplyOutcome.NotReady(seen)
        } else {
            ReplyOutcome.KindMismatch(seen)
        }

    /**
     * Types [keys]; null when the PTY refused a write. For a text reply the last key (Enter) waits a moment for
     * the draft to show in the input box; the result tells whether it did.
     */
    private suspend fun type(
        screen: HeadlessScreen,
        connector: PtyTtyConnector,
        reply: Reply,
        keys: List<String>,
        gap: Duration,
    ): Boolean? =
        try {
            var filled = false
            keys.forEachIndexed { index, chunk ->
                if (index > 0 && index == keys.lastIndex && reply is Reply.Text) {
                    filled = screen.await(FILLED_WAIT) { ScreenClassifier.classify(it) == Prompt.TextFilled }
                } else if (index > 0) {
                    delay(gap)
                }
                connector.write(chunk)
            }
            filled
        } catch (ignored: IOException) {
            null
        }

    /**
     * [Detach.HANGUP]: SIGHUP to the client, then wait for it to end so closing the PTY finds no child to kill.
     * Returns true when the client ended by itself within the wait.
     */
    private suspend fun detach(
        pid: Long,
        detach: Detach,
        connector: PtyTtyConnector,
    ): Boolean {
        when (detach) {
            Detach.KEY -> runCatchingIo { connector.write(DETACH_KEY) }
            Detach.HANGUP -> runCatchingIo { ProcessBuilder("kill", "-HUP", pid.toString()).start().waitFor() }
        }
        val start = time.markNow()
        while (isAlive(pid) && start.elapsedNow() < DETACH_WAIT) delay(POLL)
        return !isAlive(pid)
    }

    private fun isAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    private inline fun runCatchingIo(block: () -> Unit) {
        try {
            block()
        } catch (ignored: IOException) {
            // The client is gone already; nothing to detach from.
        }
    }
}

private fun matches(
    reply: Reply,
    prompt: Prompt,
): Boolean =
    when (reply) {
        is Reply.Text -> prompt == Prompt.Text
        is Reply.Option -> prompt is Prompt.Options && reply.index <= prompt.count
        is Reply.Permission -> prompt is Prompt.Permission
    }

private fun Prompt.confirmsText(draftSeen: Boolean): Boolean =
    this == Prompt.Busy || this == Prompt.None || (this == Prompt.Text && draftSeen)

private fun kindName(reply: Reply): String =
    when (reply) {
        is Reply.Text -> "text"
        is Reply.Option -> "options"
        is Reply.Permission -> "permission"
    }

private fun outcomeName(outcome: ReplyOutcome): String =
    when (outcome) {
        is ReplyOutcome.Acknowledged -> "acknowledged"
        is ReplyOutcome.NotReady -> "not-ready"
        is ReplyOutcome.KindMismatch -> "kind-mismatch"
        ReplyOutcome.NotAcknowledged -> "not-acknowledged"
        ReplyOutcome.StartFailed -> "start-failed"
    }
