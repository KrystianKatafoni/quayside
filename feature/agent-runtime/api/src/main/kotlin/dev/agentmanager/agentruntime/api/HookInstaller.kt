package dev.agentmanager.agentruntime.api

import dev.agentmanager.core.domain.Outcome
import java.nio.file.Path

/**
 * Installs Agent Manager hooks into Claude Code settings. Every `suspend` function may be called
 * from any dispatcher, never blocks the calling thread and never throws except cancellation.
 */
public interface HookInstaller {
    /** Which of our hooks are present in the settings of [scope]. */
    public suspend fun status(scope: HookScope): HookStatus

    /**
     * Adds our hooks to the settings of [scope]. Always backs up the settings file first and is
     * idempotent: a second install changes nothing but still returns a backup.
     */
    public suspend fun install(scope: HookScope): Outcome<BackupRef, HookError>

    /** Removes only our hooks from [scope]; with [backup] restores that copy instead. */
    public suspend fun uninstall(
        scope: HookScope,
        backup: BackupRef?,
    ): Outcome<Unit, HookError>
}

/** Which Claude Code settings file the hooks go to. */
public sealed interface HookScope {
    /** User settings, `~/.claude/settings.json`. */
    public data object Global : HookScope

    /** Local settings of the project in [dir], `.claude/settings.local.json`. */
    public data class Project(
        val dir: Path,
    ) : HookScope
}

/** Claude Code hook events Agent Manager listens to. */
public enum class HookKind {
    /** `Notification`. */
    NOTIFICATION,

    /** `Stop`. */
    STOP,

    /** `StopFailure`. */
    STOP_FAILURE,

    /** `SessionStart`. */
    SESSION_START,

    /** `SessionEnd`. */
    SESSION_END,
}

/** Result of [HookInstaller.status]. */
public sealed interface HookStatus {
    /** None of our hooks is present. */
    public data object NotInstalled : HookStatus

    /** Every hook of [HookKind] is present. */
    public data object Installed : HookStatus

    /** Some hooks are present; [missing] lists the others. */
    public data class Partial(
        val missing: Set<HookKind>,
    ) : HookStatus

    /** The settings file could not be read; [reason] is a fixed category, never file content. */
    public data class Unreadable(
        val reason: String,
    ) : HookStatus
}

/** Opaque handle to a settings backup kept by the implementation (`hooks-backup/` files). */
@JvmInline
public value class BackupRef(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "BackupRef must not be blank" }
    }
}

/** Why a hook installer operation failed. [String] reasons are fixed categories, never file content. */
public sealed interface HookError {
    /** [file] exists but could not be read or parsed. */
    public data class SettingsUnreadable(
        val file: Path,
        val reason: String,
    ) : HookError

    /** [file] or its backup could not be written. */
    public data class WriteFailed(
        val file: Path,
        val reason: String,
    ) : HookError

    /** No backup with [ref] exists. */
    public data class BackupNotFound(
        val ref: BackupRef,
    ) : HookError
}
