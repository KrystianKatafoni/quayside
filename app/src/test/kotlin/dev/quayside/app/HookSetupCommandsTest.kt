package dev.quayside.app

import dev.quayside.agentruntime.api.FakeHookInstaller
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.core.domain.NoOpLogger
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** The palette command "Hooki Claude Code" and its guard against open dialogs (NFR-5, AM-607, ADR 0011). */
@OptIn(ExperimentalCoroutinesApi::class)
class HookSetupCommandsTest {
    @TempDir
    lateinit var dir: Path

    private fun TestScope.setup() =
        HookSetupController(FakeHookInstaller(), hookOfferStore(dir), globalOnlyTargets, backgroundScope, NoOpLogger)

    @Test
    fun `offers hooks setup when no dialog is open`() =
        runTest {
            val command = hookSetupCommands(dialogOpen = false, setup()).single()

            command.id shouldBe CommandId("hooks.setup")
            command.title shouldBe Strings["command.hookSetup"]
            command.group shouldBe CommandGroup.APP
            command.keywords shouldBe listOf("hooks")
        }

    @Test
    fun `offers nothing while a dialog is open`() =
        runTest {
            hookSetupCommands(dialogOpen = true, setup()).shouldBeEmpty()
        }

    @Test
    fun `running the command opens the dialog and reads only the status`() =
        runTest {
            val installer = FakeHookInstaller()
            val setup =
                HookSetupController(
                    installer,
                    hookOfferStore(dir),
                    globalOnlyTargets,
                    backgroundScope,
                    NoOpLogger,
                )
            setup.state.value.shouldBeNull()

            hookSetupCommands(dialogOpen = false, setup).single().run()
            runCurrent()

            setup.state.value.shouldNotBeNull()
            installer.calls shouldBe listOf("status global")
        }
}
