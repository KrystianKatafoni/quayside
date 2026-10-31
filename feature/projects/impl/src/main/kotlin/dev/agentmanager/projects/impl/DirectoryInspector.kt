package dev.agentmanager.projects.impl

import dev.agentmanager.agentruntime.api.AgentRuntime
import dev.agentmanager.agentruntime.api.HookKind
import dev.agentmanager.agentruntime.api.RuntimeError
import dev.agentmanager.agentruntime.api.RuntimeSession
import dev.agentmanager.core.domain.Logger
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.debug
import dev.agentmanager.core.domain.flatMap
import dev.agentmanager.core.domain.map
import dev.agentmanager.projects.api.DirectoryReport
import dev.agentmanager.projects.api.HooksPresence
import dev.agentmanager.projects.api.ProjectError
import dev.agentmanager.projects.api.ProjectInputRules
import dev.agentmanager.projects.api.ProjectInspector
import dev.agentmanager.projects.api.ProjectsApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.nio.file.Path

/**
 * Read-only [ProjectInspector] over git, the runtime and the directory's files (AM-303).
 *
 * The path is resolved exactly like [ProjectsApi.add]; an unresolvable path runs neither git nor the
 * runtime. git, the runtime, `CLAUDE.md` and both settings files are then probed in parallel in the
 * caller's scope. Logs are DEBUG with fixed categories only (ENG-C8).
 */
internal class DirectoryInspector(
    private val projects: ProjectsApi,
    private val runtime: AgentRuntime,
    private val git: GitProbe,
    private val io: CoroutineDispatcher,
    private val logger: Logger,
) : ProjectInspector {
    override suspend fun inspect(dir: Path): Outcome<DirectoryReport, ProjectError> =
        ProjectInputRules
            .normalizeDir(dir)
            .flatMap { normalized -> RealPaths.resolve(normalized, io) }
            .map { real -> report(real) }

    private suspend fun report(real: Path): DirectoryReport =
        coroutineScope {
            val gitInfo = async { git.info(real) }
            val sessions = async { runtime.listSessions(under = real) }
            val claudeMd = async { ClaudeMd.present(real, io) }
            val shared = async { SettingsFiles.read(real.resolve(SHARED_SETTINGS), io) }
            val local = async { SettingsFiles.read(real.resolve(LOCAL_SETTINGS), io) }
            DirectoryReport(
                dir = real,
                git = gitInfo.await(),
                hasClaudeMd = claudeMd.await(),
                sessionCount = sessionCount(sessions.await()),
                hooks = hooks(shared.await(), local.await()),
                alreadyAdded =
                    projects.projects.value
                        .firstOrNull { it.dir == real }
                        ?.id,
            )
        }

    private fun sessionCount(listed: Outcome<List<RuntimeSession>, RuntimeError>): Int? {
        if (listed is Outcome.Err) logger.debug("inspect runtime unavailable", "error" to errorName(listed.error))
        return ActiveSessions.count(listed)
    }

    private fun hooks(
        shared: SettingsRead,
        local: SettingsRead,
    ): HooksPresence {
        val results = listOf("shared" to shared, "local" to local).map { (file, read) -> logged(file, read) }
        return OurHooks.presenceOf(results)
    }

    private fun logged(
        file: String,
        read: SettingsRead,
    ): Outcome<Set<HookKind>, String> =
        OurHooks.kindsOf(read).also { result ->
            if (result is Outcome.Err) {
                logger.debug("inspect settings unreadable", "reason" to result.error, "file" to file)
            }
        }

    /** The type name only: [RuntimeError.CommandFailed] carries stderr, which may hold paths. */
    private fun errorName(error: RuntimeError): String = error::class.simpleName ?: "RuntimeError"

    private companion object {
        const val SHARED_SETTINGS = ".claude/settings.json"
        const val LOCAL_SETTINGS = ".claude/settings.local.json"
    }
}
