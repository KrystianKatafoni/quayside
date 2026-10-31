package dev.quayside.projects.impl

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText

/**
 * Runs the real `git` for test setup in an isolated environment: `HOME` is [home], no system or global
 * config, no inherited `GIT_*` variables, fixed author, no signing and no hooks, so the user's own git
 * configuration cannot change the result. Every call has a hard limit; nothing waits on wall-clock time
 * otherwise.
 */
internal class TestGit(
    private val home: Path,
) {
    /** Runs `git <args>` in [dir]; returns stdout and stderr together, fails the test on a non-zero exit. */
    fun run(
        dir: Path,
        vararg args: String,
    ): String {
        val output = Files.createTempFile("am-test-git", ".log")
        try {
            val process =
                ProcessBuilder(listOf("git") + ISOLATING_OPTIONS + args)
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .apply {
                        environment().keys.removeIf { it.startsWith("GIT_") }
                        environment().putAll(environment(home))
                    }.start()
            process.outputStream.close()
            if (!process.waitFor(LIMIT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw AssertionError("git ${args.joinToString(" ")} did not finish in $LIMIT_SECONDS s")
            }
            val text = output.readText()
            if (process.exitValue() != 0) {
                throw AssertionError("git ${args.joinToString(" ")} failed (${process.exitValue()}): $text")
            }
            return text
        } finally {
            output.deleteIfExists()
        }
    }

    /** New repository in [dir] whose current (unborn) branch is [branch], independent of `init.defaultBranch`. */
    fun init(
        dir: Path,
        branch: String = "main",
    ) {
        run(dir, "init", "--quiet")
        run(dir, "symbolic-ref", "HEAD", "refs/heads/$branch")
    }

    /** Commits everything in [dir], or an empty commit when nothing changed. */
    fun commitAll(dir: Path) {
        run(dir, "add", "--all")
        run(dir, "commit", "--quiet", "--allow-empty", "-m", "test commit")
    }

    private companion object {
        const val LIMIT_SECONDS = 30L

        val ISOLATING_OPTIONS =
            listOf("-c", "commit.gpgsign=false", "-c", "tag.gpgsign=false", "-c", "core.hooksPath=/dev/null")

        fun environment(home: Path): Map<String, String> =
            mapOf(
                "HOME" to home.toString(),
                "XDG_CONFIG_HOME" to home.resolve(".config").toString(),
                "GIT_CONFIG_NOSYSTEM" to "1",
                "GIT_CONFIG_GLOBAL" to "/dev/null",
                "GIT_TERMINAL_PROMPT" to "0",
                "GIT_AUTHOR_NAME" to "Test",
                "GIT_AUTHOR_EMAIL" to "test@example.invalid",
                "GIT_COMMITTER_NAME" to "Test",
                "GIT_COMMITTER_EMAIL" to "test@example.invalid",
            )
    }
}
