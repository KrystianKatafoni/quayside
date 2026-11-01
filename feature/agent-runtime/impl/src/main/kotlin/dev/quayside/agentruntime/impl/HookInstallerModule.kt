package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.HookInstaller
import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Logger
import kotlinx.coroutines.CoroutineDispatcher
import java.nio.file.Path

/**
 * Creates the installer of Quayside hooks in Claude Code settings (NFR-5). It edits only on request of its
 * caller; the app asks the user first. The long parameter list is deliberate: `impl` may expose only factories.
 *
 * @param homeDir the user's home directory, where `.claude/settings.json` lives.
 * @param backupDir where copies of edited settings are kept, e.g. `<app support>/hooks-backup`.
 * @param port port of the hook server written into the URLs; the same as the server's.
 * @param io dispatcher for file access.
 */
@Suppress("LongParameterList")
public fun hookInstallerModule(
    homeDir: Path,
    backupDir: Path,
    clock: Clock,
    io: CoroutineDispatcher,
    logger: Logger,
    port: Int = DEFAULT_HOOK_PORT,
): HookInstaller = SettingsJsonHookInstaller(SettingsFiles(homeDir), HookBackups(backupDir, clock), port, io, logger)
