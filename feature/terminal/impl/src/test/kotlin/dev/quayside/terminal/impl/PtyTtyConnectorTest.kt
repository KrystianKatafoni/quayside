package dev.quayside.terminal.impl

import com.jediterm.core.util.TermSize
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Integration tests on a real pty4j PTY running `/bin/sh` (docs/plans/AM-501.md, like the `core:process`
 * tests). No network and no Claude Code. Each test ends by itself; `@Timeout` guards blocking reads.
 */
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class PtyTtyConnectorTest {
    @TempDir
    lateinit var dir: Path

    private val opened = mutableListOf<PtyTtyConnector>()
    private val output = StringBuilder()

    @AfterEach
    fun closeAll() {
        opened.forEach { it.close() }
    }

    private fun connect(vararg argv: String): PtyTtyConnector {
        val process: PtyProcess =
            PtyProcessBuilder(arrayOf(*argv))
                .setEnvironment(mapOf("PATH" to "/usr/bin:/bin", "TERM" to "xterm-256color", "HOME" to dir.toString()))
                .setDirectory(dir.toString())
                .setInitialColumns(80)
                .setInitialRows(24)
                .start()
        return PtyTtyConnector(process).also { opened += it }
    }

    /** Reads (blocking) until the accumulated output contains [text]; returns everything read so far. */
    private fun PtyTtyConnector.readUntil(text: String): String {
        val buf = CharArray(1024)
        while (!output.contains(text)) {
            val n = read(buf, 0, buf.size)
            check(n >= 0) { "PTY closed before \"$text\" appeared; got: $output" }
            output.append(buf, 0, n)
        }
        return output.toString()
    }

    @Test
    fun `echo w PTY zwraca wyjscie`() {
        val connector = connect("/bin/sh", "-c", "echo hello-pty")

        connector.readUntil("hello-pty") shouldContain "hello-pty"
    }

    @Test
    fun `write przekazuje bajty do procesu`() {
        val connector = connect("/bin/sh", "-c", "read line; echo got:\$line")

        connector.write("ping\n")

        connector.readUntil("got:ping") shouldContain "got:ping"
    }

    @Test
    fun `write bajtow przekazuje je do procesu`() {
        val connector = connect("/bin/sh", "-c", "read line; echo got:\$line")

        connector.write("bytes\n".toByteArray())

        connector.readUntil("got:bytes") shouldContain "got:bytes"
    }

    @Test
    fun `utf8 przechodzi bez znieksztalcen`() {
        val connector = connect("/bin/sh", "-c", "read line; echo got:\$line")

        connector.write("zażółć\n")

        connector.readUntil("got:zażółć") shouldContain "got:zażółć"
    }

    @Test
    fun `resize zmienia rozmiar widziany przez stty size`() {
        val connector = connect("/bin/sh")
        connector.write("stty size\n")
        connector.readUntil("24 80\r\n")

        connector.resize(TermSize(100, 30))
        connector.write("stty size\n")

        connector.readUntil("30 100\r\n") shouldContain "30 100"
    }

    @Test
    fun `close konczy proces i dzieci bez sieroty`() {
        val connector = connect("/bin/sh", "-c", "sleep 100 & echo child:\$!; wait")
        val text = connector.readUntil("child:").let { connector.readUntil("\n") }
        val childPid =
            Regex("child:(\\d+)")
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.toLong() ?: error("no child pid in $text")
        val child = ProcessHandle.of(childPid).orElseThrow()
        val parent = ProcessHandle.of(connector.process.pid()).orElseThrow()
        child.isAlive shouldBe true

        connector.close()

        parent.onExit().get(5, TimeUnit.SECONDS)
        child.onExit().get(5, TimeUnit.SECONDS)
        parent.isAlive shouldBe false
        child.isAlive shouldBe false
    }

    @Test
    fun `zakonczenie procesu daje kod wyjscia`() {
        val connector = connect("/bin/sh", "-c", "exit 3")

        connector.waitFor() shouldBe 3
    }

    @Test
    fun `closing twice is harmless`() {
        val connector = connect("/bin/sh", "-c", "sleep 100")

        shouldNotThrowAny {
            connector.close()
            connector.close()
        }
    }
}
