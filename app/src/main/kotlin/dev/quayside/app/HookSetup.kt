package dev.quayside.app

import dev.quayside.agentruntime.api.HookError
import dev.quayside.agentruntime.api.HookInstaller
import dev.quayside.agentruntime.api.HookScope
import dev.quayside.agentruntime.api.HookStatus
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path

/** Hooks setup of the window (AM-607): the installer, the folder of its backups (shown in the dialog), the offer. */
internal class HookDeps(
    val installer: HookInstaller,
    val backupDir: Path,
    val offer: HookOfferStore,
)

/**
 * The open "Hooki Claude Code" dialog. [status] is null while it is read; [busy] while install or uninstall runs;
 * [firstRun] marks the start offer, where the close button is "Pomiń" and remembers the choice.
 */
internal data class HookDialogState(
    val status: HookStatus?,
    val busy: Boolean = false,
    val notice: HookNotice? = null,
    val firstRun: Boolean = false,
) {
    /** Install adds the missing hooks: possible when some are missing. */
    val canInstall: Boolean get() = !busy && (status is HookStatus.NotInstalled || status is HookStatus.Partial)

    /** Uninstall removes our hooks: possible when some are there. */
    val canUninstall: Boolean get() = !busy && (status is HookStatus.Installed || status is HookStatus.Partial)
}

/** What the last action did. Shows the file path and a fixed text per error type, never file content (ENG-C8). */
internal sealed interface HookNotice {
    data object Installed : HookNotice

    data object Uninstalled : HookNotice

    data class Failed(
        val error: HookError,
    ) : HookNotice
}

/**
 * Logic of the dialog, scope [HookScope.Global] only. Methods run on Main; IO runs in [scope] (the window scope),
 * one action at a time; a result that arrives after [close] is dropped. Logs only the error type, no paths.
 */
internal class HookSetupController(
    private val installer: HookInstaller,
    private val offer: HookOfferStore,
    private val scope: CoroutineScope,
    private val logger: Logger,
) {
    private val mutable = MutableStateFlow<HookDialogState?>(null)
    val state: StateFlow<HookDialogState?> = mutable.asStateFlow()

    // Counts the dialog's lives: a coroutine of an earlier life must not touch the current state.
    private var life = 0
    private var reading: Job? = null
    private var acting: Job? = null

    /** Opens the dialog and reads the status; the settings file is not changed. */
    fun open() {
        life++
        mutable.value = HookDialogState(status = null, busy = acting?.isActive == true)
        readStatus()
    }

    /** Adds the missing hooks, then reads the status again. */
    fun install() = perform(HookNotice.Installed, { it.canInstall }) { installer.install(HookScope.Global) }

    /** Removes only our hooks (no backup restore), then reads the status again and remembers the choice. */
    fun uninstall() =
        perform(HookNotice.Uninstalled, { it.canUninstall }) { installer.uninstall(HookScope.Global, backup = null) }

    /** Closes the dialog; in the start offer ("Pomiń") it also remembers the choice. */
    fun close() {
        val closing = mutable.value ?: return
        life++
        reading?.cancel()
        mutable.value = null
        if (closing.firstRun) scope.launch { rememberChoice() }
    }

    /** Opens the dialog in first run mode when [HookOffer.shouldOffer] says so. */
    fun offerAtStart() {
        val mine = life
        scope.launch {
            val status = installer.status(HookScope.Global)
            val dismissed = offer.dismissed()
            // The user opened or closed the dialog meanwhile: the offer is late.
            if (mine == life && mutable.value == null && HookOffer.shouldOffer(status, dismissed)) {
                life++
                mutable.value = HookDialogState(status, firstRun = true)
            }
        }
    }

    private fun readStatus() {
        val mine = life
        reading?.cancel()
        reading =
            scope.launch {
                val status = installer.status(HookScope.Global)
                change(mine) { it.copy(status = status) }
            }
    }

    /**
     * Runs [action] unless one runs already or [allowed] says no. The second action is blocked by [acting], which
     * lives across [open] and [close]; the `try/finally` only defends the busy flag when the action throws.
     */
    private fun perform(
        success: HookNotice,
        allowed: (HookDialogState) -> Boolean,
        action: suspend () -> Outcome<*, HookError>,
    ) {
        val current = mutable.value ?: return
        if (!allowed(current) || acting?.isActive == true) return
        val mine = life
        mutable.value = current.copy(busy = true)
        acting =
            scope.launch {
                try {
                    finish(mine, success, action())
                } finally {
                    release(mine)
                }
            }
    }

    private suspend fun finish(
        mine: Int,
        success: HookNotice,
        result: Outcome<*, HookError>,
    ) {
        val notice =
            when (result) {
                is Outcome.Ok -> {
                    success.also { if (it == HookNotice.Uninstalled) rememberChoice() }
                }

                is Outcome.Err -> {
                    logger.warn("hooks action failed", "error" to result.error::class.simpleName)
                    HookNotice.Failed(result.error)
                }
            }
        // Also after a failure: the file may have changed behind the dialog.
        val status = installer.status(HookScope.Global)
        change(mine) { it.copy(status = status, notice = notice) }
    }

    /** The action ended: the dialog (of any life) is free again; a dialog opened meanwhile reads the status anew. */
    private fun release(mine: Int) {
        mutable.update { it?.copy(busy = false) }
        if (mine != life && mutable.value != null) readStatus()
    }

    private fun change(
        mine: Int,
        transform: (HookDialogState) -> HookDialogState,
    ) {
        if (mine == life) mutable.update { it?.let(transform) }
    }

    private suspend fun rememberChoice() {
        val saved = offer.dismiss()
        if (saved is Outcome.Err) logger.warn("hook offer not saved", "error" to saved.error::class.simpleName)
    }
}

/** Pure rule of the start offer. */
internal object HookOffer {
    /** Offer when some hooks are missing and the user did not decline before; never on unreadable settings. */
    fun shouldOffer(
        status: HookStatus,
        dismissed: Boolean,
    ): Boolean = !dismissed && (status is HookStatus.NotInstalled || status is HookStatus.Partial)
}
