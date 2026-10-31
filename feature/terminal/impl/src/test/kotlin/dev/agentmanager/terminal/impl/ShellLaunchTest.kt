package dev.agentmanager.terminal.impl

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream

/** Tables 1 and 2 of docs/plans/AM-502.md. */
class ShellLaunchTest {
    private val zsh = Path.of("/bin/zsh")

    @Test
    fun `shell is the login shell`() {
        ShellLaunch.shell(zsh) shouldBe listOf("/bin/zsh", "-l")
    }

    @ParameterizedTest(name = "command {0}")
    @MethodSource("commands")
    fun `command goes through the login shell with exec`(
        argv: List<String>,
        expected: List<String>,
    ) {
        ShellLaunch.command(zsh, argv) shouldBe expected
    }

    @ParameterizedTest(name = "base {0} extra {1}")
    @MethodSource("environments")
    fun `environment sets TERM and a UTF-8 locale when none is set`(
        base: Map<String, String>,
        extra: Map<String, String>,
        expected: Map<String, String>,
        absentKeys: List<String>,
    ) {
        val actual = ShellLaunch.environment(base, extra)

        expected.forEach { (key, value) -> actual[key] shouldBe value }
        absentKeys.forEach { actual.containsKey(it) shouldBe false }
    }

    @Test
    fun `environment keeps unrelated variables`() {
        val actual = ShellLaunch.environment(mapOf("HOME" to "/h", "PATH" to "/x"), mapOf("A" to "1"))

        actual["HOME"] shouldBe "/h"
        actual["PATH"] shouldBe "/x"
        actual["A"] shouldBe "1"
    }

    companion object {
        private val prefix = listOf("/bin/zsh", "-l", "-c", "exec \"\$@\"", "agent-manager")

        @JvmStatic
        fun commands(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    listOf("claude", "attach", "s-1"),
                    prefix + listOf("claude", "attach", "s-1"),
                ),
                Arguments.of(listOf("echo", "a b; \$X"), prefix + listOf("echo", "a b; \$X")),
            )

        private fun row(
            base: Map<String, String>,
            extra: Map<String, String>,
            expected: Map<String, String>,
            vararg absent: String,
        ): Arguments = Arguments.of(base, extra, expected, absent.toList())

        private val none = emptyMap<String, String>()
        private const val XTERM = "xterm-256color"

        @JvmStatic
        fun environments(): Stream<Arguments> =
            Stream.of(
                row(mapOf("PATH" to "/x"), none, mapOf("PATH" to "/x", "TERM" to XTERM, "LC_CTYPE" to "UTF-8")),
                row(mapOf("LANG" to "pl_PL.UTF-8"), none, mapOf("LANG" to "pl_PL.UTF-8", "TERM" to XTERM), "LC_CTYPE"),
                row(mapOf("LC_ALL" to "C"), none, mapOf("LC_ALL" to "C", "TERM" to XTERM), "LC_CTYPE"),
                row(mapOf("LC_CTYPE" to "en_US.UTF-8"), none, mapOf("LC_CTYPE" to "en_US.UTF-8")),
                row(mapOf("TERM" to "dumb"), mapOf("TERM" to "vt100"), mapOf("TERM" to XTERM)),
                row(mapOf("A" to "1"), mapOf("A" to "2"), mapOf("A" to "2")),
            )
    }
}
