package dev.quayside.app

import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.RuntimeHealth
import dev.quayside.agentruntime.api.RuntimeWorld
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** The version behind "supervisor X" in the status bar (AM-816, decision 2): the first token of `claude --version`. */
class SupervisorVersionTest {
    private fun health(version: String?) =
        RuntimeHealth(version = version, agentViewEnabled = true, supervisorRunning = true, problems = emptyList())

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "'2.1.139 (Claude Code)',2.1.139",
        "'2.1.150',2.1.150",
        "'  2.1.139  (Claude Code)',2.1.139",
        "'2.1.139\n',2.1.139",
    )
    fun `supervisor of health takes the version token`(
        raw: String,
        expected: String,
    ) {
        supervisorOf(health(raw)) shouldBe expected
    }

    @Test
    fun `supervisor without claude is null`() {
        supervisorOf(health(null)).shouldBeNull()
    }

    @ParameterizedTest(name = "[{0}]")
    @CsvSource("''", "'   '")
    fun `supervisor of an empty version is null`(raw: String) {
        supervisorOf(health(raw)).shouldBeNull()
    }

    @Test
    fun `reading the supervisor asks the runtime for its health once`() =
        runTest {
            val runtime = FakeAgentRuntime(RuntimeWorld(version = "2.1.150"))

            val shown = readSupervisor(runtime)

            shown shouldBe "2.1.150"
            runtime.calls.shouldContainExactly("health")
        }

    @Test
    fun `reading the supervisor of a missing claude gives null`() =
        runTest {
            val runtime = FakeAgentRuntime(RuntimeWorld(installed = false))

            readSupervisor(runtime).shouldBeNull()
        }
}
