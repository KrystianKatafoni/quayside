package dev.quayside.projects.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.core.domain.Logger
import dev.quayside.core.process.ProcessRunner
import dev.quayside.projects.api.ProjectInspector
import dev.quayside.projects.api.ProjectsApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Creates the read-only [ProjectInspector] (FR-P2). Thread-safe and stateless; [projects] is read on every
 * call, so `alreadyAdded` follows the current list. git, the runtime and both settings files are probed
 * in parallel; [gitTimeout] limits each git call, the runtime applies its own timeout.
 * The long parameter list is deliberate: `impl` may expose only factories, so no public settings type.
 *
 * @param io dispatcher for `toRealPath()` and file reads, usually `AppDispatchers.io`.
 * @param logger gets fixed categories only, never paths, branch names or process output (ENG-C8).
 * @param gitExecutable `git` on the login-shell PATH, or an absolute path (tests).
 */
@Suppress("LongParameterList")
public fun projectInspectorModule(
    projects: ProjectsApi,
    runtime: AgentRuntime,
    process: ProcessRunner,
    io: CoroutineDispatcher,
    logger: Logger,
    gitExecutable: String = "git",
    gitTimeout: Duration = 5.seconds,
): ProjectInspector =
    DirectoryInspector(
        projects = projects,
        runtime = runtime,
        git = GitProbe(process, gitExecutable, gitTimeout, logger),
        io = io,
        logger = logger,
    )
