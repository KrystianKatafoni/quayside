@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.app

import dev.quayside.agentruntime.api.BackupRef
import dev.quayside.agentruntime.api.HookError
import dev.quayside.agentruntime.api.HookInstaller
import dev.quayside.agentruntime.api.HookScope
import dev.quayside.agentruntime.api.HookStatus
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.storage.JsonStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.nio.file.Files
import java.nio.file.Path

/** The offer file in [dir] (a temp directory of the test, never the real app support folder); file IO runs inline. */
internal fun hookOfferStore(dir: Path): HookOfferStore = HookOfferStore(JsonStorage(dir, UnconfinedTestDispatcher()))

/** An offer store whose directory is a regular file, so every save fails. */
internal fun unwritableHookOfferStore(root: Path): HookOfferStore =
    hookOfferStore(Files.createFile(root.resolve("not-a-directory")))

/** Holds `install` and `uninstall` of [delegate] until [release], so a test sees the busy state. */
internal class GatedHookInstaller(
    private val delegate: HookInstaller,
) : HookInstaller by delegate {
    private val gate = CompletableDeferred<Unit>()

    fun release() {
        gate.complete(Unit)
    }

    override suspend fun install(scope: HookScope): Outcome<BackupRef, HookError> {
        gate.await()
        return delegate.install(scope)
    }

    override suspend fun uninstall(
        scope: HookScope,
        backup: BackupRef?,
    ): Outcome<Unit, HookError> {
        gate.await()
        return delegate.uninstall(scope, backup)
    }

    override suspend fun status(scope: HookScope): HookStatus = delegate.status(scope)
}

/** Holds the first `status` of [delegate] until [release]; later calls pass at once. */
internal class HoldFirstStatusInstaller(
    private val delegate: HookInstaller,
) : HookInstaller by delegate {
    private val gate = CompletableDeferred<Unit>()
    private var held = false

    fun release() {
        gate.complete(Unit)
    }

    override suspend fun status(scope: HookScope): HookStatus {
        val first = !held
        held = true
        if (first) gate.await()
        return delegate.status(scope)
    }
}

/** A dialog with the single row "Globalnie" (the start offer and the AM-607 tests). */
internal fun hookState(
    status: HookStatus?,
    busy: Boolean = false,
    notice: HookNotice? = null,
    firstRun: Boolean = false,
): HookDialogState = HookDialogState(listOf(HookRow(HookTarget.Global, status)), 0, busy, notice, firstRun)

/** Only the global target, like the dialog before AM-609. */
internal val globalOnlyTargets: HookTargetSource = HookTargetSource { listOf(HookTarget.Global) }

/** A project target on a path under `/work`; the folder is never touched (only fakes read it). */
internal fun projectTarget(
    name: String,
    present: Boolean = true,
): HookTarget.Project = HookTarget.Project(ProjectId("id-$name"), name, Path.of("/work/$name"), present)

/** The targets [Global][HookTarget.Global] and then [projects], in this order. */
internal fun hookTargets(vararg projects: HookTarget.Project): HookTargetSource =
    HookTargetSource { listOf(HookTarget.Global) + projects.toList() }
