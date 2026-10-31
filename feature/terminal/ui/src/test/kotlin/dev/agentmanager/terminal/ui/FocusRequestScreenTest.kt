package dev.agentmanager.terminal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.terminal.api.TerminalApi
import dev.agentmanager.terminal.api.TerminalError
import dev.agentmanager.terminal.api.TerminalSession
import io.kotest.matchers.ints.shouldBeGreaterThan
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

private const val WAIT_MS = 5_000L

/** Session whose widget counts the focus requests it gets. */
private class CountingSession(
    private val inner: TerminalSession,
    val requests: AtomicInteger,
) : TerminalSession by inner {
    private val panel =
        object : JPanel() {
            override fun requestFocusInWindow(): Boolean {
                requests.incrementAndGet()
                return false
            }
        }

    override fun component(): JComponent = panel
}

private class CountingApi(
    private val inner: ScriptedTerminalApi,
    val requests: AtomicInteger,
) : TerminalApi by inner {
    override suspend fun openCommand(command: ShellCommand): Outcome<TerminalSession, TerminalError> =
        when (val result = inner.openCommand(command)) {
            is Outcome.Ok -> Outcome.Ok(CountingSession(result.value, requests))
            is Outcome.Err -> result
        }
}

/** A repeated `focusOrOpen` of the shown tab moves the focus to its widget again (plan AM-510, decision 3). */
@OptIn(ExperimentalTestApi::class)
class FocusRequestScreenTest {
    @TempDir
    lateinit var root: Path

    private val requests = AtomicInteger()
    private val api = CountingApi(ScriptedTerminalApi(), requests)

    private fun ComposeUiTest.showArea(): TerminalController {
        val built = mutableListOf<TerminalController>()
        setContent {
            val scope = rememberCoroutineScope()
            val controller = remember { TerminalController(api, scope, RecordingLogger(), "zsh").also { built += it } }
            TerminalArea(controller, onAppKey = { false }, paneContent = { Box(Modifier.testTag("content")) })
        }
        waitUntil(timeoutMillis = WAIT_MS) { built.isNotEmpty() }
        return built.single()
    }

    private fun ComposeUiTest.drainSwing() {
        waitForIdle()
        SwingUtilities.invokeAndWait { }
        waitForIdle()
    }

    @Test
    fun `second focus or open of the shown tab requests the widget focus again`() =
        runComposeUiTest {
            // Given a keyed tab that is shown and has had its focus
            val controller = showArea()
            val project = project("alpha", Files.createDirectory(root.resolve("Alpha")), "Alpha")
            val run = ShellCommand(listOf("claude"), project.dir)
            val key = TabKey("attach:s1")
            runOnIdle { controller.focusOrOpen(project, key, "attach · s1", run) }
            waitUntil(timeoutMillis = WAIT_MS) { requests.get() > 0 }
            drainSwing()
            val before = requests.get()

            // When the same tab is requested again
            runOnIdle { controller.focusOrOpen(project, key, "attach · s1", run) }
            waitUntil(timeoutMillis = WAIT_MS) { requests.get() > before }
            drainSwing()

            // Then the widget got one more focus request
            requests.get() shouldBeGreaterThan before
        }
}
