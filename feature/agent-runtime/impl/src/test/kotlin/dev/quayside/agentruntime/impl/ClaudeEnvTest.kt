package dev.quayside.agentruntime.impl

import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.testing.TestClock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream

/** [ClaudeEnv.forAccount]: when an account gets `CLAUDE_CONFIG_DIR` (plan AM-1003, docs/compat.md). */
class ClaudeEnvTest {
    @ParameterizedTest(name = "{0} {1} -> {2}")
    @MethodSource("cases")
    fun `forAccount sets CLAUDE_CONFIG_DIR only for a non-default account`(
        account: String,
        configDir: String?,
        expected: Map<String, String>,
    ) {
        ClaudeEnv.forAccount(AccountId(account), configDir?.let(Path::of)) shouldBe expected
    }

    @Test
    fun `agentRuntimeModule rejects an extra account without configDir`() {
        shouldThrow<IllegalArgumentException> {
            agentRuntimeModule(
                process = FakeProcessRunner(),
                clock = TestClock(),
                logger = NoOpLogger,
                homeDir = Path.of("/h"),
                account = AccountId("work"),
            )
        }
    }

    @Test
    fun `oneShotPromptModule rejects an extra account without configDir`() {
        shouldThrow<IllegalArgumentException> {
            oneShotPromptModule(FakeProcessRunner(), Path.of("/h"), account = AccountId("work"))
        }
    }

    companion object {
        private const val VAR = "CLAUDE_CONFIG_DIR"

        @JvmStatic
        fun cases(): Stream<Arguments> =
            Stream.of(
                arguments("default", null, emptyMap<String, String>()),
                arguments("default", "/h/.claude", emptyMap<String, String>()),
                arguments("default", "/h/.claude-quayside/x", emptyMap<String, String>()),
                arguments("work", "/h/.claude-quayside/work", mapOf(VAR to "/h/.claude-quayside/work")),
                arguments("work", "/h/My Dir/work", mapOf(VAR to "/h/My Dir/work")),
            )
    }
}
