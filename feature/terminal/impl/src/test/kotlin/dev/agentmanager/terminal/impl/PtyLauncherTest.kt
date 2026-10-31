package dev.agentmanager.terminal.impl

import com.pty4j.PtyProcess
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.terminal.api.TerminalError
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Integration tests of [PtyLauncher] on a real pty4j PTY with `/bin/sh`, without a widget; output is read
 * through [PtyTtyConnector]. `HOME` is a temporary dir, so `.profile` is the test's own (AM-502 plan: the
 * fake `claude` reaches PATH through the profile, as `.zprofile` does for the user). No Claude Code.
 */
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class PtyLauncherTest {
    @TempDir
    lateinit var home: Path

    private val sh = Path.of("/bin/sh")
    private val connectors = mutableListOf<PtyTtyConnector>()
    private val output = StringBuilder()

    @AfterEach
    fun closeAll() {
        connectors.forEach { it.close() }
    }

    private fun environment(): Map<String, String> =
        ShellLaunch.environment(mapOf("PATH" to "/usr/bin:/bin", "HOME" to home.toString()))

    private fun start(argv: List<String>): PtyProcess {
        val result = PtyLauncher.start(argv, home, environment())
        val process = result.shouldBeInstanceOf<Outcome.Ok<PtyProcess>>().value
        connectors += PtyTtyConnector(process)
        return process
    }

    private fun PtyProcess.connector(): PtyTtyConnector = connectors.last()

    private fun readUntil(
        connector: PtyTtyConnector,
        text: String,
    ): String {
        val buf = CharArray(1024)
        while (!output.contains(text)) {
            val n = connector.read(buf, 0, buf.size)
            check(n >= 0) { "PTY closed before \"$text\" appeared; got: $output" }
            output.append(buf, 0, n)
        }
        return output.toString()
    }

    /** Reads the PTY to its end: pty4j reports the exit only after the output was consumed. */
    private fun drain(connector: PtyTtyConnector) {
        val buf = CharArray(1024)
        runCatching {
            var n = 0
            while (n >= 0) n = connector.read(buf, 0, buf.size)
        }
    }

    private fun script(
        name: String,
        body: String,
    ) {
        val bin = Files.createDirectories(home.resolve("bin"))
        val file = bin.resolve(name)
        Files.writeString(file, "#!/bin/sh\n$body\n")
        file.toFile().setExecutable(true)
    }

    private fun profile(text: String) {
        Files.writeString(home.resolve(".profile"), text)
    }

    private fun addBinToPath() = profile("PATH=\"${home.resolve("bin")}:\$PATH\"\nexport PATH\n")

    @Test
    fun `login shell starts in the given dir`() {
        val process = start(ShellLaunch.shell(sh))
        val connector = process.connector()

        connector.write("echo cwd:\$(pwd -P)\n")

        readUntil(connector, "cwd:${home.toRealPath()}") shouldContain "cwd:${home.toRealPath()}"
    }

    @Test
    fun `login shell loads the profile`() {
        profile("export AM_MARK=profile-loaded\n")
        val connector = start(ShellLaunch.shell(sh)).connector()

        connector.write("echo mark:\$AM_MARK\n")

        readUntil(connector, "mark:profile-loaded") shouldContain "mark:profile-loaded"
    }

    @Test
    fun `command runs the program found on the profile PATH`() {
        script("claude", "echo \"fake-claude:\$*\"")
        addBinToPath()

        val connector = start(ShellLaunch.command(sh, listOf("claude", "attach", "s-1"))).connector()

        readUntil(connector, "fake-claude:attach s-1") shouldContain "fake-claude:attach s-1"
    }

    @Test
    fun `arguments with spaces and dollars reach the program unchanged`() {
        script("showargs", "for a in \"\$@\"; do echo \"arg:[\$a]\"; done")
        addBinToPath()

        val connector = start(ShellLaunch.command(sh, listOf("showargs", "a b; \$X", "q\"r"))).connector()

        val text = readUntil(connector, "arg:[q\"r]")
        text shouldContain "arg:[a b; \$X]"
        text shouldContain "arg:[q\"r]"
    }

    @Test
    fun `command exit code passes through exec`() {
        val process = start(ShellLaunch.command(sh, listOf("/bin/sh", "-c", "exit 3")))
        drain(process.connector())

        process.waitFor(5, TimeUnit.SECONDS) shouldBe true
        process.exitValue() shouldBe 3
    }

    @Test
    fun `missing program exits with 127`() {
        val process = start(ShellLaunch.command(sh, listOf("am-no-such-program")))
        // pty4j reports the exit only after the PTY output was read.
        readUntil(process.connector(), "not found")

        process.waitFor(5, TimeUnit.SECONDS) shouldBe true
        process.exitValue() shouldBe 127
    }

    @Test
    fun `pty start failure returns StartFailed`() {
        val result = PtyLauncher.start(ShellLaunch.shell(Path.of("/nonexistent/sh")), home, environment())

        result shouldBe Outcome.Err(TerminalError.StartFailed)
    }

    @Test
    fun `a native library error returns StartFailed without its text`() {
        val result =
            PtyLauncher.start(ShellLaunch.shell(sh), home, environment()) {
                throw UnsatisfiedLinkError("SECRET_LINK_TEXT")
            }

        result shouldBe Outcome.Err(TerminalError.StartFailed)
        result.toString() shouldNotContain "SECRET_LINK_TEXT"
    }

    @Test
    fun `terminal type is xterm-256color`() {
        val connector = start(ShellLaunch.shell(sh)).connector()

        connector.write("echo term:\$TERM\n")

        readUntil(connector, "term:xterm-256color") shouldContain "term:xterm-256color"
    }
}
