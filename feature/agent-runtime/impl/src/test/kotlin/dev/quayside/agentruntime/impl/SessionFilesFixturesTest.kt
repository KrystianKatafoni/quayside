package dev.quayside.agentruntime.impl

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.exists
import kotlin.io.path.readText

/** Synthetic session files of the AM-311 spike (fixtures/session-files/README.md). */
internal class SessionFilesFixturesTest {
    @TempDir
    lateinit var dir: Path

    private val root: Path =
        Path.of(checkNotNull(javaClass.getResource("/fixtures/session-files")) { "fixtures missing" }.toURI())

    private fun fixture(name: String): String = root.resolve("synthetic/$name").readText()

    @Test
    fun `every fixture file is listed in the README`() {
        FixtureAnonymity.assertAllListedInReadme(root)
    }

    @Test
    fun `synthetic fixtures hold only test paths and test UUIDs`() {
        FixtureAnonymity.assertAnonymous(root, FixtureAnonymity.fixtureFiles(root))
    }

    @Test
    fun `no real session file is recorded`() {
        root.resolve("recorded").exists() shouldBe false
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expectations")
    fun `fixture gives the expected summary or problem`(
        name: String,
        expected: Outcome<SessionFileSummary, SessionFileProblem>,
    ) {
        parseWhole(fixture(name)) shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expectations")
    fun `reader gives the same result as parse for a fixture`(
        name: String,
        expected: Outcome<SessionFileSummary, SessionFileProblem>,
    ) = runTest {
        val file = dir.resolve("$SESSION_ID.jsonl")
        Files.writeString(file, fixture(name))

        SessionFileReader(StandardTestDispatcher(testScheduler)).read(file) shouldBe expected
    }

    companion object {
        private val quayside = Path.of("/Users/test/projects/quayside")

        private fun ok(
            startedAt: String?,
            lastActivityAt: String?,
            title: String?,
            startCwd: Path = quayside,
            cwdChanged: Boolean = false,
        ): Outcome<SessionFileSummary, SessionFileProblem> =
            Outcome.Ok(
                SessionFileSummary(
                    sessionId = SESSION_ID,
                    startCwd = startCwd,
                    startedAt = startedAt?.let(::at),
                    lastActivityAt = lastActivityAt?.let(::at),
                    cwdChanged = cwdChanged,
                    title = title,
                ),
            )

        private fun at10(minute: Int): String = "2026-01-01T10:%02d:00Z".format(Locale.ROOT, minute)

        @JvmStatic
        @Suppress("LongMethod")
        fun expectations(): List<Arguments> =
            listOf(
                arguments(
                    "interactive-single-cwd.jsonl",
                    ok(at10(0), at10(30), "Fix the login bug"),
                ),
                arguments(
                    "background-first-entry-has-cwd.jsonl",
                    ok(at10(0), at10(9), "Add tests for the parser"),
                ),
                arguments(
                    "cwd-changes.jsonl",
                    ok(at10(0), at10(6), "Start in the project root", cwdChanged = true),
                ),
                arguments("starts-with-command.jsonl", ok(at10(0), at10(2), "Write the README")),
                arguments("sidechain.jsonl", Outcome.Err(SessionFileProblem.SIDECHAIN)),
                arguments(
                    "subfolder.jsonl",
                    ok(at10(0), at10(1), "Work only in soft", startCwd = quayside.resolve("soft")),
                ),
                arguments("no-user-message.jsonl", ok(at10(0), at10(0), null)),
                arguments("leading-metadata.jsonl", ok(at10(0), at10(3), "Refactor the parser")),
                arguments("not-jsonl.txt", Outcome.Err(SessionFileProblem.NOT_JSONL)),
            )
    }
}
