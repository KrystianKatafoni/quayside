package dev.quayside.app

import dev.quayside.agentruntime.api.FakeHookInstaller
import dev.quayside.agentruntime.api.HookError
import dev.quayside.agentruntime.api.HookInstaller
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

/** The start offer and the remembered choice of the hooks dialog on [FakeHookInstaller] (NFR-5, AM-607, ADR 0020). */
@OptIn(ExperimentalCoroutinesApi::class)
class HookSetupOfferTest {
    @TempDir
    lateinit var dir: Path

    private val hooks = FakeHookInstaller()
    private val logger = RecordingLogger()
    private val global = HookScope.Global
    private val failure = HookError.WriteFailed(Path.of("/home/test/.claude/settings.json"), "disk")

    private fun TestScope.controller(
        installer: HookInstaller = hooks,
        offer: HookOfferStore = hookOfferStore(dir),
    ) = HookSetupController(installer, offer, globalOnlyTargets, backgroundScope, logger)

    private val HookSetupController.shown: HookDialogState get() = state.value.shouldNotBeNull()

    @Test
    fun `offer at start opens the dialog in first run mode`() =
        runTest {
            val controller = controller()

            controller.offerAtStart()
            runCurrent()

            controller.shown shouldBe hookState(status = HookStatus.NotInstalled, firstRun = true)
            hooks.calls shouldBe listOf("status global")
        }

    @Test
    fun `offer at start stays closed when hooks are installed`() =
        runTest {
            hooks.hasStatus(global, HookStatus.Installed)
            val controller = controller()

            controller.offerAtStart()
            runCurrent()

            controller.state.value.shouldBeNull()
            hooks.calls shouldBe listOf("status global")
        }

    @Test
    fun `offer at start stays closed after the choice was remembered`() =
        runTest {
            val offer = hookOfferStore(dir).also { it.dismiss() }
            val controller = controller(offer = offer)

            controller.offerAtStart()
            runCurrent()

            controller.state.value.shouldBeNull()
        }

    @Test
    fun `a late start offer does not reopen a dialog the user closed`() =
        runTest {
            val held = HoldFirstStatusInstaller(hooks)
            val offer = hookOfferStore(dir)
            val controller = controller(installer = held, offer = offer)
            controller.offerAtStart()
            runCurrent()
            controller.open()
            runCurrent()
            controller.close()

            held.release()
            runCurrent()

            controller.state.value.shouldBeNull()
            offer.dismissed() shouldBe false
        }

    @Test
    fun `skip remembers the choice`() =
        runTest {
            val offer = hookOfferStore(dir)
            val controller = controller(offer = offer)
            controller.offerAtStart()
            runCurrent()

            controller.close()
            runCurrent()

            controller.state.value.shouldBeNull()
            offer.dismissed() shouldBe true
            hooks.calls shouldBe listOf("status global")
        }

    @Test
    fun `close from the palette does not remember`() =
        runTest {
            val offer = hookOfferStore(dir)
            val controller = controller(offer = offer)
            controller.open()
            runCurrent()

            controller.close()
            runCurrent()

            controller.state.value.shouldBeNull()
            offer.dismissed() shouldBe false
        }

    @Test
    fun `uninstall remembers the choice`() =
        runTest {
            hooks.hasStatus(global, HookStatus.Installed)
            val offer = hookOfferStore(dir)
            val controller = controller(offer = offer)
            controller.open()
            runCurrent()

            controller.uninstall()
            runCurrent()

            offer.dismissed() shouldBe true
        }

    @Test
    fun `failed uninstall does not remember the choice`() =
        runTest {
            hooks.hasStatus(global, HookStatus.Installed)
            val offer = hookOfferStore(dir)
            val controller = controller(offer = offer)
            controller.open()
            runCurrent()
            hooks.failNext(failure)

            controller.uninstall()
            runCurrent()

            offer.dismissed() shouldBe false
        }

    @Test
    fun `failed save of the choice still closes`() =
        runTest {
            val controller = controller(offer = unwritableHookOfferStore(dir))
            controller.offerAtStart()
            runCurrent()

            controller.close()
            runCurrent()

            controller.state.value.shouldBeNull()
            logger.entries.any { it.level.ordinal >= LogLevel.WARN.ordinal }.shouldBeTrue()
            logger.entries.forEach { it.text() shouldNotContain dir.toString() }
        }

    private fun LogEntry.text(): String = "$message $fields $cause"
}
