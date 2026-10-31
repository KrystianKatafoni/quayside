package dev.agentmanager.terminal.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.domain.errorOrNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Behaviour every [TerminalApi] must have (ENG-T2). Runs on the fake and on the PTY implementation.
 * Subclasses that start real processes add `@Timeout(value = 10, threadMode = SEPARATE_THREAD)`.
 */
public abstract class TerminalApiContract {
    @TempDir
    public lateinit var dir: Path

    private val created = mutableListOf<TerminalApi>()

    /** Returns a fresh [TerminalApi] whose shell starts happily in [dir]. Called once per API in a test. */
    protected abstract fun create(dir: Path): TerminalApi

    /** Hook for subclasses to release what [create] allocated, after every API was closed. */
    protected open fun tearDown() {}

    @AfterEach
    public fun closeApis() {
        created.forEach { it.close() }
        tearDown()
    }

    private fun api(): TerminalApi = create(dir).also { created += it }

    private fun missing(): Path = dir.resolve("does-not-exist")

    private fun <T> Outcome<T, TerminalError>.session(): T = shouldBeInstanceOf<Outcome.Ok<T>>().value

    @Test
    public fun `open shell in an existing dir gives a running session`(): Unit =
        runTest {
            val session = api().openShell(dir).session()

            session.exitCode.value shouldBe null
        }

    @Test
    public fun `open shell in a missing dir returns WorkDirMissing`(): Unit =
        runTest {
            api().openShell(missing()).errorOrNull() shouldBe TerminalError.WorkDirMissing
        }

    @Test
    public fun `open command in a missing dir returns WorkDirMissing`(): Unit =
        runTest {
            val command = ShellCommand(listOf("claude", "attach", "s-1"), missing())

            api().openCommand(command).errorOrNull() shouldBe TerminalError.WorkDirMissing
        }

    @Test
    public fun `every session has its own id`(): Unit =
        runTest {
            val api = api()

            val first = api.openShell(dir).session()
            val second = api.openShell(dir).session()
            val third = api.openCommand(ShellCommand(listOf("sleep", "100"), dir)).session()

            setOf(first.id, second.id, third.id).size shouldBe 3
        }

    @Test
    public fun `component returns the same instance`(): Unit =
        runTest {
            val session = api().openShell(dir).session()

            val seen = mutableListOf<JComponent>()
            SwingUtilities.invokeAndWait {
                seen += session.component()
                seen += session.component()
            }

            (seen[0] === seen[1]) shouldBe true
        }

    @Test
    public fun `send to a running session succeeds`(): Unit =
        runTest {
            val session = api().openShell(dir).session()

            session.send("echo hello\r") shouldBe Outcome.Ok(Unit)
        }

    @Test
    public fun `send after close returns Closed`(): Unit =
        runTest {
            val session = api().openShell(dir).session()
            session.close()

            session.send("echo hello\r").errorOrNull() shouldBe TerminalError.Closed
        }

    @Test
    public fun `closing a session twice is harmless`(): Unit =
        runTest {
            val session = api().openShell(dir).session()

            session.close()
            session.close()

            session.send("x").errorOrNull() shouldBe TerminalError.Closed
        }

    @Test
    public fun `closing the api closes its sessions and refuses new ones`(): Unit =
        runTest {
            val api = api()
            val session = api.openShell(dir).session()

            api.close()
            api.close()

            session.send("x").errorOrNull() shouldBe TerminalError.Closed
            api.openShell(dir).errorOrNull() shouldBe TerminalError.Closed
            api.openCommand(ShellCommand(listOf("sleep", "100"), dir)).errorOrNull() shouldBe TerminalError.Closed
        }
}
