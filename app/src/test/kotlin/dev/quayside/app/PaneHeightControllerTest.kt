@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.app

import androidx.compose.ui.unit.dp
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * [TerminalPaneHeight] over a real [WindowLayoutStore] on a temp directory, with the file IO on the test scheduler
 * (plan AM-819). Drags go up as negative deltas (the terminal grows).
 */
class PaneHeightControllerTest {
    @TempDir
    lateinit var dir: Path

    private val logger = RecordingLogger()

    private fun TestScope.store(root: Path = dir) =
        WindowLayoutStore(JsonStorage(root, StandardTestDispatcher(testScheduler)), logger)

    private fun TestScope.pane(
        initial: Float = 320f,
        scope: CoroutineScope = backgroundScope,
    ) = TerminalPaneHeight(initial.dp, store(), scope, logger)

    private suspend fun TestScope.saved(): Float = store().load().terminalHeight

    @Test
    fun `drag starts from the shown height not the remembered one`() =
        runTest {
            // Given a remembered 500 dp in a window with 400 dp (shown: 240)
            val pane = pane(initial = 500f)

            // When the user drags down by 10 dp
            pane.drag(10.dp, available = 400.dp)

            // Then the new height is 240 - 10, not 500 - 10
            pane.preferred.value shouldBe 230.dp
        }

    @Test
    fun `the remembered height stays until a drag changes it`() =
        runTest {
            // Given a remembered 500 dp
            val pane = pane(initial = 500f)
            runCurrent()

            // Then nothing but a drag touches it
            pane.preferred.value shouldBe 500.dp
            dir.resolve(WINDOW_FILE).exists() shouldBe false
        }

    @Test
    fun `a drag in a bigger window starts from the remembered height`() =
        runTest {
            val pane = pane(initial = 500f)

            pane.drag((-20).dp, available = 1000.dp)

            pane.preferred.value shouldBe 520.dp
        }

    @Test
    fun `drag stop saves the last height`() =
        runTest {
            val pane = pane()
            pane.drag((-100).dp, available = 600.dp)

            pane.dragStopped()
            runCurrent()

            saved() shouldBe 420f
        }

    @Test
    fun `drag without stop saves nothing`() =
        runTest {
            val pane = pane()

            pane.drag((-100).dp, available = 600.dp)
            runCurrent()

            dir.resolve(WINDOW_FILE).exists() shouldBe false
        }

    @Test
    fun `start saves nothing`() =
        runTest {
            pane()

            runCurrent()

            dir.resolve(WINDOW_FILE).exists() shouldBe false
        }

    @Test
    fun `several drags save the latest height`() =
        runTest {
            val pane = pane()

            pane.drag((-50).dp, available = 600.dp)
            pane.drag((-50).dp, available = 600.dp)
            pane.dragStopped()
            runCurrent()

            saved() shouldBe 420f
        }

    @Test
    fun `a second drag after a stop saves the new height`() =
        runTest {
            val pane = pane()
            pane.drag((-100).dp, available = 600.dp)
            pane.dragStopped()
            runCurrent()

            pane.drag(50.dp, available = 600.dp)
            pane.dragStopped()
            runCurrent()

            saved() shouldBe 370f
        }

    @Test
    fun `failed save is logged and keeps the height`() =
        runTest {
            // Given a target that only the final move fails on: window.json is a non-empty directory
            val blocked = dir.resolve(WINDOW_FILE).createDirectories()
            Files.createFile(blocked.resolve("inside"))
            val pane = pane()

            // When the user drags and lets go
            pane.drag((-100).dp, available = 600.dp)
            pane.dragStopped()
            runCurrent()

            // Then the height stays and one WARN with a fixed category is logged, without the path
            pane.preferred.value shouldBe 420.dp
            val warnings = logger.entriesAt(LogLevel.WARN)
            warnings shouldHaveSize 1
            val text = warnings.single().let { it.message + it.fields.values.joinToString() }
            text shouldNotContain dir.toString()
            text shouldNotContain "inside"
        }

    @Test
    fun `a good save after a failed one still works`() =
        runTest {
            val blocked = dir.resolve(WINDOW_FILE).createDirectories()
            val inside = Files.createFile(blocked.resolve("inside"))
            val pane = pane()
            pane.drag((-100).dp, available = 600.dp)
            pane.dragStopped()
            runCurrent()
            Files.delete(inside)
            Files.delete(blocked)

            pane.drag((-10).dp, available = 600.dp)
            pane.dragStopped()
            runCurrent()

            saved() shouldBe 430f
        }
}
