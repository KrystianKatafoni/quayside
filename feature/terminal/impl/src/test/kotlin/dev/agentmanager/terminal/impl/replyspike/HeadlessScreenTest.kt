package dev.agentmanager.terminal.impl.replyspike

import com.pty4j.PtyProcess
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.terminal.impl.PtyLauncher
import dev.agentmanager.terminal.impl.PtyTtyConnector
import dev.agentmanager.terminal.impl.ShellLaunch
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * `HeadlessScreen` over a real PTY with `/bin/sh` scripts that draw with escape sequences; no widget, no Claude Code.
 * Scripts end with `read`, so the PTY stays open while the screen is inspected.
 */
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class HeadlessScreenTest {
    @TempDir
    lateinit var home: Path

    private val closeables = mutableListOf<() -> Unit>()

    @AfterEach
    fun closeAll() {
        closeables.reversed().forEach { it() }
    }

    private fun screenOf(script: String): Pair<HeadlessScreen, PtyTtyConnector> {
        val file = home.resolve("draw.sh")
        Files.writeString(file, script)
        val environment =
            ShellLaunch.environment(mapOf("PATH" to "/usr/bin:/bin", "HOME" to home.toString(), "LC_ALL" to "C.UTF-8"))
        val argv = ShellLaunch.command(Path.of("/bin/sh"), listOf("/bin/sh", file.toString()))
        val process = PtyLauncher.start(argv, home, environment).shouldBeInstanceOf<Outcome.Ok<PtyProcess>>().value
        val connector = PtyTtyConnector(process)
        closeables.add { connector.close() }
        val screen = HeadlessScreen(connector)
        closeables.add { screen.close() }
        return screen to connector
    }

    private fun HeadlessScreen.lines(): List<String> = rows().map { it.trimEnd() }

    @Test
    fun `cursor moves and line clears give the final screen`() =
        realTimeTest {
            val (screen, _) =
                screenOf(
                    """
                    printf 'first line\r\nsecond line\r\nthird line'
                    printf '\033[2;1H\033[2Kreplaced'
                    printf '\033[3;1H\033[KX'
                    printf '\033[1;1H\033[1;7HLINE'
                    read answer
                    """.trimIndent(),
                )

            screen.await(5.seconds) { rows -> rows.any { it.trimEnd() == "X" } } shouldBe true

            screen.lines().take(3) shouldBe listOf("first LINE", "replaced", "X")
        }

    @Test
    fun `polish text renders unchanged`() =
        realTimeTest {
            val (screen, _) = screenOf("printf 'zażółć gęślą jaźń ZAŻÓŁĆ GĘŚLĄ JAŹŃ\\r\\n'\nread answer\n")

            screen.await(5.seconds) { rows -> rows.any { "JAŹŃ" in it } } shouldBe true

            screen.lines() shouldContain "zażółć gęślą jaźń ZAŻÓŁĆ GĘŚLĄ JAŹŃ"
        }

    @Test
    fun `alternate screen is shown and restored`() =
        realTimeTest {
            val (screen, connector) =
                screenOf(
                    """
                    printf 'main-screen\r\n'
                    printf '\033[?1049h\033[Halt-screen'
                    read answer
                    printf '\033[?1049l'
                    read answer
                    """.trimIndent(),
                )

            screen.await(5.seconds) { rows -> rows.any { "alt-screen" in it } } shouldBe true
            screen.lines() shouldNotContain "main-screen"

            connector.write("\n")

            screen.await(5.seconds) { rows -> rows.any { "main-screen" in it } } shouldBe true
            screen.lines().none { "alt-screen" in it } shouldBe true
        }

    @Test
    fun `await returns false after the timeout`() =
        realTimeTest {
            val (screen, _) = screenOf("printf 'something'\nread answer\n")

            screen.await(300.milliseconds) { rows -> rows.any { "never-drawn" in it } } shouldBe false
        }
}
