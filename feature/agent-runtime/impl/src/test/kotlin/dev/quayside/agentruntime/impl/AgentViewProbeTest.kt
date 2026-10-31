package dev.quayside.agentruntime.impl

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** [AgentViewProbe]: the 2.1.302 messages of a switched-off Agent View (plan AM-204). */
class AgentViewProbeTest {
    @ParameterizedTest(name = "{0} -> {3}")
    @MethodSource("answers")
    fun `isDisabledMessage recognises only the Agent View switches`(
        case: String,
        stderr: String,
        stdout: String,
        expected: Boolean,
    ) {
        withClue(case) { AgentViewProbe.isDisabledMessage(stderr, stdout) shouldBe expected }
    }

    companion object {
        private const val BY_ENV = "'claude agents --json' is disabled by CLAUDE_CODE_DISABLE_AGENT_VIEW."
        private const val BY_SETTING = "'claude agents --json' is disabled by the 'disableAgentView' setting."

        @JvmStatic
        fun answers(): List<Arguments> =
            listOf(
                arguments("disabled by env", "$BY_ENV\n", "", true),
                arguments("disabled by setting", "$BY_SETTING\n", "", true),
                arguments("disabled by env on stdout", "", "$BY_ENV\n", true),
                arguments(
                    "disabled by env with ANSI",
                    "\u001b[31m'claude agents --json' is disabled by " +
                        "\u001b[1mCLAUDE_CODE_DISABLE_AGENT_VIEW\u001b[0m.\n",
                    "",
                    true,
                ),
                arguments(
                    "disabled by setting with ANSI",
                    "\u001b[33m'claude agents --json' is disabled by the " +
                        "\u001b[1m'disableAgentView'\u001b[0m setting.\n",
                    "",
                    true,
                ),
                arguments("unknown option", "error: unknown option '--json'\n", "", false),
                arguments("empty", "", "", false),
                arguments("switch named without disabled by", "CLAUDE_CODE_DISABLE_AGENT_VIEW is not set\n", "", false),
            )
    }
}
