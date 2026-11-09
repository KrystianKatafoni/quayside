package dev.quayside.app

import dev.quayside.agentruntime.api.FakeHookInstaller
import dev.quayside.agentruntime.api.HookError
import dev.quayside.agentruntime.api.HookInstaller
import dev.quayside.agentruntime.api.HookKind
import dev.quayside.agentruntime.api.HookScope
import dev.quayside.agentruntime.api.HookStatus
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.testing.LogEntry
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Logic of the "Hooki Claude Code" dialog on [FakeHookInstaller] (NFR-5, AM-607). */
@OptIn(ExperimentalCoroutinesApi::class)
class HookSetupControllerTest {
    @TempDir
    lateinit var dir: Path

    private val hooks = FakeHookInstaller()
    private val logger = RecordingLogger()
    private val global = HookScope.Global
    private val partial = HookStatus.Partial(setOf(HookKind.STOP))
    private val settingsFile = Path.of("/home/secret-user/.claude/settings.json")
    private val failure = HookError.WriteFailed(settingsFile, "secret-content-xyz")

    private fun TestScope.controller(
        installer: HookInstaller = hooks,
        offer: HookOfferStore = hookOfferStore(dir),
        targets: HookTargetSource = globalOnlyTargets,
    ) = HookSetupController(installer, offer, targets, backgroundScope, logger)

    /** The controller after `open()` and the read of the status. */
    private fun TestScope.opened(
        installer: HookInstaller = hooks,
        targets: HookTargetSource = globalOnlyTargets,
    ): HookSetupController =
        controller(installer, targets = targets).also {
            it.open()
            runCurrent()
        }

    private val HookSetupController.shown: HookDialogState get() = state.value.shouldNotBeNull()

    @Test
    fun `open shows the global status`() =
        runTest {
            hooks.hasStatus(global, partial)
            val controller = controller()

            controller.open()
            controller.shown.current.status
                .shouldBeNull()
            runCurrent()

            controller.shown shouldBe hookState(status = partial)
        }

    @Test
    fun `open reads only the status`() =
        runTest {
            opened()

            hooks.calls shouldBe listOf("status global")
        }

    @Test
    fun `install shows the installed notice and the new status`() =
        runTest {
            val controller = opened()

            controller.install()
            runCurrent()

            controller.shown shouldBe
                hookState(status = HookStatus.Installed, busy = false, notice = HookNotice.Installed)
            hooks.calls shouldBe listOf("status global", "install global", "status global")
        }

    @Test
    fun `partial status allows install and uninstall`() =
        runTest {
            val installing = FakeHookInstaller().apply { hasStatus(global, partial) }
            opened(installing).install()
            runCurrent()
            val uninstalling = FakeHookInstaller().apply { hasStatus(global, partial) }
            opened(uninstalling).uninstall()
            runCurrent()

            installing.calls.contains("install global").shouldBeTrue()
            uninstalling.calls.contains("uninstall global").shouldBeTrue()
        }

    @Test
    fun `unreadable settings disable both actions`() =
        runTest {
            hooks.hasStatus(global, HookStatus.Unreadable("json:invalid"))
            val controller = opened()

            controller.install()
            controller.uninstall()
            runCurrent()

            hooks.calls shouldBe listOf("status global")
            controller.shown shouldBe hookState(status = HookStatus.Unreadable("json:invalid"))
        }

    @Test
    fun `install failure keeps the dialog with the error`() =
        runTest {
            hooks.failNext(failure)
            val controller = opened()

            controller.install()
            runCurrent()

            controller.shown shouldBe
                hookState(status = HookStatus.NotInstalled, busy = false, notice = HookNotice.Failed(failure))
        }

    @Test
    fun `uninstall removes our hooks without a backup`() =
        runTest {
            // A backup of the partial state exists after the install; restoring it would bring `partial` back.
            hooks.hasStatus(global, partial)
            val controller = opened()
            controller.install()
            runCurrent()

            controller.uninstall()
            runCurrent()

            controller.shown shouldBe
                hookState(status = HookStatus.NotInstalled, busy = false, notice = HookNotice.Uninstalled)
        }

    @Test
    fun `second action while busy is ignored`() =
        runTest {
            hooks.hasStatus(global, partial)
            val gated = GatedHookInstaller(hooks)
            val controller = opened(gated)
            controller.install()
            runCurrent()
            controller.shown.busy shouldBe true

            controller.install()
            controller.uninstall()
            gated.release()
            runCurrent()

            hooks.calls shouldBe listOf("status global", "install global", "status global")
            controller.shown.busy shouldBe false
        }

    @Test
    fun `result after close is dropped`() =
        runTest {
            val gated = GatedHookInstaller(hooks)
            val controller = opened(gated)
            controller.install()
            runCurrent()

            controller.close()
            gated.release()
            runCurrent()

            controller.state.value.shouldBeNull()
        }

    @Test
    fun `result after close does not touch a reopened dialog`() =
        runTest {
            val gated = GatedHookInstaller(hooks)
            val controller = opened(gated)
            controller.install()
            runCurrent()
            controller.close()
            controller.open()
            runCurrent()

            gated.release()
            runCurrent()

            controller.shown shouldBe hookState(status = HookStatus.Installed) // no notice of the old action
        }

    @Test
    fun `a failed install can be retried`() =
        runTest {
            hooks.failNext(failure)
            val controller = opened()
            controller.install()
            runCurrent()
            controller.shown.busy shouldBe false

            controller.install()
            runCurrent()

            controller.shown shouldBe
                hookState(status = HookStatus.Installed, busy = false, notice = HookNotice.Installed)
        }

    @Test
    fun `a failed uninstall can be retried`() =
        runTest {
            hooks.hasStatus(global, HookStatus.Installed)
            val controller = opened()
            hooks.failNext(failure)
            controller.uninstall()
            runCurrent()
            controller.shown.busy shouldBe false

            controller.uninstall()
            runCurrent()

            controller.shown.notice shouldBe HookNotice.Uninstalled
        }

    @Test
    fun `failure logs only the error type`() =
        runTest {
            hooks.failNext(failure)
            val controller = opened()

            controller.install()
            runCurrent()

            val loud = logger.entries.filter { it.level != LogLevel.DEBUG }
            loud
                .any { it.level.ordinal >= LogLevel.WARN.ordinal && it.normalized().contains("writefailed") }
                .shouldBeTrue()
            loud.forEach {
                it.text() shouldNotContain "secret-user"
                it.text() shouldNotContain "secret-content-xyz"
            }
        }

    @Test
    fun `install failure reads the status again`() =
        runTest {
            val controller = opened()
            hooks.hasStatus(global, partial) // the file changed behind the dialog
            hooks.failNext(failure)

            controller.install()
            runCurrent()

            controller.shown.current.status shouldBe partial
            controller.shown.notice shouldBe HookNotice.Failed(failure)
            hooks.calls shouldBe listOf("status global", "install global", "status global")
        }

    @Test
    fun `reopened dialog is busy until the old action ends and then shows the current status`() =
        runTest {
            val gated = GatedHookInstaller(hooks)
            val controller = opened(gated)
            controller.install()
            runCurrent()

            controller.close()
            controller.open()
            runCurrent()
            controller.shown.busy shouldBe true
            controller.install()
            controller.uninstall()
            gated.release()
            runCurrent()

            controller.shown.busy shouldBe false
            controller.shown.current.status shouldBe HookStatus.Installed
            hooks.calls.count { it == "install global" } shouldBe 1
            hooks.calls.none { it.startsWith("uninstall") }.shouldBeTrue()
        }

    private fun LogEntry.text(): String = "$message $fields $cause"

    /** Lower-case letters only, so `WriteFailed`, `write_failed` and `write failed` all match `writefailed`. */
    private fun LogEntry.normalized(): String = text().lowercase().filter { it.isLetter() }
}
