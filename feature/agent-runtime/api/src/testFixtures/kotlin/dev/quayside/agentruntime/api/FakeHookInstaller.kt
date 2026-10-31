package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import java.util.concurrent.atomic.AtomicReference

/**
 * Scriptable in-memory [HookInstaller] for tests of features that install hooks. Passes
 * [HookInstallerContract].
 *
 * Every scope starts as [HookStatus.NotInstalled]. Each install keeps a backup of the scope state
 * before it and returns refs `backup0001`, `backup0002`, … in order. A failed install keeps no backup.
 */
public class FakeHookInstaller : HookInstaller {
    private val state = AtomicReference(FakeHookState())

    /**
     * Operations called so far, in order, as `<operation> <scope>` where scope is `global` or the
     * project directory, e.g. `status global`, `install /Users/test/projects/agent-manager`,
     * `uninstall global`.
     */
    public val calls: List<String> get() = state.get().calls

    /** Makes the next [install] or [uninstall] return [error] without changing anything; [status] is not affected. */
    public fun failNext(error: HookError) {
        state.updateAndGet { it.copy(nextFailure = error) }
    }

    /** Sets what [status] reports for [scope], e.g. [HookStatus.Partial] or [HookStatus.Unreadable]. */
    public fun hasStatus(
        scope: HookScope,
        status: HookStatus,
    ) {
        state.updateAndGet { it.withStatus(scope, status) }
    }

    override suspend fun status(scope: HookScope): HookStatus =
        state.updateAndGet { it.copy(calls = it.calls + "status ${scope.label()}") }.statusOf(scope)

    override suspend fun install(scope: HookScope): Outcome<BackupRef, HookError> =
        change("install", scope) { current ->
            val ref = BackupRef("backup%04d".format(current.backups.size + 1))
            val backedUp = current.copy(backups = current.backups + (ref to current.statusOf(scope)))
            backedUp.withStatus(scope, HookStatus.Installed) to Outcome.Ok(ref)
        }

    override suspend fun uninstall(
        scope: HookScope,
        backup: BackupRef?,
    ): Outcome<Unit, HookError> =
        change("uninstall", scope) { current ->
            val restored =
                if (backup == null) {
                    HookStatus.NotInstalled
                } else {
                    current.backups[backup] ?: return@change current to Outcome.Err(HookError.BackupNotFound(backup))
                }
            current.withStatus(scope, restored) to Outcome.Ok(Unit)
        }

    /** Records the call and applies [operation] atomically, unless a scripted failure comes first. */
    private fun <T> change(
        operation: String,
        scope: HookScope,
        apply: (FakeHookState) -> Pair<FakeHookState, Outcome<T, HookError>>,
    ): Outcome<T, HookError> {
        while (true) {
            val before = state.get()
            val recorded = before.copy(calls = before.calls + "$operation ${scope.label()}", nextFailure = null)
            val failure = before.nextFailure
            val (after, result) = if (failure != null) recorded to Outcome.Err(failure) else apply(recorded)
            if (state.compareAndSet(before, after)) return result
        }
    }
}

private data class FakeHookState(
    val statuses: Map<HookScope, HookStatus> = emptyMap(),
    val backups: Map<BackupRef, HookStatus> = emptyMap(),
    val calls: List<String> = emptyList(),
    val nextFailure: HookError? = null,
) {
    fun statusOf(scope: HookScope): HookStatus = statuses[scope] ?: HookStatus.NotInstalled

    fun withStatus(
        scope: HookScope,
        status: HookStatus,
    ): FakeHookState = copy(statuses = statuses + (scope to status))
}

private fun HookScope.label(): String =
    when (this) {
        HookScope.Global -> "global"
        is HookScope.Project -> dir.toString()
    }
