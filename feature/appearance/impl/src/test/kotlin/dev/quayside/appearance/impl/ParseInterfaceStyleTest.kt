package dev.quayside.appearance.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

/** `defaults read -g AppleInterfaceStyle` result to the macOS mode (ADR 0023, point 4). */
class ParseInterfaceStyleTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("results")
    fun `result maps to dark, light or unknown`(
        name: String,
        result: Outcome<ProcessResult, ProcessError>,
        expected: Boolean?,
    ) {
        withClue(name) { parseInterfaceStyle(result) shouldBe expected }
    }

    companion object {
        private fun exit(
            code: Int,
            stdout: String = "",
            stderr: String = "",
        ): Outcome<ProcessResult, ProcessError> = Outcome.Ok(ProcessResult(code, stdout, stderr))

        private fun failed(error: ProcessError): Outcome<ProcessResult, ProcessError> = Outcome.Err(error)

        @JvmStatic
        @Suppress("LongMethod") // a table of cases
        fun results(): Stream<Arguments> =
            Stream.of(
                Arguments.of("exit 0 with Dark", exit(0, "Dark\n"), true),
                Arguments.of("exit 0 with Dark, no newline", exit(0, "Dark"), true),
                Arguments.of("exit 0 with Light", exit(0, "Light\n"), false),
                Arguments.of(
                    "exit 1, the key does not exist",
                    exit(1, stderr = "The domain/default pair of (kCFPreferencesAnyApplication, X) does not exist"),
                    false,
                ),
                Arguments.of("profile echo before Dark", exit(0, "Welcome back, user\nDark\n"), true),
                Arguments.of("profile echo before Light", exit(0, "Welcome back, user\nLight\n"), false),
                Arguments.of("blank lines after Dark", exit(0, "Dark\n\n   \n"), true),
                Arguments.of("exit 2", exit(2), null),
                Arguments.of("exit 127", exit(127, stderr = "command not found"), null),
                Arguments.of("timeout", failed(ProcessError.Timeout(2.seconds)), null),
                Arguments.of("not found", failed(ProcessError.NotFound("defaults")), null),
                Arguments.of("start failed", failed(ProcessError.StartFailed("no dir")), null),
                Arguments.of("io failed", failed(ProcessError.IoFailed("pipe")), null),
            )
    }
}
