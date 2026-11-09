package dev.quayside.missioncontrol.ui

import dev.quayside.agentruntime.api.TrustError
import dev.quayside.agentruntime.api.WorkspaceTrust
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.warn
import dev.quayside.sessions.api.NewSessionSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path

/**
 * The answers of the consent step "Zaufać katalogowi?" for [NewAgentController] (AM-309 rule 4), working on its
 * [current] state. [restart] is the controller's start, so the start after a trust write takes the same path as a
 * submit. Logs only a fixed category (ENG-C8): never the directory.
 */
internal class NewAgentTrustFlow(
    private val current: MutableStateFlow<NewAgentState?>,
    private val trust: WorkspaceTrust,
    private val scope: CoroutineScope,
    private val logger: Logger,
    private val restart: suspend (ProjectId, NewSessionSpec) -> Unit,
) {
    /** The user said no. Ignored while the write runs: the answer is then already "yes". */
    fun decline() {
        current.update { shown -> if (shown?.trust?.saving == false) shown.declined() else shown }
    }

    /** The user said yes: the step is marked saving at once, so a second accept is ignored. */
    fun accept() {
        val step = current.value?.trust?.takeUnless { it.saving } ?: return
        current.update { it?.copy(trust = step.copy(saving = true)) }
        scope.launch {
            when (val result = trust.trust(step.dir)) {
                is Outcome.Err -> failed(step.dir, result.error)
                is Outcome.Ok -> startAgain(step.dir)
            }
        }
    }

    private fun failed(
        dir: Path,
        error: TrustError,
    ) {
        logger.warn("new agent: trust failed", "reason" to error.name)
        current.update { it?.copy(trust = null, problem = NewAgentProblem.TrustFailed(dir)) }
    }

    /**
     * Starts again with the form as it is; a second refusal is then explained, not asked about. Defensive: the form
     * cannot change in the consent step (`Edit` is ignored), so an invalid form or a changed step is not reachable
     * from the controller; the step must still never stay "saving".
     */
    private suspend fun startAgain(dir: Path) {
        val shown = current.value?.takeIf { it.trust?.dir == dir } ?: return
        when (val spec = NewAgentRules.specOf(shown.form)) {
            is Outcome.Err -> {
                current.value = shown.copy(trust = null, problem = NewAgentProblem.TrustFailed(dir))
            }

            is Outcome.Ok -> {
                current.value = shown.copy(trust = null, trustTried = true, busy = true, problem = null)
                restart(spec.value.first, spec.value.second)
            }
        }
    }
}
