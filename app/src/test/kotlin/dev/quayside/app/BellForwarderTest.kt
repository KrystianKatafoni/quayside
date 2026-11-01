package dev.quayside.app

import dev.quayside.core.domain.getOrNull
import dev.quayside.notifications.api.RecordingNotificationCenter
import dev.quayside.terminal.api.FakeTerminalApi
import dev.quayside.terminal.api.FakeTerminalSession
import dev.quayside.terminal.api.TerminalSettings
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Files

/** FR-T7: a terminal bell is a notification while the setting is on (AM-509). */
@OptIn(ExperimentalCoroutinesApi::class)
class BellForwarderTest {
    private val api = FakeTerminalApi()
    private val center = RecordingNotificationCenter()
    private val settings = MutableStateFlow(TerminalSettings())

    private suspend fun session(): FakeTerminalSession =
        api.openShell(Files.createTempDirectory("bell")).getOrNull() as FakeTerminalSession

    @Test
    fun `FR-T7 a bell becomes a notification`() =
        runTest {
            forwardBells(api.bells, settings, center, backgroundScope + UnconfinedTestDispatcher(testScheduler))
            val session = session()

            api.ringBell(session)
            api.ringBell(session)

            center.terminalBells shouldBe 2
        }

    @Test
    fun `FR-T7 a bell is ignored while the setting is off`() =
        runTest {
            settings.value = TerminalSettings(bellNotification = false)
            forwardBells(api.bells, settings, center, backgroundScope + UnconfinedTestDispatcher(testScheduler))

            api.ringBell(session())

            center.terminalBells shouldBe 0
        }

    @Test
    fun `FR-T7 switching the setting applies to the next bell of an open tab`() =
        runTest {
            forwardBells(api.bells, settings, center, backgroundScope + UnconfinedTestDispatcher(testScheduler))
            val session = session()
            api.ringBell(session)

            settings.value = settings.value.copy(bellNotification = false)
            api.ringBell(session)
            settings.value = settings.value.copy(bellNotification = true)
            api.ringBell(session)

            center.terminalBells shouldBe 2
        }
}
