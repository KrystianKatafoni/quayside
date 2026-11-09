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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** The dialog logic with project targets (NFR-5, AM-609): rows, the target of an action, the offer, the logs. */
@OptIn(ExperimentalCoroutinesApi::class)
class HookSetupProjectsControllerTest {
    @TempDir
    lateinit var dir: Path

    private val hooks = FakeHookInstaller()
    private val logger = RecordingLogger()
    private val offer by lazy { hookOfferStore(dir) }
    private val alpha = projectTarget("alpha")
    private val beta = projectTarget("beta")
    private val gone = projectTarget("gone", present = false)
    private val partial = HookStatus.Partial(setOf(HookKind.STOP))

    private fun TestScope.opened(
        installer: HookInstaller = hooks,
        targets: HookTargetSource = hookTargets(alpha, beta),
    ): HookSetupController =
        HookSetupController(installer, offer, targets, backgroundScope, logger).also {
            it.open()
            runCurrent()
        }

    private val HookSetupController.shown: HookDialogState get() = state.value.shouldNotBeNull()

    private fun HookDialogState.statusOf(target: HookTarget): HookStatus? = rows.single { it.target == target }.status

    @Test
    fun `open lists global first and then the projects`() =
        runTest {
            val controller = opened()

            controller.shown.rows.map { it.target } shouldBe listOf(HookTarget.Global, alpha, beta)
            controller.shown.selected shouldBe 0
        }

    @Test
    fun `open reads the status of every present project`() =
        runTest {
            hooks.hasStatus(HookScope.Project(alpha.dir), HookStatus.Installed)
            hooks.hasStatus(HookScope.Project(beta.dir), partial)
            val controller = opened()

            hooks.calls shouldBe listOf("status global", "status /work/alpha", "status /work/beta")
            controller.shown.statusOf(alpha) shouldBe HookStatus.Installed
            controller.shown.statusOf(beta) shouldBe partial
            controller.shown.statusOf(HookTarget.Global) shouldBe HookStatus.NotInstalled
        }

    @Test
    fun `missing project folder is not read and cannot be installed`() =
        runTest {
            val controller = opened(targets = hookTargets(alpha, gone))
            controller.select(2)

            controller.install()
            controller.uninstall()
            runCurrent()

            hooks.calls shouldBe listOf("status global", "status /work/alpha")
            controller.shown.statusOf(gone).shouldBeNull()
            controller.shown.selected shouldBe 2
            controller.shown.busy shouldBe false
            controller.shown.notice.shouldBeNull()
        }

    @Test
    fun `install acts on the selected project`() =
        runTest {
            val controller = opened()
            controller.select(1)

            controller.install()
            runCurrent()

            hooks.calls shouldContain "install /work/alpha"
            hooks.calls shouldNotContain "install global"
            hooks.calls shouldNotContain "install /work/beta"
            controller.shown.statusOf(alpha) shouldBe HookStatus.Installed
            controller.shown.statusOf(HookTarget.Global) shouldBe HookStatus.NotInstalled
            controller.shown.notice shouldBe HookNotice.Installed
            controller.shown.selected shouldBe 1
        }

    @Test
    fun `uninstall acts on the selected project without a backup`() =
        runTest {
            // A backup of the partial state would bring `partial` back if the uninstall restored it.
            hooks.hasStatus(HookScope.Project(beta.dir), partial)
            val controller = opened()
            controller.select(2)
            controller.install()
            runCurrent()

            controller.uninstall()
            runCurrent()

            hooks.calls shouldContain "uninstall /work/beta"
            hooks.calls.none { it == "uninstall global" || it == "uninstall /work/alpha" } shouldBe true
            controller.shown.statusOf(beta) shouldBe HookStatus.NotInstalled
            controller.shown.notice shouldBe HookNotice.Uninstalled
        }

    @Test
    fun `select clears the notice`() =
        runTest {
            val controller = opened()
            controller.install()
            runCurrent()
            controller.shown.notice shouldBe HookNotice.Installed

            controller.select(1)

            controller.shown.selected shouldBe 1
            controller.shown.notice.shouldBeNull()
        }

    @Test
    fun `select is ignored while busy`() =
        runTest {
            val gated = GatedHookInstaller(hooks)
            val controller = opened(gated)
            controller.install()
            runCurrent()
            controller.shown.busy shouldBe true

            controller.select(1)
            gated.release()
            runCurrent()

            controller.shown.selected shouldBe 0
            hooks.calls shouldContain "install global"
            hooks.calls shouldNotContain "install /work/alpha"
        }

    @Test
    fun `select out of range is ignored`() =
        runTest {
            val controller = opened()
            controller.select(1)
            controller.install()
            runCurrent()
            controller.shown.notice shouldBe HookNotice.Installed

            controller.select(-1)
            controller.select(3)

            controller.shown.selected shouldBe 1
            controller.shown.notice shouldBe HookNotice.Installed // an ignored select does not clear it
        }

    @Test
    fun `result of a project action updates its own row after the user selects another`() =
        runTest {
            hooks.failNext(HookError.WriteFailed(Path.of("/work/alpha/.claude/settings.local.json"), "r"))
            val controller = opened()
            controller.select(1)
            controller.install() // fails: the status of alpha stays NotInstalled, but is read again
            runCurrent()
            hooks.hasStatus(HookScope.Project(alpha.dir), partial) // changed behind the dialog

            controller.select(2)
            controller.install()
            runCurrent()

            controller.shown.statusOf(beta) shouldBe HookStatus.Installed
            controller.shown.statusOf(alpha) shouldBe HookStatus.NotInstalled // not touched by the beta action
            controller.shown.statusOf(HookTarget.Global) shouldBe HookStatus.NotInstalled
        }

    @Test
    fun `project result after close is dropped`() =
        runTest {
            val gated = GatedHookInstaller(hooks)
            val controller = opened(gated)
            controller.select(1)
            controller.install()
            runCurrent()
            controller.close()
            controller.open() // the new life: the guard must be the life counter, not "state is null"
            runCurrent()

            gated.release()
            runCurrent()

            controller.shown.notice.shouldBeNull() // no notice of the old action
            controller.shown.selected shouldBe 0
            controller.shown.busy shouldBe false
            controller.shown.statusOf(alpha) shouldBe HookStatus.Installed // read anew after the action ended
        }

    @Test
    fun `project uninstall does not remember the offer choice`() =
        runTest {
            hooks.hasStatus(HookScope.Project(alpha.dir), HookStatus.Installed)
            val controller = opened()
            controller.select(1)

            controller.uninstall()
            runCurrent()

            controller.shown.notice shouldBe HookNotice.Uninstalled
            offer.dismissed() shouldBe false
        }

    @Test
    fun `global uninstall still remembers the offer choice`() =
        runTest {
            hooks.hasStatus(HookScope.Global, HookStatus.Installed)
            val controller = opened()

            controller.uninstall()
            runCurrent()

            offer.dismissed() shouldBe true
        }

    @Test
    fun `project failure logs only the error type and kind`() =
        runTest {
            val secretDir = Path.of("/work/secret-project-name")
            val secret = HookTarget.Project(alpha.id, "secret-project-name", secretDir, present = true)
            val file = secretDir.resolve(".claude/settings.local.json")
            hooks.failNext(HookError.WriteFailed(file, "secret-content-xyz"))
            val controller = opened(targets = hookTargets(secret))
            controller.select(1)

            controller.install()
            runCurrent()

            val loud = logger.entries.filter { it.level != LogLevel.DEBUG }
            loud.any { it.level.ordinal >= LogLevel.WARN.ordinal && it.fields["kind"] == "project" } shouldBe true
            loud.any { it.fields["error"] == "WriteFailed" } shouldBe true
            loud.forEach {
                it.text() shouldNotContain "secret-project-name"
                it.text() shouldNotContain "/work"
                it.text() shouldNotContain "secret-content-xyz"
            }
        }

    @Test
    fun `an action that ends in an earlier life does not cancel the targets of the new one`() =
        runTest {
            val late = CompletableDeferred<Unit>()
            var calls = 0
            val source =
                HookTargetSource {
                    if (calls++ > 0) late.await()
                    listOf(HookTarget.Global, alpha)
                }
            val gated = GatedHookInstaller(hooks)
            val controller = opened(gated, source)
            controller.select(1)
            controller.install()
            runCurrent()
            controller.close()
            controller.open() // the targets of this life are still on their way
            runCurrent()

            gated.release() // the old action ends in the earlier life
            runCurrent()
            late.complete(Unit)
            runCurrent()

            controller.shown.rows.map { it.target } shouldBe listOf(HookTarget.Global, alpha)
            controller.shown.statusOf(alpha) shouldBe HookStatus.Installed
        }

    @Test
    fun `offer at start shows only the global row`() =
        runTest {
            val controller =
                HookSetupController(hooks, offer, hookTargets(alpha, beta), backgroundScope, logger)

            controller.offerAtStart()
            runCurrent()

            val shown = controller.shown
            shown.firstRun shouldBe true
            shown.rows.map { it.target } shouldBe listOf(HookTarget.Global)
            hooks.calls shouldBe listOf("status global")
        }

    private fun LogEntry.text(): String = "$message $fields $cause"
}
