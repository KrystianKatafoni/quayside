package dev.quayside.agentruntime.impl

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** [DaemonStatus]: whether `claude daemon status` says the supervisor runs (plan AM-204). */
class DaemonStatusTest {
    @ParameterizedTest(name = "{0}: exit {1} -> {3}")
    @MethodSource("answers")
    fun `isRunning needs exit 0 and a numeric pid line`(
        case: String,
        exitCode: Int,
        stdout: String,
        expected: Boolean,
    ) {
        withClue(case) { DaemonStatus.isRunning(exitCode, stdout) shouldBe expected }
    }

    companion object {
        /** `claude daemon status` of 2.1.302 while the supervisor runs. */
        private val RUNNING =
            """
            pid:     698
            version: 2.1.302
            uptime:  1m 2s
            origin:  agents
            bg sessions:
              control.sock: reachable
            """.trimIndent() + "\n"

        @JvmStatic
        fun answers(): List<Arguments> =
            listOf(
                arguments("running 2.1.302", 0, RUNNING, true),
                arguments("only the pid line", 0, "pid:     698\n", true),
                arguments("not running", 1, "not running\n", false),
                arguments("not running with exit 0", 0, "not running\n", false),
                arguments("empty", 0, "", false),
                arguments("non-numeric pid", 0, "pid: abc\n", false),
                arguments("pid but failing exit", 1, RUNNING, false),
                arguments("unknown command", 2, "fake-claude: unknown command daemon\n", false),
            )
    }
}
