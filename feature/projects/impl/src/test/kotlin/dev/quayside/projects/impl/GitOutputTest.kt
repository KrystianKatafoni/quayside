package dev.quayside.projects.impl

import dev.quayside.core.process.ProcessResult
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Reading git results (AM-303 plan, table). */
class GitOutputTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("workTrees")
    fun `work tree answer is read from rev-parse`(
        case: String,
        result: ProcessResult,
        expected: Boolean,
    ) {
        withClue(case) { GitOutput.isWorkTree(result) shouldBe expected }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("branches")
    fun `branch is read from symbolic-ref`(
        case: String,
        result: ProcessResult,
        expected: String?,
    ) {
        withClue(case) { GitOutput.branch(result) shouldBe expected }
    }

    companion object {
        @JvmStatic
        fun workTrees(): List<Arguments> =
            listOf(
                arguments("exit 0 true with newline", ProcessResult(0, "true\n", ""), true),
                arguments("exit 0 false (inside .git or a bare repository)", ProcessResult(0, "false", ""), false),
                arguments(
                    "exit 128 (not a repository)",
                    ProcessResult(128, "", "fatal: not a git repository (or any of the parent directories): .git\n"),
                    false,
                ),
                arguments("exit 128 with true on stdout", ProcessResult(128, "true\n", ""), false),
            )

        @JvmStatic
        fun branches(): List<Arguments> =
            listOf(
                arguments("exit 0 main with newline", ProcessResult(0, "main\n", ""), "main"),
                arguments("exit 0 branch with slash and dash", ProcessResult(0, "feat/a-b", ""), "feat/a-b"),
                arguments("exit 1 without output (detached HEAD)", ProcessResult(1, "", ""), null),
                arguments("exit 0 without output", ProcessResult(0, "", ""), null),
                arguments(
                    "exit 0 with truncated stdout",
                    ProcessResult(0, "a".repeat(4096), "", stdoutTruncated = true),
                    null,
                ),
            )
    }
}
