package dev.quayside.projects.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.core.domain.Outcome
import dev.quayside.core.testing.aSessionId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Active sessions are WORKING and BLOCKED (AM-303 plan, decision 3). */
class ActiveSessionsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("listings")
    fun `only working and blocked sessions are active`(
        case: String,
        listed: Outcome<List<RuntimeSession>, RuntimeError>,
        expected: Int?,
    ) {
        withClue(case) { ActiveSessions.count(listed) shouldBe expected }
    }

    companion object {
        private fun inPhase(phase: RuntimePhase): RuntimeSession =
            aRuntimeSession(id = aSessionId(phase.name.lowercase()), phase = phase)

        @JvmStatic
        fun listings(): List<Arguments> =
            listOf(
                arguments("one session in every phase", Outcome.Ok(RuntimePhase.entries.map(::inPhase)), 2),
                arguments(
                    "only ended sessions",
                    Outcome.Ok(listOf(RuntimePhase.DONE, RuntimePhase.FAILED, RuntimePhase.STOPPED).map(::inPhase)),
                    0,
                ),
                arguments(
                    "two working sessions",
                    Outcome.Ok(listOf(inPhase(RuntimePhase.WORKING), inPhase(RuntimePhase.WORKING))),
                    2,
                ),
                arguments("no sessions", Outcome.Ok(emptyList<RuntimeSession>()), 0),
                arguments("runtime not installed", Outcome.Err(RuntimeError.NotInstalled), null),
                arguments("runtime timeout", Outcome.Err(RuntimeError.Timeout), null),
                arguments("runtime output unparseable", Outcome.Err(RuntimeError.Unparseable("json")), null),
            )
    }
}
