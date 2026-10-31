package dev.agentmanager.projects.api

/**
 * One directory to inspect. [FakeProjectInspector] takes it directly; the adapter test (AM-303)
 * turns it into files, `git init`, a fake runtime and settings files.
 *
 * @property git null = not a git repository; `GitInfo(null)` = detached HEAD.
 * @property claudeMd whether the directory has a `CLAUDE.md`.
 * @property sessions sessions the runtime reports under the directory.
 * @property hooks our hooks in the directory settings.
 * @property runtimeAvailable false = the runtime cannot be asked, so no session count.
 * @property registered projects already in the list, by real path (see [Project.dir]); give them
 * to the inspector, e.g. as `FakeProjectsApi(initial = registered)`.
 */
public data class InspectionWorld(
    val git: GitInfo? = null,
    val claudeMd: Boolean = false,
    val sessions: Int = 0,
    val hooks: HooksPresence = HooksPresence.NONE,
    val runtimeAvailable: Boolean = true,
    val registered: List<Project> = emptyList(),
)
