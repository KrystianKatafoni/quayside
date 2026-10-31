package dev.agentmanager.agentruntime.api

import dev.agentmanager.agentruntime.api.RuntimeRequirements.MIN_CLAUDE_VERSION
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Behaviour of [AgentRuntime.health] (NFR-9, ENG-T2). Runs on [FakeAgentRuntime] and, from AM-204,
 * on the CLI adapter. Separate from [AgentRuntimeContract] because health arrives later.
 */
public abstract class AgentRuntimeHealthContract {
    /** Returns a runtime whose machine is in the state described by [world]. */
    public abstract fun create(world: RuntimeWorld): AgentRuntime

    @Test
    public fun `healthy runtime is ready and reports its version`(): Unit =
        runTest {
            val health = create(RuntimeWorld(version = "2.1.290")).health()

            assertSoftly(health) {
                version shouldBe "2.1.290"
                agentViewEnabled shouldBe true
                problems shouldBe emptyList()
                isReady shouldBe true
            }
        }

    @Test
    public fun `missing runtime reports NotInstalled without a version`(): Unit =
        runTest {
            val health = create(RuntimeWorld(installed = false)).health()

            assertSoftly(health) {
                version shouldBe null
                problems shouldContain RuntimeError.NotInstalled
                isReady shouldBe false
            }
        }

    @Test
    public fun `old version reports UnsupportedVersion with the required version`(): Unit =
        runTest {
            val health = create(RuntimeWorld(version = "2.1.100")).health()

            assertSoftly(health) {
                version shouldBe "2.1.100"
                problems shouldContain RuntimeError.UnsupportedVersion(found = "2.1.100", required = MIN_CLAUDE_VERSION)
                problems shouldNotContain RuntimeError.NotInstalled
                isReady shouldBe false
            }
        }

    @Test
    public fun `disabled Agent View reports AgentViewDisabled`(): Unit =
        runTest {
            val health = create(RuntimeWorld(agentViewEnabled = false)).health()

            assertSoftly(health) {
                agentViewEnabled shouldBe false
                problems shouldContain RuntimeError.AgentViewDisabled
                isReady shouldBe false
            }
        }
}
