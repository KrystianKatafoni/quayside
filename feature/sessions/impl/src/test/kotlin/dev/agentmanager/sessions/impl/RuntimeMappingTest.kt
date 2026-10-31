package dev.agentmanager.sessions.impl

import dev.agentmanager.agentruntime.api.RuntimeError
import dev.agentmanager.agentruntime.api.RuntimePhase
import dev.agentmanager.agentruntime.api.WaitingFor
import dev.agentmanager.agentruntime.api.aRuntimeSession
import dev.agentmanager.core.testing.aSessionId
import dev.agentmanager.sessions.api.RuntimeProblem
import dev.agentmanager.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Runtime → sessions mappings (AM-402 plan, tables "toSessionState" and "toProblem"). */
class RuntimeMappingTest {
    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @MethodSource("phasesAndReasons")
    fun `maps runtime phase and waiting reason to session state`(
        phase: RuntimePhase,
        waitingFor: WaitingFor?,
        expected: SessionState,
    ) {
        aRuntimeSession(phase = phase, waitingFor = waitingFor).toSessionState() shouldBe expected
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("allCombinations")
    fun `active phases are exactly the active states`(
        phase: RuntimePhase,
        waitingFor: WaitingFor?,
    ) {
        // Mirrors ActiveSessions in projects:impl, which counts WORKING and BLOCKED as active.
        val activePhase = phase == RuntimePhase.WORKING || phase == RuntimePhase.BLOCKED

        aRuntimeSession(phase = phase, waitingFor = waitingFor).toSessionState().isActive shouldBe activePhase
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("errors")
    fun `maps every runtime error to a fixed problem`(
        error: RuntimeError,
        expected: RuntimeProblem,
    ) {
        error.toProblem() shouldBe expected
    }

    companion object {
        private val REASONS: List<WaitingFor?> =
            listOf(null, WaitingFor.Input, WaitingFor.Permission, WaitingFor.Other("x"))

        private val awaitingInput = SessionState.AwaitingInput(question = null)
        private val awaitingPermission = SessionState.AwaitingPermission(tool = null)

        @JvmStatic
        fun phasesAndReasons(): List<Arguments> =
            REASONS.map { arguments(RuntimePhase.WORKING, it, SessionState.Working) } +
                listOf(
                    arguments(RuntimePhase.BLOCKED, null, awaitingInput),
                    arguments(RuntimePhase.BLOCKED, WaitingFor.Input, awaitingInput),
                    arguments(RuntimePhase.BLOCKED, WaitingFor.Permission, awaitingPermission),
                    arguments(RuntimePhase.BLOCKED, WaitingFor.Other("x"), awaitingInput),
                ) +
                REASONS.map { arguments(RuntimePhase.DONE, it, SessionState.Done) } +
                REASONS.map { arguments(RuntimePhase.FAILED, it, SessionState.Failed) } +
                REASONS.map { arguments(RuntimePhase.STOPPED, it, SessionState.Stopped) }

        @JvmStatic
        fun allCombinations(): List<Arguments> =
            RuntimePhase.entries.flatMap { phase -> REASONS.map { arguments(phase, it) } }

        @JvmStatic
        fun errors(): List<Arguments> =
            listOf(
                arguments(RuntimeError.NotInstalled, RuntimeProblem.NOT_INSTALLED),
                arguments(RuntimeError.UnsupportedVersion("2.0.0", "2.1.0"), RuntimeProblem.UNSUPPORTED_VERSION),
                arguments(RuntimeError.AgentViewDisabled, RuntimeProblem.AGENT_VIEW_DISABLED),
                arguments(RuntimeError.Timeout, RuntimeProblem.TIMEOUT),
                arguments(RuntimeError.CommandFailed(1, "tail"), RuntimeProblem.COMMAND_FAILED),
                arguments(RuntimeError.Unparseable("x"), RuntimeProblem.UNPARSEABLE),
                arguments(RuntimeError.UnknownSession(aSessionId()), RuntimeProblem.COMMAND_FAILED),
            )
    }
}
