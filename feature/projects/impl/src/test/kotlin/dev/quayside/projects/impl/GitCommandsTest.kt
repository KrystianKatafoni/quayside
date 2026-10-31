package dev.quayside.projects.impl

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

/** argv of the two git calls (AM-303 plan, decision 2; review S1, S6). */
class GitCommandsTest {
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["git", "/usr/bin/git"])
    fun `work tree check names the dir with -C and runs without optional locks, fsmonitor and hooks`(exe: String) {
        GitCommands.isInsideWorkTree(exe, DIR) shouldBe
            listOf(exe, "-C", DIR.toString()) + SAFE + listOf("rev-parse", "--is-inside-work-tree")
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["git", "/usr/bin/git"])
    fun `branch query names the dir with -C and runs without optional locks, fsmonitor and hooks`(exe: String) {
        GitCommands.branch(exe, DIR) shouldBe
            listOf(exe, "-C", DIR.toString()) + SAFE + listOf("symbolic-ref", "--quiet", "--short", "HEAD")
    }

    private companion object {
        val DIR: Path = Path.of("/Users/me/my project")
        val SAFE = listOf("--no-optional-locks", "-c", "core.fsmonitor=false", "-c", "core.hooksPath=/dev/null")
    }
}
