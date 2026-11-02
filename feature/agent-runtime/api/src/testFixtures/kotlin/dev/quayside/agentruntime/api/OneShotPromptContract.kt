package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.ints.shouldNotBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Behaviour every [OneShotPrompt] must have (ENG-T2). Runs on [FakeOneShotPrompt] and on the Claude Code
 * adapter over a fake `claude` program.
 */
public abstract class OneShotPromptContract {
    /** What the model side does in a test. */
    public enum class Behaviour {
        /** Answers with the `answer` given to [create]. */
        ANSWERS,

        /** There is no `claude` program. */
        NOT_INSTALLED,

        /** Does not answer within [TIMEOUT]. */
        SLOW,

        /** Ends with an error that quotes the prompt it got. */
        FAILS,
    }

    /** Returns a prompt port in which the model side acts as [behaviour]; [answer] is used by [Behaviour.ANSWERS]. */
    public abstract fun create(
        behaviour: Behaviour,
        answer: String = "",
    ): OneShotPrompt

    private fun request(prompt: String = "Summarize: the build is green.") = PromptRequest(prompt, "haiku", TIMEOUT)

    @Test
    public fun `returns the answer`() =
        runTest {
            create(Behaviour.ANSWERS, "The build is green.").ask(request()) shouldBe
                Outcome.Ok("The build is green.")
        }

    @Test
    public fun `missing claude gives NotInstalled`() =
        runTest {
            create(Behaviour.NOT_INSTALLED).ask(request()) shouldBe Outcome.Err(RuntimeError.NotInstalled)
        }

    @Test
    public fun `slow claude gives Timeout`() =
        runTest {
            create(Behaviour.SLOW).ask(request()) shouldBe Outcome.Err(RuntimeError.Timeout)
        }

    @Test
    public fun `failure carries no prompt`() =
        runTest {
            val result = create(Behaviour.FAILS).ask(request(SECRET))

            val failed =
                result
                    .shouldBeInstanceOf<Outcome.Err<RuntimeError>>()
                    .error
                    .shouldBeInstanceOf<RuntimeError.CommandFailed>()
            failed.exitCode shouldNotBeExactly 0
            failed.stderrTail shouldNotContain SECRET
            failed.toString() shouldNotContain SECRET
        }

    public companion object {
        /** The limit of every request of this contract; a [Behaviour.SLOW] model needs longer. */
        public val TIMEOUT: Duration = 1.seconds

        /** Prompt text that must never come back in an error. */
        public const val SECRET: String = "secret plan for the merger"
    }
}
